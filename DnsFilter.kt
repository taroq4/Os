package com.blockads.filter

import android.util.Log

/**
 * DnsFilter - يعترض استعلامات DNS ويحجب نطاقات الإعلانات
 * يقوم بتحليل حزم DNS الخام وإرجاع NXDOMAIN للنطاقات المحجوبة
 */
class DnsFilter(private val blocklist: Set<String>) {

    private val TAG = "DnsFilter"

    // أنماط Wildcard للنطاقات الإعلانية
    private val wildcardPatterns = listOf(
        Regex("""^ads?\d*\.""", RegexOption.IGNORE_CASE),
        Regex("""^track(er|ing)?\.""", RegexOption.IGNORE_CASE),
        Regex("""^analytics?\.""", RegexOption.IGNORE_CASE),
        Regex("""^telemetry\.""", RegexOption.IGNORE_CASE),
        Regex("""^pixel\.""", RegexOption.IGNORE_CASE),
        Regex("""^metrics?\.""", RegexOption.IGNORE_CASE),
        Regex("""^log\d*\.""", RegexOption.IGNORE_CASE),
        Regex(""".*-ads?-.*""", RegexOption.IGNORE_CASE),
        Regex(""".*\.ads?\.""", RegexOption.IGNORE_CASE),
        Regex(""".*adunit.*""", RegexOption.IGNORE_CASE),
        Regex(""".*adserver.*""", RegexOption.IGNORE_CASE),
    )

    // نطاقات يجب السماح بها دائماً (whitelist)
    private val whitelist = setOf(
        "graph.facebook.com",
        "www.instagram.com",
        "api.snapchat.com",
        "api16-normal.tiktokv.com",
        "api19-normal-c-alisg.tiktokv.com",
        "push.apple.com",
        "fcm.googleapis.com",
        "accounts.google.com",
        "login.live.com",
    )

    // ===== فحص إذا كان النطاق محجوباً =====
    fun isBlocked(domain: String): Boolean {
        val cleanDomain = domain.lowercase().trimEnd('.')

        // التحقق من Whitelist أولاً
        if (whitelist.contains(cleanDomain)) return false
        if (whitelist.any { cleanDomain.endsWith(".$it") }) return false

        // التحقق من القائمة المباشرة
        if (blocklist.contains(cleanDomain)) {
            Log.d(TAG, "Blocked (list): $cleanDomain")
            return true
        }

        // التحقق من النطاقات الأب
        val parts = cleanDomain.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.drop(i).joinToString(".")
            if (blocklist.contains(parent)) {
                Log.d(TAG, "Blocked (parent): $cleanDomain via $parent")
                return true
            }
        }

        // التحقق من الأنماط
        if (wildcardPatterns.any { it.containsMatchIn(cleanDomain) }) {
            Log.d(TAG, "Blocked (pattern): $cleanDomain")
            return true
        }

        return false
    }

    // ===== استخراج اسم النطاق من حزمة DNS خام =====
    fun extractDomain(dnsPayload: ByteArray): String? {
        return try {
            if (dnsPayload.size < 12) return null

            // DNS header = 12 bytes
            // بعدها يبدأ QNAME
            val sb = StringBuilder()
            var i = 12

            while (i < dnsPayload.size) {
                val labelLen = dnsPayload[i].toInt() and 0xFF

                // نهاية QNAME
                if (labelLen == 0) break

                // pointer (يبدأ بـ 0xC0)
                if (labelLen and 0xC0 == 0xC0) break

                i++
                if (i + labelLen > dnsPayload.size) return null

                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dnsPayload, i, labelLen, Charsets.US_ASCII))
                i += labelLen
            }

            if (sb.isEmpty()) null else sb.toString()

        } catch (e: Exception) {
            null
        }
    }

    // ===== بناء رد NXDOMAIN =====
    fun buildNXDOMAINResponse(query: ByteArray): ByteArray {
        if (query.size < 12) return query

        val response = query.copyOf()

        // تعيين الـ flags: QR=1 (Response), RCODE=3 (NXDOMAIN)
        // Byte 2: 0x81 = QR(1) + AA(0) + TC(0) + RD(1)
        // Byte 3: 0x83 = RA(1) + RCODE(3=NXDOMAIN)
        response[2] = 0x81.toByte()
        response[3] = 0x83.toByte()

        // تصفير عدد الإجابات والـ authority والـ additional
        response[6]  = 0
        response[7]  = 0
        response[8]  = 0
        response[9]  = 0
        response[10] = 0
        response[11] = 0

        return response
    }

    // ===== بناء رد A record بـ 0.0.0.0 =====
    fun buildNullAResponse(query: ByteArray): ByteArray {
        if (query.size < 12) return buildNXDOMAINResponse(query)

        val domainBytes = query.copyOfRange(12, query.size)
        val response = ByteArray(query.size + 16)

        // نسخ الـ header مع تعديل الـ flags
        System.arraycopy(query, 0, response, 0, 12)
        response[2] = 0x81.toByte()  // QR=1, RD=1
        response[3] = 0x80.toByte()  // RA=1, RCODE=0 (NOERROR)
        response[6] = 0              // Questions
        response[7] = 1              // questions=1
        response[4] = 0
        response[5] = 1              // answers=1

        // نسخ السؤال
        System.arraycopy(domainBytes, 0, response, 12, domainBytes.size)

        // إضافة A record يشير لـ 0.0.0.0
        val answerOffset = 12 + domainBytes.size
        // Name pointer → 0xC00C (يشير للسؤال)
        response[answerOffset]     = 0xC0.toByte()
        response[answerOffset + 1] = 0x0C.toByte()
        // Type A = 0x0001
        response[answerOffset + 2] = 0x00
        response[answerOffset + 3] = 0x01
        // Class IN = 0x0001
        response[answerOffset + 4] = 0x00
        response[answerOffset + 5] = 0x01
        // TTL = 60 seconds
        response[answerOffset + 6] = 0x00
        response[answerOffset + 7] = 0x00
        response[answerOffset + 8] = 0x00
        response[answerOffset + 9] = 0x3C
        // RDLENGTH = 4
        response[answerOffset + 10] = 0x00
        response[answerOffset + 11] = 0x04
        // RDATA = 0.0.0.0
        response[answerOffset + 12] = 0x00
        response[answerOffset + 13] = 0x00
        response[answerOffset + 14] = 0x00
        response[answerOffset + 15] = 0x00

        return response.copyOf(answerOffset + 16)
    }
}

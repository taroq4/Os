package com.blockads.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blockads.MainActivity
import com.blockads.R
import com.blockads.filter.AdBlocklist
import com.blockads.filter.DnsFilter
import com.blockads.filter.JsonAdFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class BlockAdsVpnService : VpnService() {

    companion object {
        private const val TAG = "BlockAdsVPN"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "blockads_vpn"

        const val ACTION_START        = "com.blockads.ACTION_START"
        const val ACTION_STOP         = "com.blockads.ACTION_STOP"
        const val ACTION_STATS_UPDATE = "com.blockads.STATS_UPDATE"
        const val ACTION_VPN_STARTED  = "com.blockads.VPN_STARTED"
        const val ACTION_VPN_STOPPED  = "com.blockads.VPN_STOPPED"

        // إحصائيات (atomic للـ thread safety)
        @Volatile var isRunning = false
        val totalAdsBlocked = AtomicLong(0)
        val dnsBlocked      = AtomicLong(0)
        val quicBlocked     = AtomicLong(0)
        val jsonFiltered    = AtomicLong(0)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    private lateinit var dnsFilter: DnsFilter
    private lateinit var jsonFilter: JsonAdFilter

    override fun onCreate() {
        super.onCreate()
        dnsFilter = DnsFilter(AdBlocklist.getBlocklist())
        jsonFilter = JsonAdFilter()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                startVpnService()
                START_STICKY
            }
            ACTION_STOP -> {
                stopVpnService()
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    // ===== تشغيل خدمة VPN =====
    private fun startVpnService() {
        if (running.get()) return

        try {
            vpnInterface = buildVpnInterface()
            running.set(true)
            isRunning = true

            startForeground(NOTIFICATION_ID, buildNotification())
            broadcastVpnState(ACTION_VPN_STARTED)

            // بدء معالجة الحزم في خيط منفصل
            Thread({ processPackets() }, "BlockAds-Processor").start()

            // خيط الإحصائيات (يُرسل تحديثاً كل ثانية)
            Thread({ statsLoop() }, "BlockAds-Stats").start()

            Log.i(TAG, "BlockAds VPN started ✅")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpnService()
        }
    }

    // ===== إيقاف الخدمة =====
    private fun stopVpnService() {
        running.set(false)
        isRunning = false

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        broadcastVpnState(ACTION_VPN_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "BlockAds VPN stopped")
    }

    // ===== بناء واجهة VPN =====
    private fun buildVpnInterface(): ParcelFileDescriptor {
        return Builder().apply {
            setMtu(1500)

            // عنوان VPN المحلي
            addAddress("10.0.0.2", 32)

            // توجيه كل الترافيك عبر VPN
            addRoute("0.0.0.0", 0)
            addRoute("::", 0)  // IPv6

            // DNS server هو نفس التطبيق (لاعتراض DNS queries)
            addDnsServer("10.0.0.2")
            addDnsServer("1.1.1.1")  // backup

            setBlocking(true)
            setSession("BlockAds Shield")
        }.establish() ?: throw IllegalStateException("فشل إنشاء VPN interface")
    }

    // ===== حلقة معالجة الحزم الرئيسية =====
    private fun processPackets() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream  = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)

        val packet = ByteBuffer.allocate(65536)

        while (running.get()) {
            try {
                packet.clear()
                val len = inputStream.read(packet.array())
                if (len <= 0) continue

                packet.limit(len)

                val result = processOnePacket(packet)

                when (result.action) {
                    PacketAction.FORWARD -> {
                        // إرسال الحزمة للشبكة الحقيقية
                        outputStream.write(result.data ?: packet.array(), 0, result.size)
                    }
                    PacketAction.BLOCK -> {
                        // إسقاط الحزمة (إعلان محجوب)
                        totalAdsBlocked.incrementAndGet()
                    }
                    PacketAction.DNS_RESPONSE -> {
                        // إرسال رد DNS مباشرة للتطبيق
                        outputStream.write(result.data ?: continue, 0, result.size)
                    }
                }

            } catch (e: Exception) {
                if (running.get()) Log.e(TAG, "Error processing packet", e)
            }
        }
    }

    // ===== تحليل كل حزمة =====
    private fun processOnePacket(buffer: ByteBuffer): PacketResult {
        if (buffer.limit() < 20) return PacketResult.forward(buffer)

        val firstByte = buffer.get(0).toInt() and 0xFF
        val ipVersion = firstByte shr 4

        return when (ipVersion) {
            4 -> processIPv4(buffer)
            6 -> processIPv6(buffer)
            else -> PacketResult.forward(buffer)
        }
    }

    // ===== معالجة حزم IPv4 =====
    private fun processIPv4(buffer: ByteBuffer): PacketResult {
        val ipHeaderLen = (buffer.get(0).toInt() and 0x0F) * 4
        if (buffer.limit() < ipHeaderLen + 4) return PacketResult.forward(buffer)

        val protocol = buffer.get(9).toInt() and 0xFF

        return when (protocol) {
            6  -> processTCP(buffer, ipHeaderLen)   // TCP
            17 -> processUDP(buffer, ipHeaderLen)   // UDP
            else -> PacketResult.forward(buffer)
        }
    }

    // ===== معالجة حزم IPv6 =====
    private fun processIPv6(buffer: ByteBuffer): PacketResult {
        if (buffer.limit() < 40) return PacketResult.forward(buffer)
        val nextHeader = buffer.get(6).toInt() and 0xFF

        return when (nextHeader) {
            6  -> processTCP(buffer, 40)
            17 -> processUDP(buffer, 40)
            else -> PacketResult.forward(buffer)
        }
    }

    // ===== معالجة UDP (DNS + QUIC) =====
    private fun processUDP(buffer: ByteBuffer, ipHeaderLen: Int): PacketResult {
        val udpOffset = ipHeaderLen
        if (buffer.limit() < udpOffset + 8) return PacketResult.forward(buffer)

        val srcPort  = ((buffer.get(udpOffset).toInt() and 0xFF) shl 8) or
                        (buffer.get(udpOffset + 1).toInt() and 0xFF)
        val destPort = ((buffer.get(udpOffset + 2).toInt() and 0xFF) shl 8) or
                        (buffer.get(udpOffset + 3).toInt() and 0xFF)

        return when {
            // ⭐ حجب QUIC/HTTP3 (UDP port 443)
            destPort == 443 -> {
                quicBlocked.incrementAndGet()
                totalAdsBlocked.incrementAndGet()
                PacketResult.block()
            }

            // ⭐ اعتراض DNS queries (UDP port 53)
            destPort == 53 -> {
                val dnsPayloadOffset = udpOffset + 8
                val dnsPayload = buffer.array()
                    .copyOfRange(dnsPayloadOffset, buffer.limit())

                handleDnsQuery(buffer, dnsPayload, ipHeaderLen, udpOffset)
            }

            else -> PacketResult.forward(buffer)
        }
    }

    // ===== معالجة TCP (HTTP/HTTPS) =====
    private fun processTCP(buffer: ByteBuffer, ipHeaderLen: Int): PacketResult {
        val tcpOffset = ipHeaderLen
        if (buffer.limit() < tcpOffset + 20) return PacketResult.forward(buffer)

        val destPort = ((buffer.get(tcpOffset + 2).toInt() and 0xFF) shl 8) or
                        (buffer.get(tcpOffset + 3).toInt() and 0xFF)

        // HTTP غير مشفر - يمكن فحص URL
        if (destPort == 80) {
            return processHttpPacket(buffer, tcpOffset)
        }

        // HTTPS - يمر (تتولاه طبقة DNS)
        return PacketResult.forward(buffer)
    }

    // ===== فحص HTTP requests مباشرة =====
    private fun processHttpPacket(buffer: ByteBuffer, tcpOffset: Int): PacketResult {
        val tcpHeaderLen = ((buffer.get(tcpOffset + 12).toInt() and 0xF0) shr 4) * 4
        val payloadOffset = tcpOffset + tcpHeaderLen
        if (buffer.limit() <= payloadOffset) return PacketResult.forward(buffer)

        val payload = String(buffer.array(), payloadOffset, buffer.limit() - payloadOffset)

        // فحص ad URL patterns في HTTP requests
        val adPatterns = listOf(
            "/ads/", "/ad/", "/ad_server/", "/sponsored/",
            "?ad_type=", "?campaign_id=", "?creative_id=",
            "/pixel/track", "/analytics/", "/log/event",
            "/ies/marketing/", "-adunit.", "/open_api/"
        )

        if (adPatterns.any { payload.contains(it, ignoreCase = true) }) {
            Log.d(TAG, "Blocked HTTP ad request: ${payload.take(100)}")
            return PacketResult.block()
        }

        return PacketResult.forward(buffer)
    }

    // ===== معالجة DNS queries =====
    private fun handleDnsQuery(
        originalBuffer: ByteBuffer,
        dnsPayload: ByteArray,
        ipHeaderLen: Int,
        udpOffset: Int
    ): PacketResult {

        val domain = dnsFilter.extractDomain(dnsPayload)
            ?: return PacketResult.forward(originalBuffer)

        return if (dnsFilter.isBlocked(domain)) {
            // إرجاع NXDOMAIN (النطاق غير موجود)
            Log.d(TAG, "DNS Blocked: $domain")
            dnsBlocked.incrementAndGet()
            totalAdsBlocked.incrementAndGet()

            val dnsResponse = dnsFilter.buildNXDOMAINResponse(dnsPayload)
            val responsePacket = buildDnsResponsePacket(
                originalBuffer, dnsResponse, ipHeaderLen, udpOffset
            )
            PacketResult(PacketAction.DNS_RESPONSE, responsePacket, responsePacket.size)

        } else {
            // السماح بالمرور للـ DNS server الحقيقي
            PacketResult.forward(originalBuffer)
        }
    }

    // ===== بناء حزمة رد DNS =====
    private fun buildDnsResponsePacket(
        request: ByteBuffer,
        dnsResponse: ByteArray,
        ipHeaderLen: Int,
        udpOffset: Int
    ): ByteArray {
        // تبادل src/dst في IP header وUDP header
        val result = request.array().copyOf(request.limit())

        // تبادل IP src وdst
        val srcIp = result.copyOfRange(12, 16)
        val dstIp = result.copyOfRange(16, 20)
        System.arraycopy(dstIp, 0, result, 12, 4)
        System.arraycopy(srcIp, 0, result, 16, 4)

        // تبادل UDP ports
        val srcPort = result.copyOfRange(udpOffset, udpOffset + 2)
        val dstPort = result.copyOfRange(udpOffset + 2, udpOffset + 4)
        System.arraycopy(dstPort, 0, result, udpOffset, 2)
        System.arraycopy(srcPort, 0, result, udpOffset + 2, 2)

        // وضع DNS response
        val dnsOffset = udpOffset + 8
        val newPacket = ByteArray(dnsOffset + dnsResponse.size)
        System.arraycopy(result, 0, newPacket, 0, dnsOffset)
        System.arraycopy(dnsResponse, 0, newPacket, dnsOffset, dnsResponse.size)

        // تحديث طول UDP
        val udpLen = (8 + dnsResponse.size)
        newPacket[udpOffset + 4] = (udpLen shr 8).toByte()
        newPacket[udpOffset + 5] = (udpLen and 0xFF).toByte()

        // تحديث IP Total Length
        val ipLen = ipHeaderLen + udpLen
        newPacket[2] = (ipLen shr 8).toByte()
        newPacket[3] = (ipLen and 0xFF).toByte()

        return newPacket
    }

    // ===== إرسال تحديثات الإحصائيات =====
    private fun statsLoop() {
        while (running.get()) {
            Thread.sleep(1000)
            sendBroadcast(Intent(ACTION_STATS_UPDATE).apply {
                putExtra("ads_blocked",  totalAdsBlocked.get())
                putExtra("dns_blocked",  dnsBlocked.get())
                putExtra("quic_blocked", quicBlocked.get())
                putExtra("json_filtered", jsonFiltered.get())
            })
        }
    }

    private fun broadcastVpnState(action: String) {
        sendBroadcast(Intent(action))
    }

    // ===== الإشعار الدائم =====
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockAds 🛡️")
            .setContentText("الحماية مُفعَّلة - حجب إعلانات TikTok, Snap, Instagram")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BlockAds VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "قناة خدمة حجب الإعلانات"
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopVpnService()
        super.onDestroy()
    }
}

// ===== نتيجة معالجة الحزمة =====
enum class PacketAction { FORWARD, BLOCK, DNS_RESPONSE }

data class PacketResult(
    val action: PacketAction,
    val data: ByteArray? = null,
    val size: Int = 0
) {
    companion object {
        fun forward(buffer: ByteBuffer) = PacketResult(
            PacketAction.FORWARD,
            buffer.array(),
            buffer.limit()
        )
        fun block() = PacketResult(PacketAction.BLOCK)
    }
}

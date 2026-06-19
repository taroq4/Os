package com.blockads.filter

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * JsonAdFilter - يحلل ردود API ويحذف عناصر الإعلانات
 * يدعم: TikTok JSON + Instagram GraphQL
 */
class JsonAdFilter {

    private val TAG = "JsonAdFilter"

    var filteredCount = 0L
        private set

    // ===== نقطة الدخول الرئيسية =====
    fun filter(responseBody: String, requestUrl: String): String {
        return try {
            when {
                isTikTokFeedUrl(requestUrl)       -> filterTikTokFeed(responseBody)
                isInstagramFeedUrl(requestUrl)     -> filterInstagramFeed(responseBody)
                isInstagramReelsUrl(requestUrl)    -> filterInstagramReels(responseBody)
                isSnapchatDiscoverUrl(requestUrl)  -> responseBody // Protobuf - يتطلب معالجة منفصلة
                else -> responseBody
            }
        } catch (e: Exception) {
            Log.e(TAG, "Filter error for $requestUrl", e)
            responseBody
        }
    }

    // ===== فحص الـ URLs =====
    private fun isTikTokFeedUrl(url: String) =
        url.contains("tiktok") && (
            url.contains("/aweme/v") ||
            url.contains("/feed/") ||
            url.contains("/recommend/")
        )

    private fun isInstagramFeedUrl(url: String) =
        url.contains("instagram.com") && (
            url.contains("/feed/") ||
            url.contains("/timeline/") ||
            url.contains("graphql")
        )

    private fun isInstagramReelsUrl(url: String) =
        url.contains("instagram.com") && url.contains("/reels/")

    private fun isSnapchatDiscoverUrl(url: String) =
        url.contains("snapchat.com") && url.contains("/discover")

    // ===== فلتر TikTok =====
    fun filterTikTokFeed(jsonStr: String): String {
        val json = JSONObject(jsonStr)

        // نقاط TikTok المحتملة للـ feed
        val feedKeys = listOf("aweme_list", "data", "items")

        for (key in feedKeys) {
            if (json.has(key)) {
                val original = json.getJSONArray(key)
                val filtered = filterTikTokItems(original)
                json.put(key, filtered)
            }
        }

        return json.toString()
    }

    private fun filterTikTokItems(array: JSONArray): JSONArray {
        val result = JSONArray()
        var removed = 0

        for (i in 0 until array.length()) {
            val item = try { array.getJSONObject(i) } catch (e: Exception) {
                result.put(array.get(i)); continue
            }

            if (!isTikTokAd(item)) {
                result.put(item)
            } else {
                removed++
                filteredCount++
                Log.d(TAG, "Removed TikTok ad: aweme_id=${item.optString("aweme_id")}")
            }
        }

        if (removed > 0) Log.i(TAG, "TikTok: removed $removed ads from feed")
        return result
    }

    private fun isTikTokAd(item: JSONObject): Boolean {
        // المؤشرات المتعددة للإعلانات في TikTok API
        return item.optBoolean("is_ads", false) ||
               item.optBoolean("is_ad", false) ||
               item.optBoolean("is_commerce_goods", false) ||
               (item.optJSONObject("ad_info") != null && item.optJSONObject("ad_info")!!.length() > 0) ||
               item.has("ad_id") ||
               item.optString("aweme_type") in TikTokAdTypes.ALL ||
               item.optInt("aweme_type_v2", -1) in TikTokAdTypes.ALL_INT ||
               item.optString("label_top_text", "").contains("sponsored", ignoreCase = true) ||
               item.optString("label_text", "").contains("ad", ignoreCase = true) ||
               hasNestedAdFlag(item)
    }

    private fun hasNestedAdFlag(item: JSONObject): Boolean {
        // فحص الحقول المتداخلة
        val statistics = item.optJSONObject("statistics")
        if (statistics?.has("ad_id") == true) return true

        val textExtra = item.optJSONArray("text_extra")
        if (textExtra != null) {
            for (i in 0 until textExtra.length()) {
                val extra = textExtra.optJSONObject(i) ?: continue
                if (extra.optString("type") == "ad" ||
                    extra.optString("hashtag_name").contains("ad", ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    // ===== فلتر Instagram =====
    fun filterInstagramFeed(jsonStr: String): String {
        val json = JSONObject(jsonStr)
        filterInstagramNode(json)
        return json.toString()
    }

    private fun filterInstagramNode(node: JSONObject) {
        val arrayKeys = listOf(
            "edges", "items", "feed_items",
            "carousel_media", "tray"
        )

        for (key in arrayKeys) {
            if (node.has(key)) {
                val arr = node.getJSONArray(key)
                val filtered = filterInstagramEdges(arr)
                node.put(key, filtered)
            }
        }

        // فحص عميق للـ objects المتداخلة
        node.keys().forEach { key ->
            try {
                val child = node.optJSONObject(key)
                if (child != null) filterInstagramNode(child)
            } catch (_: Exception) {}
        }
    }

    private fun filterInstagramEdges(arr: JSONArray): JSONArray {
        val result = JSONArray()

        for (i in 0 until arr.length()) {
            val edge = try { arr.getJSONObject(i) } catch (e: Exception) {
                result.put(arr.get(i)); continue
            }

            // الـ node قد يكون مباشرة أو داخل "node" field
            val innerNode = edge.optJSONObject("node") ?: edge

            if (!isInstagramAd(edge) && !isInstagramAd(innerNode)) {
                result.put(edge)
                // فحص عميق للمحتوى الداخلي
                filterInstagramNode(innerNode)
            } else {
                filteredCount++
                Log.d(TAG, "Removed Instagram ad: ${innerNode.optString("id")}")
            }
        }

        return result
    }

    private fun isInstagramAd(node: JSONObject): Boolean {
        return node.optBoolean("is_ad", false) ||
               node.optBoolean("is_paid_partnership", false) ||
               node.optBoolean("paid_partnership", false) ||
               node.optString("__typename").contains("Ad", ignoreCase = true) ||
               node.optString("edge_type") == "AD_EDGE" ||
               node.has("ad_id") ||
               node.has("sponsor_tags") && node.optJSONArray("sponsor_tags")?.length()!! > 0 ||
               node.optJSONObject("edge_media_to_sponsor_user") != null ||
               containsSponsoredText(node)
    }

    private fun containsSponsoredText(node: JSONObject): Boolean {
        val textFields = listOf("accessibility_caption", "tracking_token", "overlay_text")
        val sponsoredKeywords = listOf("sponsored", "advertisement", "paid partner")

        return textFields.any { field ->
            val value = node.optString(field, "").lowercase()
            sponsoredKeywords.any { value.contains(it) }
        }
    }

    // ===== فلتر Instagram Reels =====
    fun filterInstagramReels(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("items")) {
                val items = json.getJSONArray("items")
                val filtered = filterInstagramEdges(items)
                json.put("items", filtered)
            }
            json.toString()
        } catch (e: Exception) {
            jsonStr
        }
    }
}

// ===== أنواع إعلانات TikTok المعروفة =====
object TikTokAdTypes {
    val ALL = setOf("150", "151", "152", "153", "154", "155")
    val ALL_INT = setOf(150, 151, 152, 153, 154, 155)
}

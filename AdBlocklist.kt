package com.blockads.filter

/**
 * قائمة شاملة بنطاقات الإعلانات والتتبع
 * مُحدَّثة لـ TikTok + Snapchat + Instagram + شبكات إعلانية عامة
 */
object AdBlocklist {

    fun getBlocklist(): Set<String> = buildSet {
        addAll(tiktokAds)
        addAll(metaAds)
        addAll(snapchatAds)
        addAll(thirdPartyNetworks)
        addAll(trackingDomains)
    }

    // ===== TikTok / ByteDance =====
    private val tiktokAds = setOf(
        "ads.tiktok.com",
        "ads-api.tiktok.com",
        "ads-sg.tiktok.com",
        "ads-us.tiktok.com",
        "analytics.tiktok.com",
        "business-api.tiktok.com",
        "mon.tiktok.com",
        "log.tiktok.com",
        "log2.tiktok.com",
        "metrics.tiktok.com",
        "track.tiktok.com",
        "aero.tiktok.com",
        "ads.muscdn.com",
        "tracker.muscdn.com",
        "applog.musical.ly",
        "log.musical.ly",
        "analytics.musical.ly",
        "mon.musical.ly",
        "ttwid.tiktok.com",
        "open-api.tiktok.com",
        "business.tiktok.com",
        "ads.tiktokglobalshop.com",
        "event.tiktokapis.com",
        "mon3.tiktok.com",
        "log-va.tiktok.com",
        "log-sg.tiktok.com",
    )

    // ===== Meta / Instagram / Facebook =====
    private val metaAds = setOf(
        "an.facebook.com",
        "ads.instagram.com",
        "pixel.facebook.com",
        "connect.facebook.net",
        "fbsbx.com",
        "graph-video.facebook.com",
        "creative.ak.fbcdn.net",
        "ads.facebook.com",
        "advertising.facebook.com",
        "adservice.google.com",
        "imasdk.googleapis.com",
        "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "tpc.googlesyndication.com",
    )

    // ===== Snapchat =====
    private val snapchatAds = setOf(
        "ads.snapchat.com",
        "tr.snapchat.com",
        "analytics.snapchat.com",
        "adsapi.snapchat.com",
        "adsnative.snapchat.com",
        "sc-analytics.appspot.com",
        "businesshelp.snapchat.com",
        "cf-adshare.sc-cdn.net",
        "adshare.sc-cdn.net",
        "ads-measurement.snapchat.com",
        "snap-ads-assets.sc-cdn.net",
    )

    // ===== شبكات إعلانية عامة =====
    private val thirdPartyNetworks = setOf(
        // Doubleclick / Google
        "doubleclick.net",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "googleadservices.com",
        // Attribution / Analytics
        "appsflyer.com",
        "adjust.com",
        "adjust.io",
        "branch.io",
        "kochava.com",
        "singular.net",
        "airbridge.io",
        // Ad Networks
        "mopub.com",
        "admob.com",
        "unity3d.com",
        "unityads.unity3d.com",
        "ironsrc.com",
        "vungle.com",
        "chartboost.com",
        "tapjoy.com",
        "adcolony.com",
        "inmobi.com",
        "mintegral.com",
        "pangle.io",
        "byteoversea.com",
        "ibytedtos.com",
        "pangleglobal.com",
        "smartadserver.com",
        "casalemedia.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "appnexus.com",
        "xandr.com",
        "sharethrough.com",
        "indexexchange.com",
        "lijit.com",
        "33across.com",
        "adsafeprotected.com",
        "moatads.com",
        "adsrvr.org",
        "owneriq.com",
        "lkqd.net",
        "zemanta.com",
        "bidswitch.net",
        "tidaltv.com",
        "yieldmo.com",
        "revcontent.com",
        "media.net",
        "nativo.com",
    )

    // ===== مواقع التتبع والتحليلات =====
    private val trackingDomains = setOf(
        "amplitude.com",
        "segment.com",
        "mixpanel.com",
        "heap.io",
        "hotjar.com",
        "fullstory.com",
        "logrocket.com",
        "bugsnag.com",
        "sentry.io",
        "datadog-browser-agent.com",
        "newrelic.com",
        "comscore.com",
        "nielsen.com",
        "scorecardresearch.com",
        "quantserve.com",
        "bluekai.com",
        "demdex.net",
        "everesttech.net",
        "omtrdc.net",
        "adobedtm.com",
        "crazyegg.com",
        "mouseflow.com",
        "luckyorange.com",
    )
}

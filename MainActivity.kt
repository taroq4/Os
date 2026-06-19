package com.blockads

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.blockads.vpn.BlockAdsVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAdsBlocked: TextView
    private lateinit var tvTikTokStatus: TextView
    private lateinit var tvSnapStatus: TextView
    private lateinit var tvInstaStatus: TextView
    private lateinit var ivShield: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDnsStatus: TextView
    private lateinit var tvQuicStatus: TextView
    private lateinit var tvJsonStatus: TextView

    private val VPN_REQUEST_CODE = 100

    // استقبال تحديثات الإحصائيات من VPN Service
    private val statsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BlockAdsVpnService.ACTION_STATS_UPDATE -> {
                    val blocked = intent.getLongExtra("ads_blocked", 0)
                    val dnsBlocked = intent.getLongExtra("dns_blocked", 0)
                    val quicBlocked = intent.getLongExtra("quic_blocked", 0)
                    val jsonFiltered = intent.getLongExtra("json_filtered", 0)
                    updateStats(blocked, dnsBlocked, quicBlocked, jsonFiltered)
                }
                BlockAdsVpnService.ACTION_VPN_STARTED -> onVpnStarted()
                BlockAdsVpnService.ACTION_VPN_STOPPED -> onVpnStopped()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        updateUiForCurrentState()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(BlockAdsVpnService.ACTION_STATS_UPDATE)
            addAction(BlockAdsVpnService.ACTION_VPN_STARTED)
            addAction(BlockAdsVpnService.ACTION_VPN_STOPPED)
        }
        ContextCompat.registerReceiver(this, statsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        updateUiForCurrentState()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statsReceiver)
    }

    private fun initViews() {
        btnToggle      = findViewById(R.id.btn_toggle)
        tvStatus       = findViewById(R.id.tv_status)
        tvAdsBlocked   = findViewById(R.id.tv_ads_blocked)
        tvTikTokStatus = findViewById(R.id.tv_tiktok_status)
        tvSnapStatus   = findViewById(R.id.tv_snap_status)
        tvInstaStatus  = findViewById(R.id.tv_insta_status)
        ivShield       = findViewById(R.id.iv_shield)
        progressBar    = findViewById(R.id.progress_bar)
        tvDnsStatus    = findViewById(R.id.tv_dns_status)
        tvQuicStatus   = findViewById(R.id.tv_quic_status)
        tvJsonStatus   = findViewById(R.id.tv_json_status)
    }

    private fun setupClickListeners() {
        btnToggle.setOnClickListener {
            if (BlockAdsVpnService.isRunning) {
                stopVpn()
            } else {
                requestVpnPermission()
            }
        }
    }

    // طلب إذن VPN من Android
    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            // الإذن ممنوح بالفعل
            startVpn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startVpn()
        } else {
            Toast.makeText(this, "يجب السماح بإذن VPN لتفعيل الحماية", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVpn() {
        showLoading(true)
        val serviceIntent = Intent(this, BlockAdsVpnService::class.java).apply {
            action = BlockAdsVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopVpn() {
        val serviceIntent = Intent(this, BlockAdsVpnService::class.java).apply {
            action = BlockAdsVpnService.ACTION_STOP
        }
        startService(serviceIntent)
    }

    private fun onVpnStarted() {
        showLoading(false)
        btnToggle.text = "🛑 إيقاف الحماية"
        btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.red_stop))
        tvStatus.text = "✅ الحماية مُفعَّلة"
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        ivShield.setImageResource(R.drawable.ic_shield_active)
        
        // تفعيل طبقات الحماية في الواجهة
        tvDnsStatus.text  = "✅ DNS Filtering: نشط"
        tvQuicStatus.text = "✅ QUIC/HTTP3 Block: نشط"
        tvJsonStatus.text = "✅ JSON Ad Filter: نشط"
        
        tvTikTokStatus.text = "🛡️ TikTok محمي"
        tvSnapStatus.text   = "🛡️ Snapchat محمي"
        tvInstaStatus.text  = "🛡️ Instagram محمي"

        // تحريك الشعار
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        ivShield.startAnimation(pulse)
    }

    private fun onVpnStopped() {
        showLoading(false)
        btnToggle.text = "🚀 تفعيل الحماية"
        btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_start))
        tvStatus.text = "⭕ الحماية غير مُفعَّلة"
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_inactive))
        ivShield.setImageResource(R.drawable.ic_shield)
        ivShield.clearAnimation()

        tvDnsStatus.text  = "⭕ DNS Filtering: متوقف"
        tvQuicStatus.text = "⭕ QUIC/HTTP3 Block: متوقف"
        tvJsonStatus.text = "⭕ JSON Ad Filter: متوقف"

        tvTikTokStatus.text = "⭕ TikTok غير محمي"
        tvSnapStatus.text   = "⭕ Snapchat غير محمي"
        tvInstaStatus.text  = "⭕ Instagram غير محمي"
    }

    private fun updateStats(blocked: Long, dns: Long, quic: Long, json: Long) {
        tvAdsBlocked.text = "إعلانات محجوبة: $blocked"
        tvDnsStatus.text  = "✅ DNS Filtering: حجب $dns طلب"
        tvQuicStatus.text = "✅ QUIC Block: حجب $quic حزمة"
        tvJsonStatus.text = "✅ JSON Filter: حذف $json إعلان"
    }

    private fun updateUiForCurrentState() {
        if (BlockAdsVpnService.isRunning) onVpnStarted()
        else onVpnStopped()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        btnToggle.isEnabled = !show
    }
}

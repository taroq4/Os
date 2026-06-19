package com.blockads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.blockads.vpn.BlockAdsVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("blockads", Context.MODE_PRIVATE)
            // إعادة تشغيل الحماية تلقائياً إذا كانت مفعَّلة قبل إعادة التشغيل
            if (prefs.getBoolean("auto_start", false)) {
                val serviceIntent = Intent(context, BlockAdsVpnService::class.java).apply {
                    action = BlockAdsVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}

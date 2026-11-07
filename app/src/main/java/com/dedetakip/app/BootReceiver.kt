package com.dedetakip.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

// Telefon yeniden başladığında takibi otomatik sürdür (kurulum bilgileri kayıtlıysa)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val kod = p.getString("kod", "") ?: ""
        if (kod.isEmpty()) return
        val i = Intent(context, TakipService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
    }
}

package com.dedetakip.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class TakipService : Service() {

    companion object {
        const val KANAL = "takip"
        const val KONTROL_MS = 60_000L   // "istek var mı" kontrol sıklığı (az veri/az pil)
    }

    @Volatile private var calisiyor = false
    private var isci: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        baslatOnPlan()
        if (!calisiyor) {
            calisiyor = true
            isci = Thread { dongu() }.apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    private fun baslatOnPlan() {
        val kanal = NotificationChannel(KANAL, "Dede Takip", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(kanal)
        val bildirim: Notification = Notification.Builder(this, KANAL)
            .setContentTitle("Dede Takip çalışıyor")
            .setContentText("Konum güvenli şekilde paylaşılıyor")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, bildirim)
        }
    }

    private fun dongu() {
        var sonRapor = 0L
        while (calisiyor) {
            try {
                val p = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                val sunucu = p.getString("sunucu", "") ?: ""
                val cihaz = p.getString("cihaz", "") ?: ""
                val kod = p.getString("kod", "") ?: ""
                if (sunucu.isNotEmpty() && cihaz.isNotEmpty() && kod.isNotEmpty()) {
                    val istek = JSONObject().put("cihaz", cihaz).put("secret", kod)
                    val cevap = post("$sunucu/api/bekleyen", istek)
                    val iste = cevap?.optBoolean("iste", false) ?: false
                    val periyot = (cevap?.optInt("periyot", 300) ?: 300) * 1000L
                    val simdi = System.currentTimeMillis()
                    if (iste || simdi - sonRapor >= periyot) {
                        if (raporGonder(sunucu, cihaz, kod)) sonRapor = simdi
                    }
                }
            } catch (_: Exception) { }
            try { Thread.sleep(KONTROL_MS) } catch (_: InterruptedException) { break }
        }
    }

    @SuppressLint("MissingPermission")
    private fun raporGonder(sunucu: String, cihaz: String, kod: String): Boolean {
        return try {
            val fused = LocationServices.getFusedLocationProviderClient(this)
            val task = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            val loc = Tasks.await(task, 30, TimeUnit.SECONDS) ?: return false
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batarya = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val j = JSONObject()
                .put("cihaz", cihaz).put("secret", kod)
                .put("lat", loc.latitude).put("lng", loc.longitude)
                .put("acc", loc.accuracy.toDouble())
                .put("batarya", batarya)
                .put("hiz", if (loc.hasSpeed()) loc.speed.toDouble() else JSONObject.NULL)
            post("$sunucu/api/report", j) != null
        } catch (_: Exception) { false }
    }

    private fun post(adres: String, govde: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val b = govde.toString().toByteArray(Charsets.UTF_8)
            val os: OutputStream = conn.outputStream
            os.write(b); os.flush(); os.close()
            if (conn.responseCode in 200..299) {
                val t = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(t)
            } else null
        } catch (_: Exception) { null } finally { conn?.disconnect() }
    }

    override fun onDestroy() {
        calisiyor = false
        isci?.interrupt()
        super.onDestroy()
    }
}

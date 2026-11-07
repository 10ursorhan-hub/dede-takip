package com.dedetakip.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        const val PREFS = "dede"
        const val VARSAYILAN_SUNUCU = "https://takip.77.245.146.28.sslip.io"
    }

    private lateinit var sunucu: EditText
    private lateinit var cihaz: EditText
    private lateinit var kod: EditText
    private lateinit var durum: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sunucu = findViewById(R.id.sunucu)
        cihaz = findViewById(R.id.cihaz)
        kod = findViewById(R.id.kod)
        durum = findViewById(R.id.durum)

        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sunucu.setText(p.getString("sunucu", VARSAYILAN_SUNUCU))
        cihaz.setText(p.getString("cihaz", "dede"))
        kod.setText(p.getString("kod", ""))

        findViewById<Button>(R.id.btnIzin).setOnClickListener { izinIste() }
        findViewById<Button>(R.id.btnBaslat).setOnClickListener { kaydetBaslat() }
        findViewById<Button>(R.id.btnDurdur).setOnClickListener {
            stopService(Intent(this, TakipService::class.java))
            durum.text = "Durduruldu."
        }

        durumGoster()
    }

    private fun izinIste() {
        val izinler = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) izinler.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(izinler.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            // Konum verildiyse arka plan konumunu ayrıca iste (Android 11+ bunu zorunlu ayrı adım yapar)
            if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Şimdi konumu 'Her zaman izin ver' olarak seçin.", Toast.LENGTH_LONG).show()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
            } else {
                pilUyari()
            }
        } else if (requestCode == 2) {
            pilUyari()
        }
        durumGoster()
    }

    // Pil optimizasyonu kapatma ekranına yönlendir (yoksa Android servisi uykuda durdurur)
    private fun pilUyari() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            }
        } catch (_: Exception) { }
    }

    private fun kaydetBaslat() {
        val s = sunucu.text.toString().trim().removeSuffix("/")
        val c = cihaz.text.toString().trim()
        val k = kod.text.toString().trim()
        if (s.isEmpty() || c.isEmpty() || k.isEmpty()) {
            Toast.makeText(this, "Tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Önce 1) İzinleri ver.", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("sunucu", s).putString("cihaz", c).putString("kod", k).apply()

        val i = Intent(this, TakipService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        durum.text = "Başlatıldı ✓ Uygulamayı kapatabilirsiniz; arka planda çalışır."
    }

    private fun durumGoster() {
        val bg = Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        durum.text = "Konum izni: " + (if (fine) "✓" else "✗") + "   Arka plan: " + (if (bg) "✓" else "✗")
    }
}

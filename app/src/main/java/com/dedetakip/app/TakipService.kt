package com.dedetakip.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Konum (foreground) + istek üzerine foto / sesli video. Kamera yalnız çekim anında açılır, sonra kapanır.
class TakipService : LifecycleService() {

    companion object {
        const val KANAL = "takip"
        const val KONTROL_MS = 60_000L
        const val VIDEO_SN = 10L
    }

    @Volatile private var calisiyor = false
    private var isci: Thread? = null
    private val anaHandler = Handler(Looper.getMainLooper())
    private val anaExec by lazy { ContextCompat.getMainExecutor(this) }
    private val kameraExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var kayit: Recording? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Kamera + mikrofon tipini servis başlarken (ön planda) al; böylece arka plandan da foto/video çekilebilir.
        foreground(true, true)
        if (!calisiyor) {
            calisiyor = true
            isci = Thread { dongu() }.apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    private fun bildirim(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(KANAL, "Dede Takip", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, KANAL)
            .setContentTitle("Dede Takip çalışıyor")
            .setContentText("Konum güvenli şekilde paylaşılıyor")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun izin(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    // Foreground servis tipi. Kamera/mikrofon tipi ön planda eklenebilir; arka planda (ör. boot) eklenemezse sadece konumla başla.
    private fun foreground(kamera: Boolean, mikrofon: Boolean) {
        if (Build.VERSION.SDK_INT < 34) { try { startForeground(1, bildirim()) } catch (_: Exception) {}; return }
        var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (kamera && izin(Manifest.permission.CAMERA)) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (mikrofon && izin(Manifest.permission.RECORD_AUDIO)) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try { startForeground(1, bildirim(), t) }
        catch (_: Exception) {
            try { startForeground(1, bildirim(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) } catch (_: Exception) {}
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
                    val cevap = post("$sunucu/api/bekleyen", JSONObject().put("cihaz", cihaz).put("secret", kod))
                    if (cevap != null) {
                        val iste = cevap.optBoolean("iste", false)
                        val foto = cevap.optBoolean("foto", false)
                        val video = cevap.optBoolean("video", false)
                        val yon = cevap.optString("yon", "arka")
                        val periyot = cevap.optInt("periyot", 300) * 1000L
                        val simdi = System.currentTimeMillis()
                        if (iste || simdi - sonRapor >= periyot) {
                            if (raporGonder(sunucu, cihaz, kod)) sonRapor = simdi
                        }
                        if (foto && izin(Manifest.permission.CAMERA)) cekFoto(sunucu, cihaz, kod, yon)
                        if (video && izin(Manifest.permission.CAMERA)) cekVideo(sunucu, cihaz, kod, yon)
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
            val loc = Tasks.await(fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null), 30, TimeUnit.SECONDS) ?: return false
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batStat = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val sarjli = (batStat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val j = JSONObject().put("cihaz", cihaz).put("secret", kod)
                .put("lat", loc.latitude).put("lng", loc.longitude).put("acc", loc.accuracy.toDouble())
                .put("batarya", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                .put("sarj", sarjli)
                .put("hiz", if (loc.hasSpeed()) loc.speed.toDouble() else JSONObject.NULL)
            post("$sunucu/api/report", j) != null
        } catch (_: Exception) { false }
    }

    private fun secici(yon: String) = if (yon == "on") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    private fun cekFoto(sunucu: String, cihaz: String, kod: String, yon: String) {
        val dosya = File(cacheDir, "foto.jpg")
        val latch = CountDownLatch(1)
        val ok = java.util.concurrent.atomic.AtomicBoolean(false)
        anaHandler.post {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    val provider = future.get()
                    provider.unbindAll()
                    val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    // ImageCapture tek başına bazı cihazlarda (Samsung) çekmiyor; akış sağlayan ImageAnalysis ile birlikte bağla
                    val analiz = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analiz.setAnalyzer(kameraExec) { im -> im.close() }
                    provider.bindToLifecycle(this, secici(yon), analiz, ic)
                    // Pozlama/odak otursun diye kısa bekleme, sonra çek
                    anaHandler.postDelayed({
                        ic.takePicture(ImageCapture.OutputFileOptions.Builder(dosya).build(), anaExec,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(o: ImageCapture.OutputFileResults) { ok.set(true); provider.unbindAll(); latch.countDown() }
                                override fun onError(e: ImageCaptureException) { provider.unbindAll(); latch.countDown() }
                            })
                    }, 1500)
                } catch (_: Exception) { latch.countDown() }
            }, anaExec)
        }
        try { latch.await(25, TimeUnit.SECONDS) } catch (_: Exception) { }
        if (ok.get() && dosya.exists()) { upload("$sunucu/api/foto", cihaz, kod, dosya.readBytes(), "image/jpeg"); dosya.delete() }
    }

    private fun cekVideo(sunucu: String, cihaz: String, kod: String, yon: String) {
        val dosya = File(cacheDir, "video.mp4")
        if (dosya.exists()) dosya.delete()
        val sesli = izin(Manifest.permission.RECORD_AUDIO)
        val latch = CountDownLatch(1)
        anaHandler.post {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    val provider = future.get()
                    provider.unbindAll()
                    val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD)).build()
                    val vc = VideoCapture.withOutput(recorder)
                    provider.bindToLifecycle(this, secici(yon), vc)
                    var pr = vc.output.prepareRecording(this, FileOutputOptions.Builder(dosya).build())
                    if (sesli) pr = pr.withAudioEnabled()
                    kayit = pr.start(anaExec) { ev ->
                        if (ev is VideoRecordEvent.Finalize) { provider.unbindAll(); latch.countDown() }
                    }
                    anaHandler.postDelayed({ try { kayit?.stop() } catch (_: Exception) { } }, VIDEO_SN * 1000)
                } catch (_: Exception) { latch.countDown() }
            }, anaExec)
        }
        try { latch.await(VIDEO_SN + 20, TimeUnit.SECONDS) } catch (_: Exception) { }
        kayit = null
        if (dosya.exists() && dosya.length() > 0) { upload("$sunucu/api/video", cihaz, kod, dosya.readBytes(), "video/mp4"); dosya.delete() }
    }

    private fun post(adres: String, govde: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(govde.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) JSONObject(conn.inputStream.bufferedReader().use { it.readText() }) else null
        } catch (_: Exception) { null } finally { conn?.disconnect() }
    }

    private fun upload(adres: String, cihaz: String, kod: String, veri: ByteArray, tip: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 60000; doOutput = true
                setRequestProperty("Content-Type", tip)
                setRequestProperty("X-Cihaz", cihaz)
                setRequestProperty("X-Secret", kod)
                setFixedLengthStreamingMode(veri.size)
            }
            conn.outputStream.use { it.write(veri) }
            conn.responseCode in 200..299
        } catch (_: Exception) { false } finally { conn?.disconnect() }
    }

    override fun onDestroy() {
        calisiyor = false
        isci?.interrupt()
        super.onDestroy()
    }
}

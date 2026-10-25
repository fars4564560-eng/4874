package com.example.privacyshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

/**
 * يراقب الكاميرا الأمامية؛ إذا ظهر وجهان أو أكثر يفعّل الحماية.
 * تحسينات: دقة تحليل منخفضة (640x480) وإيقاف الكاميرا عند إطفاء الشاشة لتوفير البطارية.
 */
class ShieldService : LifecycleService() {

    companion object {
        private const val TAG = "ShieldService"
        const val ACTION_STOP = "com.example.privacyshield.STOP"

        /** عدد الإطارات المتتالية اللازمة للتفعيل / الإلغاء (إطار كل ~400ms). */
        private const val ENABLE_AFTER = 3
        private const val DISABLE_AFTER = 8
        private const val FRAME_INTERVAL_MS = 400L

        @Volatile
        var running = false
            private set
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f) // يلتقط الوجوه البعيدة نسبياً
                .build()
        )
    }

    private var lastRun = 0L
    private var multiStreak = 0
    private var singleStreak = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pauseCamera()
                Intent.ACTION_SCREEN_ON -> startCamera()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForegroundNotification()
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission missing", e)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        startCamera()
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("shield", "Privacy Shield", NotificationManager.IMPORTANCE_LOW)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ShieldService::class.java).setAction(ACTION_STOP), flags
        )
        val n: Notification = NotificationCompat.Builder(this, "shield")
            .setContentTitle("Privacy Shield يعمل")
            .setContentText("مراقبة النظر الجانبي مفعّلة")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stop)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider unavailable", e)
                return@addListener
            }

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to bind front camera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun pauseCamera() {
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        multiStreak = 0
        singleStreak = 0
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = proxy.image
        if (media == null || now - lastRun < FRAME_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastRun = now

        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(img)
            .addOnSuccessListener { faces -> onFaceCount(faces.size) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onFaceCount(count: Int) {
        when {
            count >= 2 -> {
                multiStreak++; singleStreak = 0
                if (multiStreak >= ENABLE_AFTER) {
                    DisplayShield.on(Prefs.dim(this), Prefs.gray(this))
                }
            }
            count == 1 -> {
                singleStreak++; multiStreak = 0
                if (singleStreak >= DISABLE_AFTER) DisplayShield.off()
            }
            else -> {
                // لا وجه (نظرت بعيداً أو الكاميرا مغطاة): نُبقي الحالة كما هي.
                multiStreak = 0
                singleStreak = 0
            }
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(screenReceiver) }
        pauseCamera()
        DisplayShield.off()
        runCatching { detector.close() }
        executor.shutdown()
        super.onDestroy()
    }
}

package com.cera.driveraapp.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import com.cera.driveraapp.R

class CameraService : Service() {

    companion object {
        private const val CHANNEL_ID = "drivera_monitoring_channel"
        private const val ALARM_CHANNEL_ID = "drivera_alarm_channel"
        private const val NOTIFICATION_ID = 101
        private const val ALARM_NOTIFICATION_ID = 102
        const val ACTION_STOP_SERVICE = "STOP_DRIVERA_SERVICE"
        const val ACTION_STOP_ALARM = "STOP_ALARM"

        private const val TAG = "CameraService"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var faceAnalyzer: FaceAnalyzer
    private var cameraProvider: ProcessCameraProvider? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isDrowsyState = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildPersistentNotification())
        initDependencies()
        startCameraAnalysis()
    }

    private fun initDependencies() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        faceAnalyzer = FaceAnalyzer(applicationContext) { result ->
            if (result.isDrowsy && !isDrowsyState) {
                isDrowsyState = true
                triggerDrowsinessAlarm()
            } else if (!result.isDrowsy) {
                isDrowsyState = false
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service Channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Driver Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background camera monitoring"
            }

            // Alarm Channel
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Drowsiness Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(null, null) // We'll handle sound separately
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder().build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, faceAnalyzer)
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                DummyLifecycleOwner(),
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            stopSelf()
        }
    }

    private fun triggerDrowsinessAlarm() {
        // 1. Vibration
        startVibration()

        // 2. Sound Alarm
        initMediaPlayer()
        mediaPlayer?.start()

        // 3. Urgent Notification
        showAlarmNotification()
    }

    private fun startVibration() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(500, 500, 500),
                        intArrayOf(255, 0, 255),
                        0
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(longArrayOf(500, 500, 500), 0)
            }
        }
    }

    private fun initMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error - what: $what, extra: $extra")
                false
            }
            setOnCompletionListener {
                if (isDrowsyState) {
                    start() // Continue looping if still drowsy
                }
            }
        }
    }

    private fun showAlarmNotification() {
        val stopAlarmIntent = Intent(this, CameraService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopAlarmPendingIntent = PendingIntent.getService(
            this,
            0,
            stopAlarmIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("DROWSINESS DETECTED!")
            .setContentText("Please take a break immediately")
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_stop,
                "Stop Alarm",
                stopAlarmPendingIntent
            )
            .build()

        startForeground(ALARM_NOTIFICATION_ID, notification)
    }

    private fun buildPersistentNotification(): Notification {
        val stopServiceIntent = Intent(this, CameraService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent = PendingIntent.getService(
            this,
            0,
            stopServiceIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriveRA Active")
            .setContentText("Monitoring driver drowsiness")
            .setSmallIcon(R.drawable.ic_drivera_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_stop,
                "Stop Service",
                stopServicePendingIntent
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
            }
        }
        return START_STICKY
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        vibrator?.cancel()
        isDrowsyState = false
    }

    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
    }

    private fun cleanupResources() {
        cameraExecutor.shutdown()
        faceAnalyzer.shutdown()
        mediaPlayer?.release()
        vibrator?.cancel()
        cameraProvider?.unbindAll()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
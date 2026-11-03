package dk.opmeddig

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Spiller alarmlyden og vibrerer, indtil RingActivity stopper servicen. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0.3f

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (player != null) return START_STICKY

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
        )
        val ring = PendingIntent.getActivity(
            this, 3,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(ring, true)
            .setContentIntent(ring)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }

        // Skru alarmlydstyrken helt op
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            setVolume(volume, volume)
            start()
        }

        // Bliver højere hvert 3. sekund
        handler.post(object : Runnable {
            override fun run() {
                volume = (volume + 0.07f).coerceAtMost(1f)
                player?.setVolume(volume, volume)
                handler.postDelayed(this, 3000)
            }
        })

        // Lås alarmlydstyrken på fuld: skrues den ned (knapper eller panel), sættes den straks op igen
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        handler.post(object : Runnable {
            override fun run() {
                if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                }
                handler.postDelayed(this, 250)
            }
        })

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0))

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.run { stop(); release() }
        player = null
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "alarm"
        const val ACTION_STOP = "dk.opmeddig.STOP"
    }
}

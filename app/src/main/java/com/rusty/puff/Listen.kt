package com.rusty.puff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Find my phone": a small always-on listener that keeps one request open to echo-hub, so when the
 * Echo asks, the phone rings straight away (loud, on the alarm volume, even on silent) for a minute or
 * until "Found it". Android needs a notification while it runs; it sits quietly at the bottom.
 */
class ListenService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var player: MediaPlayer? = null
    private var stopJob: Job? = null
    private var savedVolume = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        if (intent?.action == ACTION_FOUND) {
            stopRinging()
            return START_STICKY
        }
        val n = NotificationCompat.Builder(this, CH_LISTEN)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("The Echo can ring this phone")
            .setContentText("Say \"where's my phone\" to the Echo, or tap 📱 on it")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_LISTEN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(NOTE_LISTEN, n)
        if (loop?.isActive != true) loop = scope.launch { listen() }
        return START_STICKY
    }

    private suspend fun listen() {
        val hub = Hub(this)
        val prefs = getSharedPreferences("puff", Context.MODE_PRIVATE)
        // Only asks made from now on (not one from before the phone was listening).
        var after = maxOf(prefs.getString("ring_after", null)?.toDoubleOrNull() ?: 0.0, System.currentTimeMillis() / 1000.0 - 60)
        var backoff = 5_000L
        while (scope.isActive) {
            if (!hub.paired || !enabled(this)) { stopSelf(); return }
            val seen = prefs.getInt("last_event", -1)
            val r = try { hub.waitForRing(after, seen) } catch (e: Hub.Unpaired) { stopSelf(); return }
            if (r == null) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000)
                continue
            }
            backoff = 5_000
            // Something to notify about (someone's here...): straight away rather than at the next sync.
            if (seen >= 0 && r.events > seen) runCatching { checkEvents(this, hub) }
            r.ring?.let { at ->
                after = at
                prefs.edit().putString("ring_after", at.toString()).apply()
                ring()
            }
        }
    }

    private fun ring() {
        if (player != null) return
        val audio = getSystemService(AudioManager::class.java)
        savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@ListenService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        val found = PendingIntent.getService(this, 1, Intent(this, ListenService::class.java).setAction(ACTION_FOUND),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH_RING)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("📱 Here I am!")
            .setContentText("The Echo is looking for this phone. Tap to stop.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(found)
            .setDeleteIntent(found)
            .addAction(0, "Found it", found)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTE_RING, n) }
        stopJob = scope.launch { delay(60_000); stopRinging() }
    }

    private fun stopRinging() {
        stopJob?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        getSystemService(Vibrator::class.java)?.cancel()
        if (savedVolume >= 0) {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            savedVolume = -1
        }
        NotificationManagerCompat.from(this).cancel(NOTE_RING)
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CH_LISTEN = "listen"
        private const val CH_RING = "ring"
        private const val NOTE_LISTEN = 9001
        private const val NOTE_RING = 9002
        private const val ACTION_FOUND = "com.rusty.puff.FOUND"

        fun enabled(context: Context) = context.getSharedPreferences("puff", Context.MODE_PRIVATE).getBoolean("find_phone", true)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences("puff", Context.MODE_PRIVATE).edit().putBoolean("find_phone", on).apply()
            if (on) start(context) else context.stopService(Intent(context, ListenService::class.java))
        }

        /** Start listening if it's switched on and the app is paired (from the app or at boot). */
        fun start(context: Context) {
            if (!enabled(context) || !Hub(context).paired) return
            runCatching { context.startForegroundService(Intent(context, ListenService::class.java)) }
        }

        fun channels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_LISTEN, "Listening for the Echo", NotificationManager.IMPORTANCE_MIN))
            nm.createNotificationChannel(NotificationChannel(CH_RING, "Find my phone", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the alarm sound plays itself
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }
}

/** After a restart, listen again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) ListenService.start(context)
    }
}

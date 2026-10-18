package com.rusty.puff

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/** Every 15 minutes (as often as Android allows): new things to notify about, and the widget. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val hub = Hub(applicationContext)
        if (!hub.paired) return Result.success()
        try {
            checkEvents(applicationContext, hub)
            refreshAgenda(applicationContext, hub)
        } catch (e: Hub.Unpaired) {
            return Result.success()
        }
        PuffWidget.refresh(applicationContext)
        return Result.success()
    }
}

object Sync {
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("puff-sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** Keep the leave-now alarms in step with the Echo's agenda (the cached world, refreshed if it changed). */
suspend fun refreshAgenda(context: Context, hub: Hub) {
    val file = java.io.File(context.filesDir, "world.json")
    val cached = runCatching { HubJson.decodeFromString(WorldReply.serializer(), file.readText()) }.getOrNull()
    val w = hub.world(cached?.at ?: 0.0)
    val fresh = if (w != null && !w.unchanged && w.journal != null) {
        runCatching { file.writeText(HubJson.encodeToString(WorldReply.serializer(), w)) }
        w
    } else cached
    fresh?.journal?.agenda?.let { Leave.schedule(context, it) }
}

/** Notifications for what echo-hub noticed (the first check only remembers where it's up to). */
suspend fun checkEvents(context: Context, hub: Hub) {
    val prefs = context.getSharedPreferences("puff", Context.MODE_PRIVATE)
    val last = prefs.getInt("last_event", -1)
    val reply = hub.events(last.coerceAtLeast(0)) ?: return
    if (last >= 0) {
        val wanted = hub.prefsOnHub() ?: emptyMap()
        Notify.pick(reply.events, wanted).forEach { Notify.show(context, it) }
    }
    prefs.edit().putInt("last_event", reply.last).apply()
}

object Notify {
    private val CHANNELS = listOf("letters" to "Letters", "stickers" to "Stickers", "cards" to "Holiday cards", "snow" to "The first snow", "weekly" to "Weekly report", "visitor" to "Someone's here?", "costume" to "New costume")

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        CHANNELS.forEach { (id, name) -> nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)) }
    }

    /** The events worth showing: switched on (on the settings page or here), newest five at most. */
    fun pick(events: List<HubEvent>, wanted: Map<String, Boolean>): List<HubEvent> =
        events.filter { wanted[it.kind] != false && it.kind in CHANNELS.map { c -> c.first } }.takeLast(5)

    fun show(context: Context, e: HubEvent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channels(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, e.kind)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(e.title)
            .setContentText(e.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                if (e.kind == "visitor") addAction(0, "Turn on guest mode", PendingIntent.getBroadcast(context, e.id,
                    Intent(context, GuestReceiver::class.java).putExtra("note", e.id), PendingIntent.FLAG_IMMUTABLE))
            }
            .build()
        NotificationManagerCompat.from(context).notify(e.id, n)
    }
}

/** "Turn on guest mode" on a someone's-here notification: 4 hours, like the Echo's own button. */
class GuestReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val note = intent.getIntExtra("note", 0)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val ok = runCatching {
                Hub(context).act(kotlinx.serialization.json.buildJsonObject {
                    put("action", kotlinx.serialization.json.JsonPrimitive("settings"))
                    put("changes", kotlinx.serialization.json.buildJsonObject { put("guest_hours", kotlinx.serialization.json.JsonPrimitive(4)) })
                }).first
            }.getOrDefault(false)
            NotificationManagerCompat.from(context).cancel(note)
            if (!ok) android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Couldn't reach the Echo", android.widget.Toast.LENGTH_SHORT).show()
            }
            pending.finish()
        }
    }
}

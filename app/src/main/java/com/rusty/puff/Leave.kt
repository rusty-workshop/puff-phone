package com.rusty.puff

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Time to leave" on the phone: the Echo works out when to leave for events with an address, and the
 * phone sets an alarm for each (so it buzzes on time even when the app hasn't synced for a while).
 */
object Leave {
    private const val MAX = 12
    const val CHANNEL = "leave"

    fun schedule(context: Context, agenda: AgendaInfo) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val today = LocalDate.now()
        val midnight = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("puff_leave", Context.MODE_PRIVATE)
        val nudged = prefs.getStringSet("nudged", emptySet()).orEmpty().filter { it.startsWith(today.toString()) }.toSet()
        val due = if (agenda.day != today.toString()) emptyList() else agenda.items.filter { i ->
            val leave = i.leaveMin ?: return@filter false
            midnight + leave * 60_000L > now - 2 * 60_000L && "$today|${i.title}|${i.time}" !in nudged
        }.take(MAX)
        for (k in 0 until MAX) {
            val i = due.getOrNull(k)
            val pi = PendingIntent.getBroadcast(context, 700 + k, Intent(context, LeaveReceiver::class.java).apply {
                if (i != null) {
                    putExtra("key", "$today|${i.title}|${i.time}")
                    putExtra("title", i.title)
                    putExtra("time", i.time)
                    putExtra("location", i.location)
                }
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (i == null) { am.cancel(pi); continue }
            val at = (midnight + i.leaveMin!! * 60_000L).coerceAtLeast(now + 5_000)
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        prefs.edit().putStringSet("nudged", nudged).apply()
    }

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Time to leave", NotificationManager.IMPORTANCE_HIGH))
    }
}

class LeaveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val prefs = context.getSharedPreferences("puff_leave", Context.MODE_PRIVATE)
        val nudged = prefs.getStringSet("nudged", emptySet()).orEmpty()
        if (key in nudged) return
        prefs.edit().putStringSet("nudged", nudged + key).apply()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        Leave.channel(context)
        val title = intent.getStringExtra("title").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val location = intent.getStringExtra("location").orEmpty()
        // Tap for directions.
        val maps = PendingIntent.getActivity(context, key.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, Leave.CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("🚗 Time to leave for $title")
            .setContentText(listOf(if (time.isNotBlank()) "Starts $time" else "", location).filter { it.isNotBlank() }.joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(maps)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), n)
    }
}

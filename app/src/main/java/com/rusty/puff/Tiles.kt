package com.rusty.puff

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Quick-settings tiles (in the phone's pull-down menu): straight to the Echo, no app needed. */
private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Opens the app at [what] ("announce" or "live") and closes the pull-down menu. */
private fun TileService.openApp(what: String) {
    val intent = Intent(this, MainActivity::class.java).putExtra("open", what).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE))
    else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
}

class PokeTile : TileService() {
    override fun onStartListening() { qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() } }
    override fun onClick() {
        qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() }
        tileScope.launch {
            runCatching { Hub(applicationContext).act(buildJsonObject { put("action", "poke") }) }
            kotlinx.coroutines.delay(800)
            qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
        }
    }
}

/** Quiet for an hour, or back to normal if it's already quiet. Shows which. */
class QuietTile : TileService() {
    override fun onStartListening() {
        tileScope.launch {
            val until = runCatching { Hub(applicationContext).settings() }.getOrNull()?.quietUntilMs ?: 0L
            show(until == -1L || until > System.currentTimeMillis())
        }
    }

    private fun show(on: Boolean) = qsTile?.apply {
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) subtitle = if (on) "Quiet" else "1 hour"
        updateTile()
    }

    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        show(!on)
        tileScope.launch {
            runCatching {
                Hub(applicationContext).act(buildJsonObject {
                    put("action", "settings")
                    putJsonObject("changes") { put("quiet_minutes", if (on) 0 else 60) }
                })
            }
        }
    }
}

class AnnounceTile : TileService() {
    override fun onClick() = openApp("announce")
}

class LiveTile : TileService() {
    override fun onClick() = openApp("live")
}

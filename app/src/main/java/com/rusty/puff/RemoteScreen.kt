package com.rusty.puff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Echo's remotes on the phone: the TV (Roku keys) and the laptop (media keys, lock, sleep,
 * brightness, shortcuts). Presses go through the Echo, which already knows how to reach both.
 */
@Composable
fun RemoteScreen(model: PuffModel) {
    val world by model.world.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val info = world?.journal?.remote ?: RemoteInfo()
    var which by rememberSaveable { mutableStateOf(if (info.tv || !info.laptop) "tv" else "laptop") }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    // While this is open, the Echo checks for presses every half second.
    LaunchedEffect(Unit) {
        while (isActive) {
            runCatching { model.hub.act(buildJsonObject { put("action", "hello") }) }
            delay(10_000)
        }
    }
    fun press(target: String, key: String) = model.act("remote", buildJsonObject { put("action", "remote"); put("target", target); put("key", key) }, "")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Remote", color = Pal.amber, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Small("Goes through the Echo, so it works wherever the app does.")
        Spacer(Modifier.height(10.dp))
        ChipRow { Chip("📺 TV", which == "tv") { which = "tv" }; Chip("💻 Laptop", which == "laptop") { which = "laptop" } }
        Spacer(Modifier.height(16.dp))
        if (which == "tv") {
            if (!info.tv) Small("The Echo hasn't found the TV (set it up on the Echo's TV page).", Pal.coral)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pad("▲") { press("tv", "Up") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pad("◀") { press("tv", "Left") }
                    Pad("OK", color = Pal.violet) { press("tv", "Select") }
                    Pad("▶") { press("tv", "Right") }
                }
                Pad("▼") { press("tv", "Down") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pad("↩ Back", wide = 110.dp) { press("tv", "Back") }
                    Pad("⌂ Home", wide = 110.dp) { press("tv", "Home") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pad("⏪") { press("tv", "Rev") }; Pad("⏯") { press("tv", "Play") }; Pad("⏩") { press("tv", "Fwd") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pad("🔉") { press("tv", "VolumeDown") }; Pad("🔇") { press("tv", "VolumeMute") }; Pad("🔊") { press("tv", "VolumeUp") }
                }
                Pad("Power", wide = 230.dp, color = Pal.coral) { press("tv", "Power") }
            }
        } else {
            if (!info.laptop) Small("The Echo isn't linked to the laptop.", Pal.coral)
            val l = state?.state?.blob?.laptop
            Card {
                Text(l?.track?.ifBlank { null } ?: "Nothing playing", color = Pal.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!l?.artist.isNullOrBlank()) Small(l!!.artist)
                l?.battery?.let { Small("Battery $it%") }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Pad("⏮") { press("laptop", "PREVIOUS") }
                    Pad(if (l?.playing == true) "⏸" else "▶", color = Pal.violet, size = 72.dp) { press("laptop", if (l?.playing == true) "PAUSE" else "PLAY") }
                    Pad("⏭") { press("laptop", "NEXT") }
                }
            }
            Section("Laptop")
            ChipRow {
                Chip("🔇 Mute") { press("laptop", "MUTE") }
                Chip("🔊 Unmute") { press("laptop", "UNMUTE") }
                Chip("🔅 Dimmer") { press("laptop", "BRIGHTNESS_DOWN") }
                Chip("🔆 Brighter") { press("laptop", "BRIGHTNESS_UP") }
            }
            Spacer(Modifier.height(8.dp))
            ChipRow {
                Chip("🔒 Lock") { confirm = "LOCK" }
                Chip("😴 Sleep") { confirm = "SLEEP" }
            }
            if (info.shortcuts.isNotEmpty()) {
                Section("Shortcuts")
                FlowChips(info.shortcuts.map { it.key to it.label }, null) { k -> press("laptop", "shortcut:$k") }
            }
        }
    }
    confirm?.let { what ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { confirm = null }, containerColor = Pal.card,
            title = { Text(if (what == "LOCK") "Lock the laptop?" else "Put the laptop to sleep?", color = Pal.ink) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { press("laptop", what); confirm = null }) { Text("Yes") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun Pad(label: String, size: Dp = 64.dp, wide: Dp = size, color: Color = Pal.card, onClick: () -> Unit) {
    Box(Modifier.width(wide).height(size).background(color, if (wide == size) CircleShape else RoundedCornerShape(50))
        .pressable(onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Pal.ink, fontSize = if (label.length > 2) 16.sp else 24.sp)
    }
}

/** Squishes a little when pressed, like the Echo's buttons. */
internal fun Modifier.pressable(onClick: () -> Unit): Modifier = this.composed {
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.9f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.45f), label = "press")
    Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(src, null, onClick = onClick)
}

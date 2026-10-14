package com.rusty.puff

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Echo's screen, live (about a picture a second), filling the phone sideways. Tap on it to tap
 * the Echo there; swipe across it to swipe the Echo (change pages). Only what the Echo's own app
 * shows (it can't see or touch anything else).
 */
@Composable
fun LiveScreen(model: PuffModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var age by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("Connecting to the Echo…") }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    // Sideways while it's open, back as it was after.
    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    LaunchedEffect(Unit) {
        // Keep asking for pictures, and fetch the newest one about once a second.
        launch { while (isActive) { runCatching { model.hub.watchScreen() }; delay(4_000) } }
        while (isActive) {
            val (bmp, a) = runCatching { model.hub.screen() }.getOrDefault(null to -1)
            if (bmp != null) { frame = bmp; age = a; note = "" }
            else if (a == -1 && frame == null) note = "The Echo's app isn't on screen right now (or it's asleep)."
            delay(800)
        }
    }
    fun send(kind: String, x: Float, y: Float, x2: Float = x, y2: Float = y) = model.act("touch", buildJsonObject {
        put("action", "touch"); put("kind", kind); put("x", x); put("y", y)
        if (kind == "swipe") { put("x2", x2); put("y2", y2) }
    }, "")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val f = frame
            if (f != null) {
                Box(Modifier.align(Alignment.Center).aspectRatio(f.width.toFloat() / f.height)
                    .onSizeChanged { box = it }
                    .pointerInput(Unit) {
                        detectTapGestures { p -> send("tap", p.x / box.width, p.y / box.height) }
                    }
                    .pointerInput(Unit) {
                        var start = Offset.Zero
                        var end = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start = it; end = it },
                            onDrag = { change, _ -> end = change.position },
                            onDragEnd = {
                                if ((end - start).getDistance() > 30f)
                                    send("swipe", start.x / box.width, start.y / box.height, end.x / box.width, end.y / box.height)
                            },
                        )
                    }) {
                    Image(f.asImageBitmap(), "The Echo's screen", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            if (note.isNotEmpty()) Text(note, color = Pal.muted, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                if (f != null && age > 5) Text("${age}s old  ", color = Pal.amber, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .pointerInput(Unit) { detectTapGestures { onClose() } }.padding(horizontal = 14.dp, vertical = 6.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                if (typing) Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(Pal.card, RoundedCornerShape(16.dp)).padding(6.dp)) {
                    androidx.compose.material3.OutlinedTextField(typed, { typed = it.take(300) }, singleLine = true,
                        placeholder = { Text("Type into the Echo's open box") }, modifier = Modifier.width(320.dp))
                    Chip("Type", typed.isNotEmpty()) {
                        if (typed.isNotEmpty()) { model.act("type", buildJsonObject { put("action", "type"); put("text", typed); put("enter", false) }, ""); typed = "" }
                    }
                    Chip("⏎ Enter") {
                        model.act("type", buildJsonObject { put("action", "type"); put("text", typed); put("enter", true) }, ""); typed = ""
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌨", color = Color.White, fontSize = 20.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .pointerInput(Unit) { detectTapGestures { typing = !typing } }.padding(horizontal = 12.dp, vertical = 4.dp))
                    Text("  Tap to tap the Echo · swipe to change pages · ⌨ to type", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

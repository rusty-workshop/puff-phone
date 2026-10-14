package com.rusty.puff

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The phone's Share menu, "Puff → Echo": text becomes a card on the Echo; a photo goes to the
 * slideshow or Puff's album (you pick). A small sheet over whatever app you shared from.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hub = Hub(this)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        @Suppress("DEPRECATION")
        val image = if (intent.type?.startsWith("image/") == true) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Pal.violet, background = Pal.night, surface = Pal.card)) {
                var status by remember { mutableStateOf(if (!hub.paired) "Open the Puff app and pair it first." else "") }
                val scope = rememberCoroutineScope()
                fun done(msg: String) { status = msg; scope.launch { delay(1_400); finish() } }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).background(Pal.card, RoundedCornerShape(20.dp)).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Send to the Echo", color = Pal.amber, fontSize = 18.sp)
                        if (status.isNotEmpty()) { Text(status, color = Pal.ink, fontSize = 15.sp); return@Column }
                        if (image != null) {
                            Text("A photo for…", color = Pal.muted, fontSize = 14.sp)
                            listOf("frame" to "🖼️ The slideshow", "album" to "📸 Puff's album").forEach { (dest, label) ->
                                Chip(label, true) {
                                    status = "Sending…"
                                    scope.launch {
                                        val jpeg = withContext(Dispatchers.IO) { runCatching { shrink(image) }.getOrNull() }
                                        val ok = jpeg != null && runCatching { hub.sendPhoto(jpeg, dest, subject) }.getOrDefault(false)
                                        done(if (ok) "Sent! It's on the Echo in a few seconds." else "Couldn't send that photo.")
                                    }
                                }
                            }
                        } else if (text.isNotEmpty()) {
                            Text(text.take(200) + if (text.length > 200) "…" else "", color = Pal.ink, fontSize = 15.sp)
                            Chip("📱 Show it on the Echo", true) {
                                status = "Sending…"
                                scope.launch {
                                    val (ok, _) = runCatching { hub.act(buildJsonObject {
                                        put("action", "card"); put("text", text.take(600)); put("title", subject.take(60))
                                    }) }.getOrDefault(false to "")
                                    done(if (ok) "Sent! It's up on the Echo." else "Couldn't reach the Echo.")
                                }
                            }
                        } else Text("Nothing to send here.", color = Pal.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Chip("Cancel") { finish() }
                    }
                }
            }
        }
    }

    private fun shrink(uri: Uri): ByteArray {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
            val edge = maxOf(info.size.width, info.size.height)
            if (edge > 1600) dec.setTargetSize(info.size.width * 1600 / edge, info.size.height * 1600 / edge)
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
}

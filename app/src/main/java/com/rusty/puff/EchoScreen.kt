package com.rusty.puff

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Composable
fun EchoScreen(model: PuffModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val reachable by model.reachable.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var announce by remember { mutableStateOf("") }
    var timerLabel by remember { mutableStateOf("") }
    var notify by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var update by remember { mutableStateOf<AppRelease?>(null) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var live by remember { mutableStateOf(false) }
    var allSettings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        notify = runCatching { model.hub.prefsOnHub() }.getOrNull()
        update = runCatching { model.hub.latestApp() }.getOrNull()?.takeIf { it.code > BuildConfig.VERSION_CODE }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }
    fun set(changes: Map<String, Any>, done: String) = model.act("settings", buildJsonObject {
        put("action", "settings")
        putJsonObject("changes") { changes.forEach { (k, v) -> when (v) { is Boolean -> put(k, v); is Int -> put(k, v) } } }
    }, done)
    val s = settings ?: EchoSettings()
    val now = System.currentTimeMillis()
    fun until(ms: Long, off: String) = when { ms == -1L -> "on until you turn it off"; ms > now -> "on until " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms)); else -> off }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("The Echo", color = Pal.amber, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Small(if (reachable) "Connected over ${model.hub.via}" + if (s.version.isNotBlank()) " · Echo ${s.version}" else "" else "Can't reach the Latitude right now")
        update?.let { u ->
            Spacer(Modifier.height(8.dp))
            Card(onClick = { scope.launch { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.hub.appUrl()))) } }) {
                Text("⬆️ An update for this app is ready (${u.version})", color = Pal.teal, fontSize = 15.sp); Small("Tap to download and install it")
            }
        }

        val journal = model.world.collectAsStateWithLifecycle().value?.journal
        journal?.agenda?.let { AgendaCard(it) }
        journal?.takeIf { it.weekRecap.isNotBlank() }?.let { j ->
            Section("📰 Your week")
            var open by remember { mutableStateOf(false) }
            Card(onClick = { open = !open }) {
                Small(java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.US).format(java.util.Date(j.weekRecapMs)))
                Text(j.weekRecap, color = Pal.ink, fontSize = 15.sp, lineHeight = 21.sp, maxLines = if (open) Int.MAX_VALUE else 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }

        Section("See the Echo")
        ChipRow {
            Chip("📺 Live screen", true) { live = true }
            Chip("⚙️ All Echo settings", true) { allSettings = true }
        }
        if (live) LiveScreen(model) { live = false }
        if (allSettings) SettingsWeb(model) { allSettings = false }

        Section("Send a photo")
        if (photo == null) Chip("🖼️ Pick a photo") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        else {
            var caption by remember { mutableStateOf("") }
            OutlinedTextField(caption, { caption = it.take(80) }, label = { Text("Caption (for the album)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ChipRow {
                listOf("frame" to "🖼️ To the slideshow", "album" to "📸 To ${model.blobName}'s album").forEach { (dest, label) ->
                    Chip(label, true) {
                        val uri = photo!!
                        photo = null
                        scope.launch {
                            model.say("Sending…")
                            val jpeg = withContext(Dispatchers.IO) { runCatching { shrink(ctx, uri) }.getOrNull() }
                            val ok = jpeg != null && model.hub.sendPhoto(jpeg, dest, caption)
                            model.say(if (ok) "Sent! It shows up on the Echo in a few seconds." else "Couldn't send that photo.")
                        }
                    }
                }
                Chip("Cancel") { photo = null }
            }
        }

        Section("Say something on the Echo")
        OutlinedTextField(announce, { announce = it.take(300) }, label = { Text("Dinner's ready!") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Chip("📣 Announce", announce.isNotBlank()) {
            if (announce.isNotBlank()) { model.act("announce", buildJsonObject { put("action", "announce"); put("text", announce) }, "Announced on the Echo"); announce = "" }
        }

        Section("Timer")
        OutlinedTextField(timerLabel, { timerLabel = it.take(40) }, label = { Text("What for (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        ChipRow {
            listOf(1, 5, 10, 15, 20, 30, 45, 60).forEach { m ->
                Chip("$m min") {
                    model.act("timer", buildJsonObject { put("action", "timer"); put("seconds", m * 60); put("label", timerLabel) }, "Timer set for $m min ⏱")
                    timerLabel = ""
                }
            }
        }

        Section("Hold still")
        Small(until(s.stillUntilMs, "Puff and friends move about as usual"))
        Spacer(Modifier.height(6.dp))
        ChipRow { listOf(0 to "Off", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", -1 to "Until I turn it off").forEach { (v, l) -> Chip(l) { set(mapOf("still_minutes" to v), "Hold still: $l") } } }

        Section("Quiet now")
        Small(until(s.quietUntilMs, "like the overnight quiet hours: no wake word, nothing out loud"))
        Spacer(Modifier.height(6.dp))
        ChipRow { listOf(0 to "Off", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours", -1 to "Until I turn it off").forEach { (v, l) -> Chip(l) { set(mapOf("quiet_minutes" to v), "Quiet: $l") } } }

        Section("Guest mode")
        Small(until(s.guestUntilMs, "hides the agenda, notes and reminders while people are over"))
        Spacer(Modifier.height(6.dp))
        ChipRow { listOf(0 to "Off", 1 to "1 hour", 2 to "2 hours", 4 to "4 hours").forEach { (v, l) -> Chip(l) { set(mapOf("guest_hours" to v), "Guest mode: $l") } } }

        Section("${model.blobName}'s world")
        Toggle("Friend visits", "friends drop by to play", s.blobFriend) { set(mapOf("blob_friend" to it), if (it) "Friends can visit" else "No more visits for now") }
        Toggle("Sniffly days", "now and then a cold for a day", s.puffSniffles) { set(mapOf("puff_sniffles" to it), "Saved") }
        Toggle("Sleepovers at friends'", "sometimes it stays the night away", s.puffAwaySleepovers) { set(mapOf("puff_away_sleepovers" to it), "Saved") }
        Toggle("New houses", "new friends arrive after a week of building", s.puffHouseBuild) { set(mapOf("puff_house_build" to it), "Saved") }

        Section("Notifications")
        val n = notify
        if (n == null) Small("Loading…")
        else listOf("letters" to "Letters", "stickers" to "Stickers", "cards" to "Holiday cards", "snow" to "The first snow", "weekly" to "Weekly report", "visitor" to "Someone's here?").forEach { (k, l) ->
            Toggle(l, "", n[k] != false) { on ->
                notify = n + (k to on)
                scope.launch { if (!model.hub.setPref(k, on)) model.say("Couldn't save that") }
            }
        }

        Section("Find my phone")
        var findOn by remember { mutableStateOf(ListenService.enabled(ctx)) }
        Toggle("Let the Echo ring this phone", "\"where's my phone?\" or 📱 on the Echo: it rings loudly for a minute, even on silent. Keeps a quiet notification while it listens.", findOn) {
            findOn = it
            ListenService.setEnabled(ctx, it)
        }

        Section("This phone")
        Small("Puff for phone ${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(6.dp))
        Chip("Unpair") { model.unpair() }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Pal.ink, fontSize = 15.sp); if (sub.isNotBlank()) Small(sub) }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pal.violet))
    }
}

/** The picked photo as a JPEG no bigger than 1600 px (the frame shows 1280). */
private fun shrink(ctx: android.content.Context, uri: Uri): ByteArray {
    val src = ImageDecoder.createSource(ctx.contentResolver, uri)
    val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
        val edge = maxOf(info.size.width, info.size.height)
        if (edge > 1600) dec.setTargetSize(info.size.width * 1600 / edge, info.size.height * 1600 / edge)
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}

/** The Echo's whole settings page (the one on the laptop), inside the app, over home Wi-Fi or Tailscale. */
@Composable
fun SettingsWeb(model: PuffModel, onClose: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { url = model.hub.settingsUrl() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pal.night)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Echo settings", color = Pal.amber, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Chip("Done") { onClose() }
            }
            val u = url
            if (u == null) Small("Finding the Echo…")
            else androidx.compose.ui.viewinterop.AndroidView(factory = { c ->
                android.webkit.WebView(c).apply {
                    @android.annotation.SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.parseColor("#0E0B14"))
                    webViewClient = android.webkit.WebViewClient()
                    loadUrl(u)
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** "Announce…" from the quick tile: one line to say on the Echo. */
@Composable
fun AnnounceDialog(model: PuffModel, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, containerColor = Pal.card,
        title = { Text("Say on the Echo", color = Pal.ink) },
        text = { OutlinedTextField(text, { text = it.take(300) }, label = { Text("Dinner's ready!") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            if (text.isNotBlank()) model.act("announce", buildJsonObject { put("action", "announce"); put("text", text) }, "Announced on the Echo")
            onClose()
        }) { Text("Announce") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancel") } })
}

/** Today's events from the Echo's agenda page, with when to leave, and the next few days. */
@Composable
private fun AgendaCard(a: AgendaInfo) {
    val today = java.time.LocalDate.now().toString()
    Section("📅 Today")
    Card {
        val items = if (a.day == today) a.items else emptyList()
        if (items.isEmpty()) Small(if (a.day == today) "Nothing on today" else "The Echo hasn't checked today's calendars yet")
        items.forEach { i ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(i.time.ifBlank { "·" }, color = Pal.teal, fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(i.title, color = Pal.ink, fontSize = 15.sp)
                    i.leaveMin?.let { m -> Small("🚗 Leave by " + clock(m), Pal.amber) }
                    if (i.location.isNotBlank()) Small(i.location)
                }
            }
        }
        val later = a.upcoming.filter { it.date > today || it.date.length != 10 }
        if (later.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Small("Coming up", Pal.amber)
            later.forEach { d ->
                val label = runCatching { java.time.LocalDate.parse(d.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) }.getOrDefault(d.date)
                Text(label, color = Pal.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                d.items.forEach { i -> Text((if (i.time.isNotBlank()) i.time + "  " else "") + i.title, color = Pal.ink, fontSize = 14.sp) }
            }
        }
    }
}

private fun clock(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60).mod(24)
    val m = minuteOfDay.mod(60)
    return "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

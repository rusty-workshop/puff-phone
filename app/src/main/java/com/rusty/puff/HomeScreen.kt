package com.rusty.puff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun HomeScreen(model: PuffModel) {
    val reply by model.state.collectAsStateWithLifecycle()
    val reachable by model.reachable.collectAsStateWithLifecycle()
    val world by model.world.collectAsStateWithLifecycle()
    val blob = reply?.state?.blob ?: BlobState()
    var sheet by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.blobName, color = Pal.amber, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(9.dp).background(if (reachable && reply?.online == true) Pal.teal else Pal.coral, CircleShape))
            Spacer(Modifier.width(6.dp))
            Small(when {
                !reachable -> "can't reach the Latitude"
                reply?.online != true -> "the Echo's offline"
                else -> "live"
            })
        }
        Text(blob.doing.ifBlank { "…" }, color = Pal.ink, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        LivePuff(model, blob)
        // The talent show's vote, while it's open on the Echo.
        if (blob.vote.isNotEmpty()) {
            Card {
                Text("🎭 Who was best at the talent show?", color = Pal.ink, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                ChipRow { blob.vote.forEach { a -> Chip(a.name, color = Pal.amber) { model.act("vote", buildJsonObject { put("action", "vote"); put("id", a.id) }, "Voted for ${a.name}! 🏆") } } }
            }
        }
        world?.journal?.friends?.let { w -> DebateCard(model, w); MysteryCard(model, w) }
        Section("Do something")
        ChipRow {
            Chip("👋 Poke") { model.act("poke", buildJsonObject { put("action", "poke") }, "${model.blobName} felt that! 👋") }
            Chip("🚶 Invite a friend") { sheet = "invite" }
            Chip("🎁 Send a gift") { sheet = "gift" }
            Chip("✨ Make something happen") { sheet = "happen" }
            Chip("🥠 Fortune cookie") { model.act("fortune", buildJsonObject { put("action", "fortune") }, "Cracking it open on the Echo… 🥠") }
            Chip("✉️ Write to ${model.blobName}") { sheet = "letter" }
            Chip("👗 Closet") { sheet = "closet" }
            Chip("🐾 Pet tricks") { sheet = "tricks" }
        }
        reply?.state?.timers?.takeIf { it.isNotEmpty() }?.let { timers ->
            Section("Timers on the Echo")
            timers.forEach { tm -> Small("⏱ ${tm.label.ifBlank { tm.kind }} · ${tm.leftS / 60} min ${tm.leftS % 60} s left", Pal.ink) }
        }
        world?.journal?.friends?.cards?.lastOrNull { it.date == java.time.LocalDate.now().toString() }?.let { c ->
            Section("Today")
            Card { Text("💌 A ${c.holiday} card for you", color = Pal.amber, fontSize = 16.sp); Body(c.text); Small("Love from ${c.from}") }
        }
    }
    when (sheet) {
        "invite" -> InviteSheet(model, world?.journal?.friends?.friends.orEmpty()) { sheet = null }
        "gift" -> GiftSheet(model) { sheet = null }
        "happen" -> HappenSheet(model) { sheet = null }
        "letter" -> LetterSheet(model) { sheet = null }
        "closet" -> ClosetSheet(model, world?.journal?.friends ?: World()) { sheet = null }
        "tricks" -> TricksSheet(model, world?.journal?.friends ?: World()) { sheet = null }
    }
}

/** Puff as it is on the Echo right now (and whoever's over), redrawn smoothly; tap to poke. */
@Composable
private fun LivePuff(model: PuffModel, blob: BlobState) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) { t = (System.currentTimeMillis() - start) / 1000f; delay(33) }
    }
    val tint = parseHex(blob.color, PuffViolet)
    Box(Modifier.fillMaxWidth().height(300.dp).background(Pal.card.copy(alpha = 0.5f), RoundedCornerShape(24.dp))) {
        if (blob.awayAt != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (blob.awayOvernight) "🌙" else "🧳", fontSize = 56.sp)
                Text(if (blob.awayOvernight) "Sleeping over at ${blob.awayAt}'s" else "Round at ${blob.awayAt}'s house", color = Pal.ink, fontSize = 18.sp)
                Small("back soon")
            }
            return@Box
        }
        val visitors = blob.visitors.take(3)
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * 0.33f
            val puffAt = Offset(if (visitors.isEmpty()) size.width / 2 else size.width * 0.38f, size.height * 0.55f)
            visitors.forEachIndexed { i, v ->
                val fr = r * 0.6f * v.scale
                val at = Offset(size.width * (0.72f + 0.12f * (i % 2)) - (i / 2) * r * 0.2f, size.height * (0.62f - 0.18f * (i % 2)))
                drawBlob(at, fr, parseHex(v.color, Pal.coral), "happy", t + i, still = blob.asleep)
            }
            drawBlob(puffAt, r, tint, if (blob.asleep) "sleepy" else blob.mood, t, sniffles = blob.sniffles)
        }
        // Poking: Puff in the middle (or left), friends on the right.
        Box(Modifier.align(if (visitors.isEmpty()) Alignment.Center else Alignment.CenterStart).padding(start = if (visitors.isEmpty()) 0.dp else 40.dp)
            .size(180.dp).clickable(remember { MutableInteractionSource() }, null) {
                model.act("poke", buildJsonObject { put("action", "poke") }, "${model.blobName} felt that! 👋")
            })
        visitors.forEachIndexed { i, v ->
            Box(Modifier.align(Alignment.CenterEnd).offset(x = (-30 - 40 * (i % 2)).dp, y = (20 - 50 * (i % 2)).dp).size(80.dp)
                .clickable(remember { MutableInteractionSource() }, null) {
                    model.act("poke", buildJsonObject { put("action", "poke"); put("friend_id", v.id) }, "${v.name} giggled 😆")
                })
        }
        accessoryEmoji(blob.accessory)?.let { Text(it, fontSize = 30.sp, modifier = Modifier.align(Alignment.TopCenter).offset(x = if (visitors.isEmpty()) 0.dp else (-50).dp, y = 30.dp)) }
        heldEmoji(blob.holding)?.let { e ->
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp).background(Pal.night.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(e, fontSize = 18.sp)
                blob.label?.takeIf { it.isNotBlank() }?.let { Text(" $it", color = Pal.ink, fontSize = 13.sp) }
            }
        }
        // Speech: Puff's bubble top-left, the visitors' top-right.
        Column(Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 220.dp)) {
            blob.bubble?.let { Bubble(it, Color.White) }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(12.dp).widthIn(max = 180.dp), horizontalAlignment = Alignment.End) {
            visitors.forEach { v -> v.says?.let { Bubble("${v.name}: $it", parseHex(v.color, Pal.coral)) } }
            if (blob.visitors.size > 3) Small("+${blob.visitors.size - 3} more over")
        }
    }
}

@Composable
private fun Bubble(text: String, color: Color) {
    Text(text, color = Pal.night, fontSize = 13.sp, textAlign = TextAlign.Start,
        modifier = Modifier.padding(bottom = 4.dp).background(color.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(model: PuffModel, friends: List<Friend>, preset: String? = null, onClose: () -> Unit) {
    var who by remember { mutableStateOf(preset) }
    var what by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Pal.night) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Invite a friend over", color = Pal.ink, fontSize = 20.sp)
            Section("Who")
            ChipRow {
                Chip("Anyone", who == null) { who = null }
                friends.filter { it.name.isNotBlank() }.forEach { f -> Chip(f.shownName, who == f.name, Color(f.color)) { who = f.name } }
            }
            Section("To do")
            FlowChips(listOf<Pair<String?, String>>(null to "✨ Whatever they like") + ACTIVITIES, what) { what = it }
            Spacer(Modifier.height(16.dp))
            Chip("Send the invite 🚶", true) {
                model.act("invite", buildJsonObject { put("action", "invite"); put("name", who ?: ""); put("activity", what ?: "") },
                    "Invited ${who ?: "a friend"}! They'll be over in a moment.")
                onClose()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun <T> FlowChips(items: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (v, label) -> Chip(label, v == selected) { onPick(v) } } }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GiftSheet(model: PuffModel, onClose: () -> Unit) {
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Pal.night) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Send ${model.blobName} a gift", color = Pal.ink, fontSize = 20.sp)
            OutlinedTextField(note, { note = it.take(120) }, label = { Text("A little note (optional)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            FlowChips(GIFTS.map { it to it.third }, null) { (kind, value, label) ->
                model.act("gift", buildJsonObject { put("action", "gift"); put("kind", kind); put("value", value); put("note", note) }, "Sent $label! 🎁")
                onClose()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HappenSheet(model: PuffModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Pal.night) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Make something happen", color = Pal.ink, fontSize = 20.sp)
            Small("Pixel writes the words, so letters and comics take a minute or two.")
            Spacer(Modifier.height(8.dp))
            FlowChips(HAPPENINGS, null) { v ->
                model.act("play", buildJsonObject { put("action", "play"); put("value", v) }, "On it! Watch the Echo ✨")
                onClose()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

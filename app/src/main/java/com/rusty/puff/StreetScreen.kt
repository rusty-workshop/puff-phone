package com.rusty.puff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val WHERE_LABEL = mapOf("HOME" to "home", "OUT" to "out and about", "ASLEEP" to "asleep", "VISITING" to "visiting", "AWAY" to "away on a trip")

@Composable
fun StreetScreen(model: PuffModel) {
    val reply by model.world.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    var invite by remember { mutableStateOf<Friend?>(null) }
    var looking by remember { mutableStateOf<Friend?>(null) }
    val j = reply?.journal
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Neighbourhood", color = Pal.amber, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (j == null) { Empty("The street hasn't arrived yet."); return@Column }
        val st = j.street
        val w = j.friends
        val hour = st.hour
        Small(when (st.festival) {
            "FAIR" -> "🍎 Harvest fair today!"
            "PARADE" -> "🏮 Winter lights parade tonight!"
            else -> if (st.market) "🧺 Market day: stalls are out" else "Tap a house to look inside, or invite someone over."
        }, if (st.festival != null) Pal.amber else Pal.muted)
        Spacer(Modifier.height(12.dp))
        val sky = when (hour) {
            in 7..16 -> listOf(Color(0xFF6FA8DC), Color(0xFFB4D7F0))
            in 17..19 -> listOf(Color(0xFFE07A5F), Color(0xFFF2CC8F))
            else -> listOf(Color(0xFF0B1030), Color(0xFF1B2050))
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(sky.map { it.copy(alpha = 0.35f) }), RoundedCornerShape(18.dp)).padding(vertical = 12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom) {
                val puffWhere = if (state?.state?.blob?.awayAt != null) "OUT" else "HOME"
                val unread = w.letters.count { !it.read && !it.fromBlob }
                House(model.blobName, PuffViolet, 1.15f, puffWhere, if (unread > 0) "📬 $unread new" else "home", hour, mailbox = true) {}
                w.friends.forEach { f ->
                    val where = st.where[f.id] ?: "HOME"
                    House(f.shownName, Color(f.color), f.scale, where, WHERE_LABEL[where] ?: "", hour, upgrade = w.upgrades[f.id] ?: 0) { looking = f }
                }
                w.building?.takeIf { it.friendId.isEmpty() }?.let { b ->
                    val day = runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(b.startDate), java.time.LocalDate.now()).toInt() }.getOrDefault(0)
                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏗️", fontSize = 44.sp)
                        Text("New house", color = Pal.ink, fontSize = 13.sp)
                        Small(if (day >= 7) "moving day!" else "day ${day + 1} of 7")
                    }
                }
                Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (st.activity == "picnic") "🧺🌳" else "🌳🦆", fontSize = 40.sp)
                    Text("The park", color = Color(0xFF7FC88A), fontSize = 13.sp)
                    Small(if (st.activity == "picnic") "picnic!" else "quiet")
                }
            }
        }
        Section("Who's where")
        w.friends.filter { it.name.isNotBlank() }.forEach { f ->
            val where = st.where[f.id] ?: "HOME"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { looking = f }, verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = Color(f.color), fontSize = 16.sp)
                Text("  ${f.shownName}", color = Pal.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Small(WHERE_LABEL[where] ?: where.lowercase())
            }
        }
    }
    looking?.let { f ->
        val where = j?.street?.where?.get(f.id) ?: "HOME"
        androidx.compose.material3.AlertDialog(onDismissRequest = { looking = null }, containerColor = Pal.card,
            title = { Text("${f.shownName}'s house", color = Color(f.color)) },
            text = {
                Column {
                    Small("${f.personality.lowercase()} · ${WHERE_LABEL[where] ?: where} · closeness ${f.closeness}/100")
                    if (f.bio.isNotBlank()) Body(f.bio)
                    if (f.petKind.isNotBlank()) Small("Pet: ${f.petName.ifBlank { "a" }} ${f.petKind}")
                    if (f.birthday.isNotBlank()) Small("🎂 Birthday: ${f.birthday}")
                }
            },
            confirmButton = {
                if (where == "HOME" || where == "OUT") androidx.compose.material3.TextButton(onClick = { invite = f; looking = null }) { Text("Invite over 🚶") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { looking = null }) { Text("Close") } })
    }
    invite?.let { f -> InviteSheet(model, j?.friends?.friends.orEmpty(), preset = f.name) { invite = null } }
}

@Composable
private fun House(name: String, c: Color, scale: Float, where: String, status: String, hour: Int, mailbox: Boolean = false, upgrade: Int = 0, onTap: () -> Unit) {
    Column(Modifier.width(96.dp).clickable(onClick = onTap), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(90.dp).height((66 + 28 * scale).dp)) { drawHouse(c, where, hour, mailbox) }
        Text(name, color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status + if (upgrade > 0) " · ✨" else "", color = Pal.muted, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

private fun DrawScope.drawHouse(c: Color, where: String, hour: Int, mailbox: Boolean) {
    val w = size.width * 0.78f
    val wallH = size.height * 0.52f
    val left = (size.width - w) / 2
    val top = size.height - wallH
    drawRoundRect(lerp(c, Color(0xFF1B1526), 0.45f), Offset(left, top), Size(w, wallH), CornerRadius(4f))
    drawPath(Path().apply { moveTo(left - w * 0.08f, top + 2f); lineTo(size.width / 2, top - size.height * 0.36f); lineTo(left + w * 1.08f, top + 2f); close() },
        lerp(c, Color(0xFF0E0B14), 0.2f))
    val doorW = w * 0.24f
    val doorH = wallH * 0.55f
    val doorX = left + w * 0.62f
    drawRoundRect(Color(0xFF3A2A24), Offset(doorX, size.height - doorH), Size(doorW, doorH), CornerRadius(3f, 3f))
    if (where == "VISITING") drawRect(Color(0xFFFFE6A8).copy(alpha = 0.7f), Offset(doorX + doorW * 0.15f, size.height - doorH), Size(doorW * 0.7f, doorH))
    val win = Offset(left + w * 0.12f, top + wallH * 0.2f)
    val winS = Size(w * 0.36f, wallH * 0.38f)
    val lit = where == "HOME"
    val night = hour >= 18 || hour < 7
    drawRoundRect(if (lit) Color(0xFFFFE08A).copy(alpha = if (night) 0.95f else 0.6f) else Color(0xFF151220), win, winS, CornerRadius(3f))
    if (lit) drawCircle(c, winS.height * 0.32f, win + Offset(winS.width / 2, winS.height * 0.62f))
    if (where == "ASLEEP") drawCircle(Color.White.copy(alpha = 0.6f), 2.5f, win + Offset(winS.width + 6f, -4f))
    if (mailbox) {
        val mb = Offset(left - w * 0.06f, size.height - wallH * 0.45f)
        drawLine(Color(0xFF6E5A4A), mb + Offset(0f, wallH * 0.2f), Offset(mb.x, size.height), 3f)
        drawRoundRect(Color(0xFF8C9AA8), mb + Offset(-8f, 0f), Size(16f, 11f), CornerRadius(5f))
    }
}

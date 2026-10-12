package com.rusty.puff

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val SECTIONS = listOf("diary" to "📔 Diary", "letters" to "✉️ Letters", "comics" to "📰 Comics", "gazette" to "🗞️ Gazette",
    "album" to "📸 Album", "friends" to "👯 Friends", "dreams" to "💭 Dreams", "cookbook" to "🍳 Cookbook", "books" to "📚 Books",
    "stickers" to "🏅 Stickers", "cabinet" to "🗄️ Cabinet", "weather" to "📅 Weather", "stars" to "🔭 Stars")

@Composable
fun WorldScreen(model: PuffModel) {
    val reply by model.world.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("diary") }
    var reading by remember { mutableStateOf<Pair<String, String>?>(null) }
    val j = reply?.journal
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        ChipRow { SECTIONS.forEach { (id, label) -> Chip(label, section == id) { section = id } } }
        Spacer(Modifier.height(8.dp))
        if (j == null) {
            Empty("Puff's world hasn't arrived yet. It comes from the Echo every few minutes.")
            return@Column
        }
        val w = j.friends
        val names = w.friends.associate { it.id to it.shownName } + ("rusty" to "you") + ("penpal" to (w.penpal?.name ?: "the pen pal"))
        when (section) {
            "diary" -> TextList(j.diary.reversed().map { prettyDate(it.date) to it.text }, "Nothing yet: it's written in the evening.")
            "dreams" -> TextList(w.dreams.reversed().map { prettyDate(it.date) to it.text }, "No dreams written down yet.", italic = true)
            "letters" -> LazyColumn {
                if (w.letters.isEmpty()) item { Empty("No letters yet.") }
                items(w.letters.reversed()) { l ->
                    val who = names[l.friendId] ?: "a friend"
                    val title = when {
                        l.fromBlob && l.friendId == "rusty" -> "💌 ${model.blobName} to you"
                        l.fromBlob -> "📮 ${model.blobName} to $who"
                        else -> "✉️ From $who"
                    }
                    Card(onClick = { reading = "$title · ${prettyDate(l.date)}" to l.text }) {
                        Text(title, color = Pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Small(prettyDate(l.date) + " · " + l.text.replace("\n", " ").take(80) + "…")
                    }
                }
            }
            "comics" -> LazyColumn {
                if (w.comics.isEmpty()) item { Empty("No comics yet: drawn after 8 pm.") }
                items(w.comics.reversed()) { c ->
                    Card {
                        Text(c.title, color = Pal.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Small(prettyDate(c.date))
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.panels.take(3).forEachIndexed { i, p ->
                                Column(Modifier.weight(1f).background(Pal.night, RoundedCornerShape(8.dp)).padding(6.dp)) {
                                    Small("${i + 1}. ${p.who.joinToString()}")
                                    if (p.caption.isNotBlank()) Text(p.caption, color = Color.Black, fontSize = 11.sp, modifier = Modifier.background(Color(0xFFFFE680), RoundedCornerShape(3.dp)).padding(2.dp))
                                    if (p.speech.isNotBlank()) Text("“${p.speech}”", color = Pal.ink, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            "gazette" -> LazyColumn {
                if (w.gazettes.isEmpty()) item { Empty("The Blob Gazette comes out on Sunday evenings.") }
                items(w.gazettes.reversed()) { g ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Color(0xFFF2EADA), RoundedCornerShape(6.dp)).padding(12.dp)) {
                        val ink = Color(0xFF2A2420)
                        Text("THE BLOB GAZETTE · ${prettyDate(g.date)}", color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text(g.headline, color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        g.articles.forEach { a ->
                            Text(a.title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                            Text(a.text, color = ink.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                        if (g.weather.isNotBlank()) Text("☁️ ${g.weather}", color = ink.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            "album" -> AlbumGrid(model)
            "friends" -> LazyColumn {
                items(w.friends.filter { it.name.isNotBlank() }) { f ->
                    val diary = w.diary.filter { it.friendId == f.id }.reversed()
                    Card(onClick = { reading = f.shownName to (f.bio + "\n\n" + diary.joinToString("\n\n") { "${prettyDate(it.date)}: ${it.text}" }).trim() }) {
                        Text("● ${f.shownName}", color = Color(f.color), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Small("${f.personality.lowercase()} · closeness ${f.closeness}/100 · ${f.visits} visits" + if (f.trophies > 0) " · 🏆${f.trophies}" else "")
                        if (f.bio.isNotBlank()) Body(f.bio)
                    }
                }
                w.penpal?.let { p -> item { Card { Text("🌍 ${p.name}, the pen pal", color = Pal.teal, fontSize = 16.sp); Small(p.place); Body(p.bio) } } }
            }
            "cookbook" -> LazyColumn {
                if (w.recipes.isEmpty()) item { Empty("Nothing baked yet.") }
                items(w.recipes.reversed()) { r ->
                    Card(onClick = {
                        reading = r.title to ("By ${r.cooks}, ${prettyDate(r.date)}" + (if (r.real) "\nA real recipe you can make!" else "") +
                            "\n\nIngredients\n" + r.ingredients.joinToString("\n") { "• $it" } + "\n\nSteps\n" + r.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))
                    }) {
                        Text((if (r.real) "🧑‍🍳 " else "🧁 ") + r.title, color = Pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Small("${r.cooks} · ${prettyDate(r.date)}" + if (r.real) " · real recipe" else "")
                    }
                }
            }
            "books" -> TextList(w.books.reversed().map { "📖 ${it.title}" to "${it.reader}: ${it.review}" }, "No books yet.")
            "stickers" -> LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(j.catalog.stickers) { s ->
                    val got = w.stickers[s.id]
                    Column(Modifier.background(Pal.card.copy(alpha = if (got != null) 1f else 0.4f), RoundedCornerShape(12.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (got != null) s.emoji else "🔒", fontSize = 26.sp)
                        Text(s.title, color = if (got != null) Pal.ink else Pal.muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Small(if (s.who == "you") "yours" else model.blobName)
                    }
                }
            }
            "cabinet" -> LazyColumn {
                j.catalog.collectibles.groupBy { it.set }.forEach { (set, list) ->
                    item {
                        Section(set)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.forEach { c ->
                                val got = w.collection[c.id] != null
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (got) c.emoji else "❔", fontSize = 24.sp)
                                    Text(if (got) c.name else "???", color = if (got) Pal.ink else Pal.muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            "weather" -> WeatherMonth(w.weatherDays)
            "stars" -> TextList(w.constellations.reversed().map { "⭐ ${it.name}" to "${it.shape}\n${it.by}, ${prettyDate(it.date)}" }, "None named yet (on clear nights).")
        }
    }
    reading?.let { (title, text) -> Reader(title, text) { reading = null } }
}

@Composable
private fun TextList(items: List<Pair<String, String>>, empty: String, italic: Boolean = false) {
    LazyColumn {
        if (items.isEmpty()) item { Empty(empty) }
        items(items) { (title, text) -> Card { Small(title, Pal.amber); Body(text, italic) } }
    }
}

@Composable
private fun WeatherMonth(days: Map<String, String>) {
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("◀") { month = month.minusMonths(1) }
            Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US)), color = Pal.amber, fontSize = 16.sp,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Chip("▶") { if (month < java.time.YearMonth.now()) month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(8.dp))
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = List(lead) { 0 } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Column(Modifier.weight(1f).padding(2.dp).background(Pal.card.copy(alpha = if (d > 0) 0.7f else 0f), RoundedCornerShape(6.dp)).padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (d > 0) {
                            Small("$d")
                            Text(days[month.atDay(d).toString()]?.replace("|", "") ?: " ", fontSize = 14.sp)
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The album pictures, daily ones and friend moments, loaded over the hub (with the token). */
@Composable
private fun AlbumGrid(model: PuffModel) {
    var kind by rememberSaveable { mutableStateOf("friends") }
    var shots by remember(kind) { mutableStateOf<List<AlbumShot>?>(null) }
    var open by remember { mutableStateOf<AlbumShot?>(null) }
    LaunchedEffect(kind) { shots = runCatching { model.hub.album(kind) }.getOrNull()?.reversed() ?: emptyList() }
    Column {
        ChipRow { Chip("👯 Friend moments", kind == "friends") { kind = "friends" }; Chip("📸 Daily", kind == "daily") { kind = "daily" } }
        Spacer(Modifier.height(8.dp))
        val list = shots
        if (list == null) Empty("Loading…") else if (list.isEmpty()) Empty("No pictures yet.")
        else LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { a ->
                Column(Modifier.clickable { open = a }) {
                    AlbumImage(model, path(kind, a), Modifier.fillMaxWidth().aspectRatio(1f))
                    Small("${prettyDate(a.date)} · ${a.caption}")
                }
            }
        }
    }
    open?.let { a ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = null }) {
            Column(Modifier.background(Pal.card, RoundedCornerShape(16.dp)).padding(8.dp).clickable { open = null }) {
                AlbumImage(model, path(kind, a), Modifier.fillMaxWidth().aspectRatio(1f), fit = true)
                Small("${prettyDate(a.date)} · ${a.caption}", Pal.ink)
            }
        }
    }
}

private fun path(kind: String, a: AlbumShot) = if (kind == "daily") "/phone/album/${a.name ?: a.date}.jpg" else "/phone/album-friends/${a.name ?: a.date}.jpg"

private val imageCache = android.util.LruCache<String, ImageBitmap>(40)

@Composable
fun AlbumImage(model: PuffModel, path: String, modifier: Modifier, fit: Boolean = false) {
    var img by remember(path) { mutableStateOf(imageCache.get(path)) }
    LaunchedEffect(path) { if (img == null) model.hub.image(path)?.asImageBitmap()?.let { imageCache.put(path, it); img = it } }
    Box(modifier.background(Pal.card, RoundedCornerShape(10.dp))) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop) }
    }
}

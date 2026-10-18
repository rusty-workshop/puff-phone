package com.rusty.puff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Outfit names to emoji (the same list the Echo's Wardrobe tab uses). */
val OUTFIT_EMOJI = mapOf("PARTY_HAT" to "🎉", "SANTA_HAT" to "🎅", "WITCH_HAT" to "🧙", "SUNGLASSES" to "😎", "SCARF" to "🧣",
    "HEADPHONES" to "🎧", "CROWN" to "👑", "BOW" to "🎀", "FLOWER" to "🌸", "POPCORN" to "🍿", "RAINCOAT" to "🧥",
    "EARMUFFS" to "❄️", "HEART_GLASSES" to "💘", "CLOVER_HAT" to "☘️", "BUNNY_EARS" to "🐰", "FLOWER_CROWN" to "💐",
    "SUNHAT" to "👒", "STAR_GLASSES" to "🎆", "SNORKEL" to "🤿", "GRAD_CAP" to "🎓", "SCARECROW_HAT" to "🌾",
    "LEAF_CROWN" to "🍂", "ANTLERS" to "🦌")

fun outfitLabel(name: String) = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LetterSheet(model: PuffModel, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Pal.night) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("✉️ Write to ${model.blobName}", color = Pal.ink, fontSize = 20.sp)
            Small("It goes in the post: the mail truck brings it tomorrow morning, and ${model.blobName} writes back.")
            OutlinedTextField(text, { text = it.take(1000) }, label = { Text("Dear ${model.blobName},") },
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp))
            Chip("📮 Post it", text.isNotBlank()) {
                if (text.isNotBlank()) {
                    model.act("letter", buildJsonObject { put("action", "letter"); put("text", text) }, "Posted! It arrives tomorrow 📬")
                    onClose()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClosetSheet(model: PuffModel, w: World, onClose: () -> Unit) {
    val today = java.time.LocalDate.now().toString()
    val wearing = w.outfit.takeIf { it.startsWith(today) }?.substringAfter('|') ?: "NONE"
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Pal.night) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("👗 ${model.blobName}'s closet", color = Pal.ink, fontSize = 20.sp)
            Small("Pick today's outfit. A new costume arrives every month.")
            Spacer(Modifier.height(8.dp))
            FlowChips((w.wardrobe + "NONE").map { it to (if (it == "NONE") "🫥 Nothing" else "${OUTFIT_EMOJI[it] ?: "✨"} ${outfitLabel(it)}") }, wearing) { n ->
                model.act("outfit", buildJsonObject { put("action", "outfit"); put("name", n) }, if (n == "NONE") "Outfit off" else "Wearing the ${outfitLabel(n).lowercase()}! ✨")
                onClose()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Trivia night on the Echo: pick a theme here, answer by tapping on the Echo. */
@Composable
fun TriviaSection(model: PuffModel, stats: TriviaStats?) {
    var theme by remember { mutableStateOf("") }
    Section("🧠 Trivia night")
    Small("Pixel writes 5 questions and you answer on the Echo. ${model.blobName} cheers or groans at each one.")
    Spacer(Modifier.height(6.dp))
    ChipRow {
        listOf("Anything", "Animals", "Space", "Movies", "Music", "Geography", "Science", "History", "Food").forEach { th ->
            Chip(th) { model.act("trivia", buildJsonObject { put("action", "trivia"); put("theme", th) }, "Trivia night is on! Over to the Echo 🧠") }
        }
    }
    OutlinedTextField(theme, { theme = it.take(40) }, label = { Text("Or your own theme") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    Spacer(Modifier.height(6.dp))
    Chip("Start", theme.isNotBlank()) {
        if (theme.isNotBlank()) { model.act("trivia", buildJsonObject { put("action", "trivia"); put("theme", theme) }, "Trivia night is on! Over to the Echo 🧠"); theme = "" }
    }
    stats?.takeIf { it.played > 0 || it.bestStreak > 0 }?.let { s ->
        Spacer(Modifier.height(8.dp))
        Card {
            Text("🔥 Streak: ${s.streak} right in a row", color = Pal.ink, fontSize = 15.sp)
            Small("Best streak ${s.bestStreak} · best score ${s.bestScore}/5 · ${s.played} games")
            s.rounds.takeLast(5).reversed().forEach { r -> Small("${prettyDate(r.date)} · ${r.theme} · ${r.score}/5") }
        }
    }
}

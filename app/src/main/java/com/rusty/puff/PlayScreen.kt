package com.rusty.puff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Puff's side of the games: same rules as on the Echo (it plays O, you go first as X). */
object Games {
    private val lines = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))

    fun winner(b: String): Char? = lines.firstOrNull { (a, c, d) -> b[a] != ' ' && b[a] == b[c] && b[a] == b[d] }?.let { b[it[0]] }

    fun full(b: String) = ' ' !in b

    /** Win if it can, block you if it must, else the middle, a corner, anything. */
    fun puffMove(b: String, rnd: kotlin.random.Random = kotlin.random.Random): Int? {
        val free = b.indices.filter { b[it] == ' ' }
        if (free.isEmpty()) return null
        fun wins(i: Int, who: Char) = winner(b.substring(0, i) + who + b.substring(i + 1)) == who
        free.firstOrNull { wins(it, 'O') }?.let { return it }
        free.firstOrNull { wins(it, 'X') }?.let { return it }
        // A little forgiving now and then, so it's beatable.
        if (rnd.nextInt(5) == 0) return free.random(rnd)
        if (4 in free) return 4
        return listOf(0, 2, 6, 8).filter { it in free }.randomOrNull(rnd) ?: free.random(rnd)
    }

    /** Rock-paper-scissors: 1 you won, -1 Puff won, 0 a draw (0 rock, 1 paper, 2 scissors). */
    fun rps(you: Int, puff: Int) = when { you == puff -> 0; (you + 1) % 3 == puff -> -1; else -> 1 }
}

@Composable
fun PlayScreen(model: PuffModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ctx.getSharedPreferences("puff", android.content.Context.MODE_PRIVATE) }
    var score by remember { mutableStateOf(Triple(store.getInt("you", 0), store.getInt("puff", 0), store.getInt("draw", 0))) }
    var game by rememberSaveable { mutableStateOf("ttt") }
    var result by remember { mutableStateOf("") }
    val name = model.blobName
    fun finish(outcome: Int, what: String) {
        score = when (outcome) { 1 -> score.copy(first = score.first + 1); -1 -> score.copy(second = score.second + 1); else -> score.copy(third = score.third + 1) }
        store.edit().putInt("you", score.first).putInt("puff", score.second).putInt("draw", score.third).apply()
        result = when (outcome) { 1 -> "You win! 🏆"; -1 -> "$name wins! 🎉"; else -> "A draw 🤝" }
        // Puff reacts on the Echo too.
        model.act("game", buildJsonObject { put("action", "game"); put("game", what); put("outcome", outcome) }, result)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Play with $name", color = Pal.amber, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Small("You ${score.first} · $name ${score.second} · draws ${score.third}  (on this phone)")
        Spacer(Modifier.height(10.dp))
        ChipRow {
            Chip("⭕ Tic-tac-toe", game == "ttt") { game = "ttt"; result = "" }
            Chip("✊ Rock-paper-scissors", game == "rps") { game = "rps"; result = "" }
        }
        Spacer(Modifier.height(16.dp))
        if (game == "ttt") TicTacToe(name, result, onDone = { finish(it, "tic-tac-toe") }, onReset = { result = "" })
        else Rps(name, result) { finish(it, "rock-paper-scissors") }
        val world by model.world.collectAsStateWithLifecycle()
        TriviaSection(model, world?.journal?.friends?.trivia)
        Section("Or have a friend over to play")
        ChipRow {
            listOf("tictactoe" to "⭕ Tic-tac-toe", "boardgame" to "🎲 Snakes and ladders", "catch" to "⚾ Catch", "hideseek" to "🙈 Hide and seek").forEach { (a, l) ->
                Chip(l) { model.act("invite", buildJsonObject { put("action", "invite"); put("activity", a) }, "A friend's coming over for ${l.drop(2)}!") }
            }
        }
    }
}

@Composable
private fun TicTacToe(name: String, result: String, onDone: (Int) -> Unit, onReset: () -> Unit) {
    var board by remember { mutableStateOf("         ") }
    var thinking by remember { mutableStateOf(false) }
    LaunchedEffect(thinking) {
        if (!thinking) return@LaunchedEffect
        delay(600)
        Games.puffMove(board)?.let { board = board.substring(0, it) + 'O' + board.substring(it + 1) }
        thinking = false
        when {
            Games.winner(board) == 'O' -> onDone(-1)
            Games.full(board) -> onDone(0)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 24.dp)) {
        for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            for (c in 0..2) {
                val i = r * 3 + c
                Box(Modifier.size(84.dp).background(Pal.card, RoundedCornerShape(12.dp)).clickable(enabled = board[i] == ' ' && !thinking && result.isEmpty()) {
                    board = board.substring(0, i) + 'X' + board.substring(i + 1)
                    when {
                        Games.winner(board) == 'X' -> onDone(1)
                        Games.full(board) -> onDone(0)
                        else -> thinking = true
                    }
                }, contentAlignment = Alignment.Center) {
                    Text(when (board[i]) { 'X' -> "✖️"; 'O' -> "⭕"; else -> "" }, fontSize = 34.sp)
                }
            }
        }
        Text(if (result.isNotEmpty()) result else if (thinking) "$name is thinking… 🤔" else "Your turn (you're ✖️)", color = Pal.ink, fontSize = 16.sp)
        if (result.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Chip("Play again") { board = "         "; onReset() } }
    }
}

@Composable
private fun Rps(name: String, result: String, onDone: (Int) -> Unit) {
    val hands = listOf("✊" to "Rock", "✋" to "Paper", "✌️" to "Scissors")
    var last by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            hands.forEachIndexed { i, (e, l) ->
                Column(Modifier.background(Pal.card, RoundedCornerShape(14.dp)).clickable {
                    val puff = kotlin.random.Random.nextInt(3)
                    last = i to puff
                    onDone(Games.rps(i, puff))
                }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(e, fontSize = 36.sp); Small(l) }
            }
        }
        last?.let { (you, puff) ->
            Spacer(Modifier.height(12.dp))
            Text("You: ${hands[you].first}   $name: ${hands[puff].first}", color = Pal.ink, fontSize = 20.sp)
            Text(result, color = Pal.amber, fontSize = 16.sp)
        }
    }
}

package com.rusty.puff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

val PuffViolet = Color(0xFF8B6CFF)
val Teal = Color(0xFF45D3C7)
val Ink = Color(0xFF231C30)

fun parseHex(hex: String?, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex!!))
}.getOrDefault(fallback)

/**
 * A blob like the one on the Echo (lighter: glow, body, a face for the mood), centred at [c]
 * with radius [r]. [t] is seconds, for breathing and blinks.
 */
fun DrawScope.drawBlob(c: Offset, r: Float, tint: Color, mood: String, t: Float, sniffles: Boolean = false, still: Boolean = false) {
    val breathe = if (still) 1f else 1f + 0.025f * sin(t * 1.6f)
    val rr = r * breathe
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), c, rr * 1.15f), rr * 1.15f, c)
    drawCircle(Brush.radialGradient(listOf(Teal.copy(alpha = 0.45f), Color.Transparent), c + Offset(rr * 0.12f, rr * 0.1f), rr * 0.75f), rr * 0.7f, c + Offset(rr * 0.12f, rr * 0.1f))
    val core = c + Offset(0f, rr * 0.05f)
    drawCircle(Brush.radialGradient(listOf(tint, tint.copy(alpha = 0.25f)), core, rr * 0.62f), rr * 0.55f, core)
    drawFace(core, rr, mood, t)
    if (sniffles) {
        drawCircle(Color(0xFFFF6B7A), rr * 0.045f, core + Offset(0f, rr * 0.07f))
        for (s in listOf(-1f, 1f)) drawOval(Color(0xFFFF7A9A).copy(alpha = 0.35f), core + Offset(s * rr * 0.22f - rr * 0.07f, rr * 0.06f), Size(rr * 0.14f, rr * 0.07f))
    }
}

private fun DrawScope.drawFace(c: Offset, r: Float, mood: String, t: Float) {
    val ink = Ink.copy(alpha = 0.88f)
    val eyeDx = r * 0.17f
    val eyeY = c.y - r * 0.08f
    val w = r * 0.07f
    val h = r * 0.13f
    val left = Offset(c.x - eyeDx, eyeY)
    val right = Offset(c.x + eyeDx, eyeY)
    // A blink every few seconds.
    val ph = (t % 4.3f)
    val lid = if (ph < 0.18f) sin(ph / 0.18f * PI.toFloat()) else 0f
    fun oval(at: Offset) = drawOval(ink, Offset(at.x - w / 2, at.y - h / 2 * (1 - lid * 0.9f)), Size(w, h * (1 - lid * 0.9f)))
    fun closed(at: Offset) = drawArc(ink, 200f, 140f, false, Offset(at.x - w, at.y - h / 4), Size(w * 2, h / 1.5f), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
    fun happy(at: Offset) = drawArc(ink, 200f, 140f, false, Offset(at.x - w * 0.9f, at.y - h / 3), Size(w * 1.8f, h / 1.2f), style = Stroke(r * 0.035f, cap = StrokeCap.Round))
    val mouth = Offset(c.x, c.y + r * 0.16f)
    fun smile(width: Float, depth: Float) = drawArc(ink, 20f, 140f, false, Offset(mouth.x - width / 2, mouth.y - depth), Size(width, depth * 2), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
    when (mood) {
        "happy", "excited" -> { happy(left); happy(right); smile(r * 0.22f, r * 0.07f) }
        "sleepy" -> {
            closed(left); closed(right)
            // Little z's drifting up.
            for (i in 0 until 3) {
                val p = ((t / 4f) + i / 3f) % 1f
                val z = Offset(c.x + r * 0.35f + p * r * 0.25f, c.y - r * 0.3f - p * r * 0.45f)
                val s = r * (0.04f + 0.04f * p)
                drawPath(Path().apply { moveTo(z.x - s, z.y - s); lineTo(z.x + s, z.y - s); lineTo(z.x - s, z.y + s); lineTo(z.x + s, z.y + s) },
                    Color.White.copy(alpha = (1f - p) * 0.7f), style = Stroke(r * 0.02f))
            }
        }
        "drowsy" -> { closed(left); closed(right); smile(r * 0.12f, r * 0.03f) }
        "sad", "worried" -> { oval(left); oval(right); drawArc(ink, 200f, 140f, false, Offset(mouth.x - r * 0.08f, mouth.y), Size(r * 0.16f, r * 0.08f), style = Stroke(r * 0.03f, cap = StrokeCap.Round)) }
        "surprised" -> { oval(left); oval(right); drawOval(ink, Offset(mouth.x - r * 0.04f, mouth.y - r * 0.03f), Size(r * 0.08f, r * 0.1f)) }
        "curious" -> { oval(left); oval(right); drawCircle(ink, r * 0.025f, mouth) }
        "wink" -> { happy(left); oval(right); smile(r * 0.2f, r * 0.06f) }
        else -> { oval(left); oval(right); smile(r * 0.16f, r * 0.04f) }
    }
}

/** What the Echo's held props are, as emoji for the phone. */
fun heldEmoji(item: String?): String? = when (item) {
    null, "none" -> null
    "watering_can" -> "🪴"; "package" -> "📦"; "umbrella" -> "☂️"; "letter" -> "✉️"; "gift" -> "🎁"; "coffee" -> "☕"
    "heart" -> "💛"; "star" -> "⭐"; "balloon" -> "🎈"; "cake" -> "🎂"; "sunglasses" -> "🕶️"; "suitcase" -> "🧳"
    "camera" -> "📷"; "bag" -> "👜"; "calendar" -> "📅"; "note" -> "📝"; "server" -> "🖥️"; "sun" -> "☀️"; "hourglass" -> "⏳"
    else -> "✨"
}

fun accessoryEmoji(a: String?): String? = when (a) {
    null, "none" -> null
    "party_hat" -> "🥳"; "crown" -> "👑"; "bow" -> "🎀"; "flower" -> "🌸"; "sunglasses" -> "😎"; "scarf" -> "🧣"
    "witch_hat" -> "🧙"; "popcorn" -> "🍿"; "headphones" -> "🎧"; "santa_hat" -> "🎅"; "beanie" -> "🧢"
    else -> null
}

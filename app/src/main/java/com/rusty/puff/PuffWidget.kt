package com.rusty.puff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** A little Puff on the home screen: its mood and one line about what it's doing. Tap to open the app. */
class PuffWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = context.getSharedPreferences("puff_widget", Context.MODE_PRIVATE)
        val name = p.getString("name", "Puff") ?: "Puff"
        val doing = p.getString("doing", "Open the app to say hi") ?: ""
        val bmp = PuffBitmap.draw(240, p.getString("color", null), p.getString("mood", "neutral") ?: "neutral", p.getBoolean("away", false))
        provideContent {
            Row(GlanceModifier.fillMaxSize().background(ColorProvider(Pal.card)).cornerRadius(20.dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(bmp), contentDescription = name, modifier = GlanceModifier.size(72.dp))
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    Text(name, style = TextStyle(color = ColorProvider(Pal.amber), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text(doing, style = TextStyle(color = ColorProvider(Pal.ink), fontSize = 13.sp), maxLines = 3)
                }
            }
        }
    }

    companion object {
        /** Fetch Puff's state now and redraw every widget (from the sync job, or when the app closes). */
        suspend fun refresh(context: Context) {
            val hub = Hub(context)
            if (!hub.paired) return
            val b = runCatching { hub.state() }.getOrNull()?.state?.blob
            val p = context.getSharedPreferences("puff_widget", Context.MODE_PRIVATE).edit()
            if (b != null) {
                p.putString("mood", if (b.asleep) "sleepy" else b.mood).putString("color", b.color).putBoolean("away", b.awayAt != null)
                    .putString("doing", b.doing.ifBlank { "Hanging out at home 🏠" })
            }
            runCatching { hub.settings() }.getOrNull()?.blobName?.takeIf { it.isNotBlank() }?.let { p.putString("name", it) }
            p.apply()
            runCatching { PuffWidget().updateAll(context) }
        }
    }
}

class PuffWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PuffWidget()
}

/** Puff drawn with plain Android graphics (widgets can't run Compose drawing). */
object PuffBitmap {
    fun draw(px: Int, colorHex: String?, mood: String, away: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = px / 2f
        val cy = px / 2f
        val r = px * 0.42f
        val tint = runCatching { android.graphics.Color.parseColor(colorHex!!) }.getOrDefault(0xFF8B6CFF.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
        paint.shader = RadialGradient(cx, cy, r * 1.1f, alpha(tint, 0.4f), 0, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.1f, paint)
        paint.shader = RadialGradient(cx + r * 0.1f, cy + r * 0.1f, r * 0.7f, alpha(0xFF45D3C7.toInt(), 0.45f), 0, Shader.TileMode.CLAMP)
        c.drawCircle(cx + r * 0.1f, cy + r * 0.1f, r * 0.7f, paint)
        paint.shader = RadialGradient(cx, cy, r * 0.6f, tint, alpha(tint, 0.3f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.56f, paint)
        paint.shader = null
        if (away) {
            paint.textSize = r * 0.5f
            paint.textAlign = Paint.Align.CENTER
            c.drawText("🧳", cx, cy + r * 0.18f, paint)
            return bmp
        }
        paint.color = 0xE0231C30.toInt()
        val ex = r * 0.17f
        val ey = cy - r * 0.08f
        val closed = mood == "sleepy" || mood == "drowsy"
        val happy = mood == "happy"
        paint.strokeCap = Paint.Cap.ROUND
        for (s in listOf(-1, 1)) {
            val x = cx + s * ex
            if (closed || happy) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.035f
                c.drawArc(RectF(x - r * 0.07f, ey - r * 0.05f, x + r * 0.07f, ey + r * 0.06f), if (happy) 200f else 20f, 140f, false, paint)
            } else {
                paint.style = Paint.Style.FILL
                c.drawOval(RectF(x - r * 0.035f, ey - r * 0.065f, x + r * 0.035f, ey + r * 0.065f), paint)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.03f
        val my = cy + r * 0.16f
        if (mood == "sad" || mood == "worried") c.drawArc(RectF(cx - r * 0.08f, my, cx + r * 0.08f, my + r * 0.08f), 200f, 140f, false, paint)
        else c.drawArc(RectF(cx - r * 0.09f, my - r * 0.05f, cx + r * 0.09f, my + r * 0.05f), 20f, 140f, false, paint)
        return bmp
    }
}

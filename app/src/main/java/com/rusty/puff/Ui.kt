package com.rusty.puff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Chip(label: String, on: Boolean = false, color: Color = Pal.violet, onClick: () -> Unit) {
    Text(label, color = if (on) Pal.night else Pal.ink, fontSize = 14.sp,
        modifier = Modifier.background(if (on) color else Pal.card, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun Section(title: String) {
    Text(title, color = Pal.amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp).background(Pal.card, RoundedCornerShape(14.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(12.dp), content = content)
}

@Composable
fun Small(text: String, color: Color = Pal.muted) = Text(text, color = color, fontSize = 12.sp)

@Composable
fun Body(text: String, italic: Boolean = false) = Text(text, color = Pal.ink, fontSize = 15.sp, lineHeight = 21.sp,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)

@Composable
fun Empty(text: String) = Text(text, color = Pal.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))

/** A full text in a dialog (a letter, a recipe...). */
@Composable
fun Reader(title: String, text: String, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, containerColor = Pal.card,
        title = { Text(title, color = Pal.ink) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Body(text) } },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

/** "2026-10-12" as "Oct 12". */
fun prettyDate(d: String): String = runCatching {
    java.time.LocalDate.parse(d.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}.getOrDefault(d)

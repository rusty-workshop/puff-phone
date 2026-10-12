package com.rusty.puff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay

/** First run: scan the settings page's QR code (or paste its text) to pair with echo-hub. */
@Composable
fun PairScreen(model: PuffModel) {
    val ctx = LocalContext.current
    var pasted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val s = System.currentTimeMillis(); while (true) { t = (System.currentTimeMillis() - s) / 1000f; delay(33) } }
    fun use(text: String) {
        val p = PairingCode.parse(text)
        if (p == null) error = "That isn't a pairing code from the settings page." else model.pair(p)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Canvas(Modifier.size(180.dp)) { drawBlob(Offset(size.width / 2, size.height / 2), size.minDimension * 0.4f, PuffViolet, "happy", t) }
        Text("Hi! Let's find Puff.", color = Pal.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("On your laptop, open the Echo's settings page and find the Phone app section. Then scan its code.",
            color = Pal.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Chip("📷 Scan to pair", true) {
            error = ""
            val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(ctx, opts).startScan()
                .addOnSuccessListener { code -> use(code.rawValue.orEmpty()) }
                .addOnFailureListener { error = "The scanner didn't start (it needs Google Play services). Paste the code instead." }
        }
        Spacer(Modifier.height(24.dp))
        Small("Or paste the code (under \"Can't scan?\" on the page):")
        OutlinedTextField(pasted, { pasted = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(8.dp))
        Chip("Pair", pasted.isNotBlank()) { use(pasted) }
        if (error.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(error, color = Pal.coral, fontSize = 14.sp, textAlign = TextAlign.Center) }
    }
}

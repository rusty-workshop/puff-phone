package com.rusty.puff

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object Pal {
    val night = Color(0xFF0E0B14)
    val card = Color(0xFF231C30)
    val ink = Color(0xFFF3EEF8)
    val muted = Color(0xFF9C93AB)
    val violet = PuffViolet
    val teal = Teal
    val coral = Color(0xFFFF7A8A)
    val amber = Color(0xFFFFC266)
}

private val Scheme = darkColorScheme(
    primary = Pal.violet, secondary = Pal.teal, tertiary = Pal.amber, background = Pal.night, surface = Pal.night,
    surfaceVariant = Pal.card, onBackground = Pal.ink, onSurface = Pal.ink, onSurfaceVariant = Pal.muted, error = Pal.coral,
)

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = PuffModel.get(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Notify.channels(this)
        Sync.schedule(this)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                val paired by model.paired.collectAsStateWithLifecycle()
                if (!paired) PairScreen(model) else PuffApp(model)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PuffModel.get(this).start()
    }

    override fun onStop() {
        super.onStop()
        val model = PuffModel.get(this)
        model.stop()
        // The widget shows what Puff was up to when the app was last open, until the next sync.
        lifecycleScope.launch { PuffWidget.refresh(this@MainActivity) }
    }
}

@Composable
private fun PuffApp(model: PuffModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val toast by model.toast.collectAsStateWithLifecycle()
    val tabs = listOf("🫧" to "Puff", "📖" to "World", "🏘️" to "Street", "🎮" to "Play", "⚙️" to "Echo")
    Scaffold(
        containerColor = Pal.night,
        bottomBar = {
            NavigationBar(containerColor = Pal.card) {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(if (i == 0) model.blobName else label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Pal.violet.copy(alpha = 0.35f), selectedTextColor = Pal.ink, unselectedTextColor = Pal.muted))
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeScreen(model)
                1 -> WorldScreen(model)
                2 -> StreetScreen(model)
                3 -> PlayScreen(model)
                else -> EchoScreen(model)
            }
            toast?.let {
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Pal.card, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(it, color = Pal.ink, fontSize = 14.sp)
                }
            }
        }
    }
}

package com.rusty.puff

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * The app's view of Puff: live state every few seconds while the app is open, Puff's world
 * every minute (kept on disk so the app opens straight away, even with no connection).
 */
class PuffModel(private val context: Context) {
    val hub = Hub(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<StateReply?>(null)
    val state: StateFlow<StateReply?> = _state.asStateFlow()
    private val _world = MutableStateFlow<WorldReply?>(loadCachedWorld())
    val world: StateFlow<WorldReply?> = _world.asStateFlow()
    private val _settings = MutableStateFlow<EchoSettings?>(null)
    val settings: StateFlow<EchoSettings?> = _settings.asStateFlow()
    private val _reachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()
    private val _paired = MutableStateFlow(hub.paired)
    val paired: StateFlow<Boolean> = _paired.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var loops: Job? = null

    val blobName get() = _settings.value?.blobName?.ifBlank { null } ?: _world.value?.blobName ?: "Puff"

    fun start() {
        if (loops?.isActive == true || !hub.paired) return
        loops = scope.launch {
            launch { while (isActive) { refreshState(); delay(4_000) } }
            launch { while (isActive) { refreshWorld(); delay(60_000) } }
            launch { while (isActive) { refreshSettings(); delay(30_000) } }
        }
    }

    fun stop() {
        loops?.cancel()
        loops = null
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Hub.Unpaired) {
            _paired.value = false
            stop()
        }
    }

    suspend fun refreshState() = guarded {
        val s = hub.state()
        _reachable.value = s != null
        if (s != null) _state.value = s
    }

    suspend fun refreshWorld() = guarded {
        val w = hub.world(_world.value?.at ?: 0.0) ?: return
        if (w.unchanged || w.journal == null) return
        _world.value = w
        withContext(Dispatchers.IO) { runCatching { cacheFile().writeText(HubJson.encodeToString(WorldReply.serializer(), w)) } }
    }

    suspend fun refreshSettings() = guarded {
        hub.settings()?.let { _settings.value = it }
    }

    fun pair(p: Pairing) {
        hub.pair(p)
        _paired.value = true
        start()
        scope.launch { refreshWorld(); refreshSettings() }
        Sync.schedule(context)
    }

    fun unpair() {
        stop()
        hub.unpair()
        _paired.value = false
    }

    /** Send an action to the Echo and say how it went. */
    fun act(what: String, body: JsonObject, done: String) {
        scope.launch {
            val (ok, msg) = try { hub.act(body) } catch (e: Hub.Unpaired) { _paired.value = false; false to "the pairing was reset" }
            say(if (ok) done else "Couldn't: ${msg.take(80)}")
            if (ok) { delay(1_500); refreshState(); if (what == "settings") { delay(5_000); refreshSettings() } }
        }
    }

    fun say(text: String) {
        _toast.value = text
        scope.launch { delay(3_500); if (_toast.value == text) _toast.value = null }
    }

    private fun cacheFile() = File(context.filesDir, "world.json")

    private fun loadCachedWorld(): WorldReply? = runCatching {
        cacheFile().takeIf { it.isFile }?.readText()?.let { HubJson.decodeFromString(WorldReply.serializer(), it) }?.copy(at = 0.0)
    }.getOrNull()

    companion object {
        @Volatile private var instance: PuffModel? = null
        fun get(context: Context): PuffModel = instance ?: synchronized(this) {
            instance ?: PuffModel(context.applicationContext).also { instance = it }
        }
    }
}

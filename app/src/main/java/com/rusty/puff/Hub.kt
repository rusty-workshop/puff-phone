package com.rusty.puff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * echo-hub on the Latitude: straight over the home Wi-Fi when that works, otherwise over the
 * tailnet (Tailscale on the phone). The pairing (both addresses and the token) comes from the
 * settings page's QR code.
 */
class Hub(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("puff", Context.MODE_PRIVATE)
    private val quick = OkHttpClient.Builder().connectTimeout(1500, TimeUnit.MILLISECONDS).callTimeout(3, TimeUnit.SECONDS).build()
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()

    val paired get() = prefs.getString("token", null) != null
    private val token get() = prefs.getString("token", "").orEmpty()
    private val lan get() = prefs.getString("lan", "").orEmpty()
    private val tailnet get() = prefs.getString("tailnet", "").orEmpty()

    fun pair(p: Pairing) {
        prefs.edit().putString("token", p.token).putString("lan", p.lan.trimEnd('/')).putString("tailnet", p.tailnet.trimEnd('/'))
            .remove("base").remove("base_at").apply()
    }

    fun unpair() = prefs.edit().remove("token").remove("base").apply()

    /** Which address answered last (LAN or tailnet), rechecked every minute. */
    private suspend fun base(): String = withContext(Dispatchers.IO) {
        val cached = prefs.getString("base", null)
        if (cached != null && System.currentTimeMillis() - prefs.getLong("base_at", 0) < 60_000) return@withContext cached
        val pick = listOf(lan, tailnet).filter { it.isNotEmpty() }.firstOrNull { b ->
            runCatching {
                quick.newCall(authed("$b/phone/ping").build()).execute().use { it.code == 200 || it.code == 401 }
            }.getOrDefault(false)
        } ?: tailnet
        prefs.edit().putString("base", pick).putLong("base_at", System.currentTimeMillis()).apply()
        pick
    }

    /** "Home Wi-Fi" or "Tailscale", for the Echo tab. */
    val via get() = if (prefs.getString("base", "") == lan) "home Wi-Fi" else "Tailscale"

    private fun authed(url: String) = Request.Builder().url(url).header("Authorization", "Bearer $token")

    private fun forget() = prefs.edit().remove("base_at").apply()

    private suspend fun getText(path: String): String? = withContext(Dispatchers.IO) {
        val b = base()
        runCatching {
            http.newCall(authed(b + path).build()).execute().use { r ->
                if (r.code == 401) throw Unpaired()
                if (r.isSuccessful) r.body?.string() else null
            }
        }.onFailure { if (it is Unpaired) throw it; forget() }.getOrNull()
    }

    private suspend fun <T> get(path: String, ser: KSerializer<T>): T? =
        getText(path)?.let { runCatching { HubJson.decodeFromString(ser, it) }.getOrNull() }

    private suspend fun post(path: String, body: JsonObject): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val b = base()
        runCatching {
            http.newCall(authed(b + path).post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                if (r.code == 401) throw Unpaired()
                r.isSuccessful to (r.body?.string().orEmpty())
            }
        }.getOrElse { if (it is Unpaired) throw it; forget(); false to "couldn't reach the Latitude" }
    }

    class Unpaired : Exception("not paired")

    suspend fun state() = get("/phone/state", StateReply.serializer())
    suspend fun world(since: Double) = get("/phone/world?since=$since", WorldReply.serializer())
    suspend fun events(after: Int) = get("/phone/events?after=$after", EventsReply.serializer())
    suspend fun settings() = get("/phone/settings", EchoSettings.serializer())
    suspend fun album(kind: String) = get("/phone/album?kind=$kind", ListSerializer(AlbumShot.serializer()))
    suspend fun prefsOnHub(): Map<String, Boolean>? = getText("/phone/prefs")?.let { t ->
        runCatching { (HubJson.parseToJsonElement(t) as JsonObject).mapValues { (it.value as JsonPrimitive).content.toBoolean() } }.getOrNull()
    }

    suspend fun setPref(kind: String, on: Boolean) = post("/phone/prefs", buildJsonObject { put(kind, on) }).first

    /** Ask the Echo to do something ({action: ...}); false with a reason if it didn't go. */
    suspend fun act(body: JsonObject): Pair<Boolean, String> = post("/phone/do", body)

    suspend fun image(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val b = base()
        runCatching {
            http.newCall(authed(b + path).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } else null
            }
        }.getOrNull()
    }

    /** The Echo's screen right now: the picture (null if it isn't showing), and how old it is in seconds. */
    suspend fun screen(): Pair<Bitmap?, Int> = withContext(Dispatchers.IO) {
        val b = base()
        runCatching {
            http.newCall(authed("$b/phone/screen").build()).execute().use { r ->
                val age = r.header("X-Age-S")?.toIntOrNull() ?: 0
                if (r.code == 200) r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } to age else null to -1
            }
        }.getOrDefault(null to -1)
    }

    /** Ask the Echo to send pictures every second for a while (renewed every few seconds while watching). */
    suspend fun watchScreen() = post("/phone/screen/watch", buildJsonObject { }).first

    suspend fun sendPhoto(jpeg: ByteArray, dest: String, caption: String): Boolean = withContext(Dispatchers.IO) {
        val b = base()
        runCatching {
            val url = "$b/phone/photo?dest=$dest&caption=" + java.net.URLEncoder.encode(caption, "UTF-8")
            http.newCall(authed(url).post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** The newest build of this app on the Latitude (no token needed). */
    suspend fun latestApp(): AppRelease? = withContext(Dispatchers.IO) {
        val b = base()
        runCatching {
            http.newCall(Request.Builder().url("$b/phone-app/latest.json").build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string()?.let { HubJson.decodeFromString(AppRelease.serializer(), it) } else null
            }
        }.getOrNull()
    }

    suspend fun appUrl() = base() + "/phone-app/app.apk"

    /** The Echo's full settings page (it's on the same address). */
    suspend fun settingsUrl() = base() + "/settings"
}

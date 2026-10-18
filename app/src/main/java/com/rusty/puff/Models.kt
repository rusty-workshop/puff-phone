package com.rusty.puff

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything here mirrors what the Echo sends echo-hub (only the parts the phone shows). */
val HubJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

// -- Live state (GET /phone/state) ------------------------------------------------------------

@Serializable
data class StateReply(val state: EchoState? = null, @SerialName("age_s") val ageS: Long? = null, val online: Boolean = false)

@Serializable
data class EchoState(val blob: BlobState = BlobState(), val quiet: Boolean = false, val timers: List<TimerInfo> = emptyList())

@Serializable
data class TimerInfo(val label: String = "", val kind: String = "", @SerialName("left_s") val leftS: Long = 0)

@Serializable
data class Visitor(val id: String, val name: String = "", val color: String = "#FF9EC7", val scale: Float = 1f, val says: String? = null)


@Serializable
data class BlobState(
    val mood: String = "neutral",
    val color: String? = null,
    val accessory: String? = null,
    val bubble: String? = null,
    val holding: String? = null,
    val label: String? = null,
    val doing: String = "",
    @SerialName("away_at") val awayAt: String? = null,
    @SerialName("away_overnight") val awayOvernight: Boolean = false,
    val sniffles: Boolean = false,
    val asleep: Boolean = false,
    val visitors: List<Visitor> = emptyList(),
    val friend: String? = null,
    val laptop: LaptopNow? = null,
)

@Serializable data class LaptopNow(val playing: Boolean = false, val track: String = "", val artist: String = "", val battery: Int? = null)
@Serializable data class Shortcut(val key: String, val label: String)
@Serializable data class RemoteInfo(val tv: Boolean = false, val laptop: Boolean = false, val shortcuts: List<Shortcut> = emptyList())

// -- Puff's world (GET /phone/world) ----------------------------------------------------------

@Serializable
data class WorldReply(val at: Double = 0.0, val unchanged: Boolean = false, val journal: Journal? = null, @SerialName("blob_name") val blobName: String = "Puff")

@Serializable
data class Journal(
    val diary: List<DiaryEntry> = emptyList(),
    val things: List<Thing> = emptyList(),
    val friends: World = World(),
    val street: Street = Street(),
    val catalog: Catalog = Catalog(),
    @SerialName("friend_album") val friendAlbum: List<AlbumNote> = emptyList(),
    val remote: RemoteInfo = RemoteInfo(),
    val agenda: AgendaInfo = AgendaInfo(),
    @SerialName("week_recap") val weekRecap: String = "",
    @SerialName("week_recap_ms") val weekRecapMs: Long = 0,
)

/** The Echo's agenda page: today's events (with when to leave, for ones with an address) and the next few days. */
@Serializable
data class AgendaInfo(
    val date: String = "",
    /** yyyy-MM-dd when the list is today's, else blank. */
    val day: String = "",
    @SerialName("updated_ms") val updatedMs: Long = 0,
    val items: List<AgendaEntry> = emptyList(),
    val upcoming: List<AgendaLater> = emptyList(),
)

@Serializable
data class RingReply(val ring: Double? = null, val events: Int = -1, val now: Double = 0.0)

@Serializable
data class AgendaEntry(val time: String = "", val title: String = "", val location: String = "", @SerialName("leave_min") val leaveMin: Int? = null)

@Serializable
data class AgendaLater(val date: String = "", val items: List<AgendaEntry> = emptyList())

@Serializable data class DiaryEntry(val date: String, val text: String)
@Serializable data class Thing(val date: String = "", val title: String = "", val text: String = "", val kind: String = "")
@Serializable data class AlbumNote(val date: String, val caption: String = "")

@Serializable
data class Friend(
    val id: String,
    val name: String = "",
    val bio: String = "",
    val personality: String = "BOUNCY",
    val color: Long = 0xFFFF9EC7,
    val closeness: Int = 0,
    val visits: Int = 0,
    val birthday: String = "",
) {
    val shownName get() = name.ifBlank { "New friend" }
    val scale get() = when (personality) { "SHY" -> 0.9f; "SLEEPY" -> 1.25f; "TINY" -> 0.7f; else -> 1f }
}

@Serializable data class FriendDiaryEntry(val friendId: String, val date: String, val text: String)
@Serializable data class Letter(val friendId: String, val date: String, val text: String, val atMs: Long, val read: Boolean = false, val fromBlob: Boolean = false)
@Serializable data class Book(val title: String, val review: String, val reader: String, val date: String)
@Serializable data class Constellation(val name: String, val shape: String, val by: String, val date: String)
@Serializable data class HolidayCard(val holiday: String, val date: String, val text: String, val from: String)
@Serializable data class PenPal(val name: String, val place: String, val bio: String = "")
@Serializable data class Fortune(val date: String, val text: String)
@Serializable data class TriviaRound(val date: String, val theme: String, val score: Int)
@Serializable data class TriviaStats(val played: Int = 0, val bestScore: Int = 0, val streak: Int = 0, val bestStreak: Int = 0, val rounds: List<TriviaRound> = emptyList())
@Serializable data class Building(val startDate: String, val friendId: String = "")
@Serializable data class Postcard(val friendId: String, val text: String, val date: String)

@Serializable
data class World(
    val friends: List<Friend> = emptyList(),
    val diary: List<FriendDiaryEntry> = emptyList(),
    val postcards: List<Postcard> = emptyList(),
    val letters: List<Letter> = emptyList(),
    val penpal: PenPal? = null,
    val books: List<Book> = emptyList(),
    val stickers: Map<String, String> = emptyMap(),
    val constellations: List<Constellation> = emptyList(),
    val building: Building? = null,
    val cards: List<HolidayCard> = emptyList(),
    val weatherDays: Map<String, String> = emptyMap(),
    val boardWins: Map<String, Int> = emptyMap(),
    val upgrades: Map<String, Int> = emptyMap(),
    val fortunes: List<Fortune> = emptyList(),
    val trivia: TriviaStats = TriviaStats(),
    val wardrobe: List<String> = emptyList(),
    val outfit: String = "",
)

@Serializable
data class Street(
    val where: Map<String, String> = emptyMap(),
    val festival: String? = null,
    val activity: String? = null,
    @SerialName("puff_at") val puffAt: String? = null,
    val snowman: Boolean = false,
    val hour: Int = 12,
)

@Serializable data class StickerInfo(val id: String, val emoji: String, val title: String, val who: String)
@Serializable data class Catalog(val stickers: List<StickerInfo> = emptyList())

// -- Albums, events, settings -----------------------------------------------------------------

@Serializable data class AlbumShot(val date: String, val name: String? = null, val caption: String = "")

@Serializable data class HubEvent(val id: Int, val kind: String, val title: String, val body: String = "")
@Serializable data class EventsReply(val events: List<HubEvent> = emptyList(), val last: Int = 0)

@Serializable
data class EchoSettings(
    val version: String = "",
    @SerialName("blob_name") val blobName: String = "",
    @SerialName("still_until_ms") val stillUntilMs: Long = 0,
    @SerialName("quiet_until_ms") val quietUntilMs: Long = 0,
    @SerialName("guest_until_ms") val guestUntilMs: Long = 0,
    @SerialName("blob_friend") val blobFriend: Boolean = true,
    @SerialName("puff_sniffles") val puffSniffles: Boolean = true,
    @SerialName("puff_away_sleepovers") val puffAwaySleepovers: Boolean = true,
    @SerialName("puff_house_build") val puffHouseBuild: Boolean = true,
)

@Serializable data class AppRelease(val version: String = "", val code: Int = 0)

/** What the settings page's pairing QR code holds. */
@Serializable data class Pairing(val puff: Int = 1, val lan: String, val tailnet: String, val token: String)

object PairingCode {
    /** The QR code / pasted text, or null if it isn't one. */
    fun parse(text: String): Pairing? = runCatching { HubJson.decodeFromString(Pairing.serializer(), text.trim()) }.getOrNull()
        ?.takeIf { it.token.length >= 16 && (it.lan.startsWith("http://") || it.lan.startsWith("https://")) && it.tailnet.startsWith("https://") }
}

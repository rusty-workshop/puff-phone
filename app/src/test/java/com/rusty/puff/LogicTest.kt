package com.rusty.puff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test
    fun pairingCodes() {
        val p = PairingCode.parse("""{"puff":1,"lan":"http://192.168.1.158:8094","tailnet":"https://latitude.example.ts.net:9094","token":"0123456789abcdef0123"}""")
        assertNotNull(p)
        assertEquals("0123456789abcdef0123", p!!.token)
        assertNull(PairingCode.parse("hello"))
        assertNull(PairingCode.parse("""{"lan":"ftp://x","tailnet":"https://x","token":"0123456789abcdef0123"}"""))
        assertNull(PairingCode.parse("""{"lan":"http://x","tailnet":"https://x","token":"short"}"""))
    }

    @Test
    fun puffPlaysTicTacToeSensibly() {
        // Takes the win.
        assertEquals(2, Games.puffMove("OO XX    ", kotlin.random.Random(1)))
        // Blocks yours.
        assertEquals(2, Games.puffMove("XX  O    ", kotlin.random.Random(1)))
        assertEquals('X', Games.winner("XXX      "))
        assertEquals('O', Games.winner("O   O   O"))
        assertNull(Games.winner("XOXXOOOXX"))
        assertTrue(Games.full("XOXXOOOXX"))
        assertNull(Games.puffMove("XOXXOOOXX"))
    }

    @Test
    fun rockPaperScissors() {
        assertEquals(0, Games.rps(1, 1))
        assertEquals(-1, Games.rps(0, 1)) // rock loses to paper
        assertEquals(1, Games.rps(2, 1)) // scissors beat paper
        assertEquals(-1, Games.rps(2, 0)) // scissors lose to rock
    }

    @Test
    fun notificationsFollowTheSwitches() {
        val ev = (1..8).map { HubEvent(it, if (it % 2 == 0) "letters" else "snow", "t$it") } + HubEvent(9, "bogus", "x")
        val picked = Notify.pick(ev, mapOf("snow" to false))
        assertTrue(picked.all { it.kind == "letters" })
        assertEquals(4, picked.size)
        assertEquals(5, Notify.pick(ev, emptyMap()).size)
    }

    @Test
    fun readsWhatTheHubSends() {
        val world = HubJson.decodeFromString(WorldReply.serializer(), """
            {"at": 12.5, "blob_name": "Puff", "journal": {"diary": [{"date": "2026-10-12", "text": "Hi", "atMs": 1}],
             "friends": {"friends": [{"id": "a", "name": "Pip", "color": 4294942407, "personality": "TINY", "extra": 1}],
                         "letters": [{"friendId": "rusty", "date": "2026-10-11", "text": "Dear Rusty", "atMs": 5, "fromBlob": true}],
                         "stickers": {"books_5": "2026-10-12"}, "weatherDays": {"2026-10-12": "☀️|🙂"}, "boardWins": {"Puff": 2}},
             "street": {"where": {"a": "HOME"}, "festival": "FAIR", "hour": 14, "snowman": false},
             "catalog": {"stickers": [{"id": "books_5", "emoji": "📚", "title": "Read 5 books", "who": "puff"}], "collectibles": []}}}
        """.trimIndent())
        val j = world.journal!!
        assertEquals("Pip", j.friends.friends.single().shownName)
        assertEquals(0.7f, j.friends.friends.single().scale)
        assertEquals(0xFFFF9EC7, j.friends.friends.single().color)
        assertEquals("FAIR", j.street.festival)
        assertTrue(j.friends.letters.single().fromBlob)
        val state = HubJson.decodeFromString(StateReply.serializer(), """
            {"state": {"blob": {"mood": "happy", "doing": "Baking with Pip", "visitors": [{"id": "a", "name": "Pip", "color": "#FF9EC7", "scale": 1.0, "says": "Hi"}],
              "vote": [{"id": "a", "name": "Pip"}], "holding": "cake"}, "quiet": false, "timers": []}, "age_s": 3, "online": true}
        """.trimIndent())
        assertEquals("Baking with Pip", state.state!!.blob.doing)
        assertEquals("Hi", state.state!!.blob.visitors.single().says)
        assertEquals("🎂", heldEmoji(state.state!!.blob.holding))
    }
}

package com.rusty.puff

/** Things a friend can come over to do (the Echo's activity names) with a label for the phone. */
val ACTIVITIES = listOf(
    "chat" to "💬 Chat", "tea" to "☕ Tea time", "catch" to "⚾ Catch", "tag" to "🏃 Tag", "hideseek" to "🙈 Hide and seek",
    "tictactoe" to "⭕ Tic-tac-toe", "boardgame" to "🎲 Snakes and ladders", "story" to "📖 A story", "dance" to "💃 Dance party",
    "movie" to "🎬 Movie night", "stargazing" to "🔭 Stargazing", "bookclub" to "📚 Book club", "nap" to "😴 A nap",
    "sleepover" to "🌙 Sleepover", "groupphoto" to "📸 Group photo",
)

/** One-off things to make happen on the Echo (its playground names). */
val HAPPENINGS = listOf(
    "LETTER" to "✉️ A friend writes a letter", "PENPAL" to "🌍 A pen pal letter", "RUSTYLETTER" to "💌 Puff writes to me",
    "TREASURE" to "🗝️ A treasure hunt", "TRIP" to "🧳 Puff visits a friend", "BOOK" to "📖 A new book", "HANGOUT" to "👯 Friends hang out",
)

/** Presents (kind, value, label), like the settings page's. */
val GIFTS = listOf(
    Triple("hat", "PARTY_HAT", "🥳 Party hat"), Triple("hat", "CROWN", "👑 Crown"), Triple("hat", "BOW", "🎀 Bow"),
    Triple("hat", "FLOWER", "🌸 Flower"), Triple("hat", "SUNGLASSES", "😎 Sunglasses"), Triple("hat", "SCARF", "🧣 Scarf"),
    Triple("snack", "cake", "🎂 Cake"), Triple("snack", "cookie", "🍪 Cookie"), Triple("snack", "coffee", "☕ Coffee"),
    Triple("room", "", "🛋️ Something for its room"),
)

fun activityLabel(a: String?) = ACTIVITIES.firstOrNull { it.first == a }?.second ?: a.orEmpty()

# Puff for phone

The phone side of Muse Echo: Puff (the blob on the Echo Show) on an Android phone. Everything goes
through echo-hub on the Latitude (`/phone/...`, token from the settings page's pairing QR code): over
the home Wi-Fi when it can, otherwise over Tailscale (tailnet only, `https://latitude.<tailnet>.ts.net:9094`).

- **Puff tab**: live Puff (mood, what it's holding, who's over, away on a trip), poke it, invite a friend, send a gift, make something happen, vote in the talent show.
- **World**: diary, letters, comics, the Gazette, albums, friends, dreams, cookbook, bookshelf, stickers, cabinet, weather calendar, constellations.
- **Street**: who's home, the house being built, festivals, the park.
- **Play**: tic-tac-toe and rock-paper-scissors against Puff (Puff reacts on the Echo).
- **Echo**: send a photo to the slideshow or album, announce, timers, hold still, quiet now, guest mode, world switches, notification switches.
- Widget, and notifications (letters, stickers, holiday cards, the first snow) checked every 15 minutes.

Build and publish: `tools/release.sh` (the app offers the update itself; first install from the settings page's link).

AGPL-3.0-or-later.

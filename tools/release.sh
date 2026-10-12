#!/usr/bin/env bash
# Build the release APK and put it on the Latitude, where the app (and the settings page) find it.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q :app:assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
code=$(git rev-list --count HEAD)
printf '{"version": "1.0.%s", "code": %s}\n' "$code" "$code" > /tmp/puff-latest.json
ssh latitude 'mkdir -p ~/.local/share/echo-hub/phone-app'
scp -q "$apk" latitude:.local/share/echo-hub/phone-app/app.apk.part
scp -q /tmp/puff-latest.json latitude:.local/share/echo-hub/phone-app/latest.json.part
# Swap both in at once so the app never sees a version without its APK.
ssh latitude 'cd ~/.local/share/echo-hub/phone-app && mv app.apk.part app.apk && mv latest.json.part latest.json'
echo "Published Puff 1.0.$code ($(du -h "$apk" | cut -f1))"

#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMU="$ANDROID_HOME/emulator/emulator"
AVD="MoodFox"
PKG="com.moodfox/.ui.MainActivity"

# Boot emulator if not already running
if ! "$ADB" devices 2>/dev/null | grep -q "emulator-"; then
  echo "▸ Starting emulator ($AVD)…"
  nohup "$EMU" -avd "$AVD" &>/dev/null &
  echo "▸ Waiting for boot…"
  "$ADB" -s emulator-5554 wait-for-device
  "$ADB" -s emulator-5554 shell 'while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 1; done' 2>/dev/null
  echo "✔ Emulator booted"
else
  echo "✔ Emulator already running"
fi

# Build + install + launch
cd "$(dirname "$0")/../app"
echo "▸ Installing…"
./gradlew installDebug -PandroidDeviceSerial=emulator-5554
echo "▸ Launching…"
"$ADB" -s emulator-5554 shell am start -n "$PKG"
echo "✔ App running on emulator"

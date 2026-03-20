#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

cd "$(dirname "$0")/../app"
echo "▸ Building debug APK…"
./gradlew assembleDebug
echo "✔ APK: mobile/build/outputs/apk/debug/mobile-debug.apk"

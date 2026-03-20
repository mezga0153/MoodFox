#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Wireless ADB – Pair & Connect"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "On your Phone: Settings → Developer options → Wireless debugging"
echo ""

# Step 1: Pair (one-time)
read -rp "Already paired before? [y/N] " paired
if [[ "$paired" != "y" && "$paired" != "Y" ]]; then
  echo ""
  echo "Tap 'Pair device with pairing code' on the phone."
  read -rp "Pairing IP:PORT (e.g. 192.168.1.42:37123): " pair_addr
  "$ADB" pair "$pair_addr"
  echo ""
fi

# Step 2: Connect
echo "Use the IP:PORT shown on the Wireless debugging main screen"
echo "(this is a DIFFERENT port than the pairing one)."
read -rp "Debug IP:PORT (e.g. 192.168.1.42:43567): " debug_addr
"$ADB" connect "$debug_addr"

echo ""
echo "▸ Connected devices:"
"$ADB" devices
echo ""
echo "✔ Done. Now run: ./scripts/run-phone.sh"

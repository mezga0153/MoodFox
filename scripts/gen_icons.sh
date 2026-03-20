#!/bin/bash
set -e

FOX="/Users/tinem/dev/MoodFox/fox.png"
RES="/Users/tinem/dev/MoodFox/app/mobile/src/main/res"

gen() {
  density=$1; sz=$2; fgsz=$3
  dir="$RES/mipmap-$density"
  inner=$((fgsz * 66 / 100))

  # Legacy square icon
  magick "$FOX" -background "#1B1B2E" -gravity center \
    -resize "${sz}x${sz}" -extent "${sz}x${sz}" \
    "$dir/ic_launcher.png"

  # Round icon: circle mask
  magick "$FOX" -background "#1B1B2E" -gravity center \
    -resize "${sz}x${sz}" -extent "${sz}x${sz}" \
    \( +clone -alpha extract \
       -draw "fill black polygon 0,0 0,${sz} ${sz},0 fill white circle $((sz/2)),$((sz/2)) $((sz/2)),0" \
       \( +clone -flip \) -compose Multiply -composite \
       \( +clone -flop \) -compose Multiply -composite \
    \) -alpha off -compose CopyOpacity -composite \
    "$dir/ic_launcher_round.png"

  # Foreground layer (66% safe zone)
  magick "$FOX" -background "#1B1B2E" -gravity center \
    -resize "${inner}x${inner}" -extent "${fgsz}x${fgsz}" \
    -background "#1B1B2E" -alpha remove \
    "$dir/ic_launcher_foreground.png"

  echo "$density done"
}

gen mdpi    48  108
gen hdpi    72  162
gen xhdpi   96  216
gen xxhdpi  144 324
gen xxxhdpi 192 432

echo "All icons generated."

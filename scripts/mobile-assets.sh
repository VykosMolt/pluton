#!/usr/bin/env bash
# Regenerates the native app icons and splash screens from public/icon.svg
# (the adaptive-icon foreground is the cat alone, in assets/icon-foreground.svg).
set -euo pipefail
cd "$(dirname "$0")/.."

BLUE='#3d5afe'
RES=android/app/src/main/res
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# iOS: one 1024px opaque icon (the App Store rejects alpha)
magick -background "$BLUE" public/icon.svg -resize 1024x1024 -flatten -alpha off -depth 8 \
  ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png

# Android legacy + adaptive icons
for pair in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  bucket=${pair%%:*}; size=${pair##*:}
  rsvg-convert -w "$size" -h "$size" public/icon.svg -o "$TMP/icon.png"
  cp "$TMP/icon.png" "$RES/mipmap-$bucket/ic_launcher.png"
  magick -size "${size}x${size}" xc:black -fill white -draw "circle $((size / 2)),$((size / 2)) $((size / 2)),0" "$TMP/mask.png"
  magick "$TMP/icon.png" "$TMP/mask.png" -alpha off -compose CopyOpacity -composite "$RES/mipmap-$bucket/ic_launcher_round.png"
done
for pair in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
  bucket=${pair%%:*}; size=${pair##*:}
  rsvg-convert -w "$size" -h "$size" assets/icon-foreground.svg -o "$RES/mipmap-$bucket/ic_launcher_foreground.png"
done
sed -i "s|#FFFFFF|$BLUE|" "$RES/values/ic_launcher_background.xml"

# Splashes: the cat centred on the brand blue. iOS wants a square; Android's
# drawable buckets are aspect-matched to phones.
rsvg-convert -w 1024 -h 1024 assets/icon-foreground.svg -o "$TMP/cat.png"
splash() { # width height cat-size out
  magick -size "$1x$2" "xc:$BLUE" \( "$TMP/cat.png" -resize "$3x$3" \) -gravity center -composite -depth 8 "$4"
}
for f in ios/App/App/Assets.xcassets/Splash.imageset/splash-2732x2732*.png; do
  splash 2732 2732 780 "$f"
done
splash 480 320 150 "$RES/drawable/splash.png"
for pair in mdpi:320:480 hdpi:480:800 xhdpi:720:1280 xxhdpi:960:1600 xxxhdpi:1280:1920; do
  bucket=${pair%%:*}; rest=${pair#*:}; w=${rest%%:*}; h=${rest##*:}
  splash "$w" "$h" "$((w * 3 / 10))" "$RES/drawable-port-$bucket/splash.png"
  splash "$h" "$w" "$((w * 3 / 10))" "$RES/drawable-land-$bucket/splash.png"
done

echo "native icons and splashes regenerated"

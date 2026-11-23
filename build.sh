#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$HOME/Library/Android/sdk"}}
platform="$sdk/platforms/android-35/android.jar"
tools="$sdk/build-tools/35.0.0"
output=${1:-build/tv-shelf.apk}

mkdir -p build/generated build/classes build/dex "$(dirname "$output")"
"$tools/aapt2" compile --dir src/main/res -o build/resources.zip
"$tools/aapt2" link --manifest src/main/AndroidManifest.xml -I "$platform" \
  --java build/generated -o build/resources.apk build/resources.zip
javac --release 8 -classpath "$platform" -d build/classes \
  src/main/java/ru/local/tvshelf/*.java build/generated/ru/local/tvshelf/R.java
jar cf build/classes.jar -C build/classes .
"$tools/d8" --lib "$platform" --min-api 26 --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
zip -q -j build/unsigned.apk build/dex/classes.dex
"$tools/zipalign" -f 4 build/unsigned.apk build/aligned.apk

key=${TV_SHELF_KEYSTORE:-build/local-signing.p12}
export TV_SHELF_KEYSTORE_PASSWORD=${TV_SHELF_KEYSTORE_PASSWORD:-android}
if [[ ! -f "$key" ]]; then
  keytool -genkeypair -keystore "$key" -storepass:env TV_SHELF_KEYSTORE_PASSWORD \
    -alias local -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=TV Shelf Local'
fi
"$tools/apksigner" sign --ks "$key" --ks-key-alias local \
  --ks-pass env:TV_SHELF_KEYSTORE_PASSWORD \
  --v4-signing-enabled false --out "$output" build/aligned.apk
"$tools/apksigner" verify --print-certs "$output"

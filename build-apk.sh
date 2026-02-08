#!/bin/bash
# Manual APK build script for ImageMultiplier
# Builds without Android Studio or Google Maven - uses apt android-sdk + Maven Central kotlin
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
KOTLIN_STDLIB="/opt/kotlin/lib/kotlin-stdlib-1.9.24.jar"
KOTLIN_CP="/opt/kotlin/lib/kotlin-compiler-embeddable-1.9.24.jar:/opt/kotlin/lib/kotlin-stdlib-1.9.24.jar:/opt/kotlin/lib/kotlin-script-runtime-1.9.24.jar:/opt/kotlin/lib/kotlin-reflect-1.9.24.jar:/opt/kotlin/lib/trove4j-1.0.20200330.jar:/opt/kotlin/lib/annotations-13.0.jar"
DX="/usr/lib/android-sdk/build-tools/debian/dx"
BUILD_DIR="/tmp/apk-build"
SRC="$SCRIPT_DIR/app/src/main/java/com/secondfirst/app/MainActivity.kt"
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
RES_DIR="$SCRIPT_DIR/app/src/main/res"

echo "=== Cleaning build dir ==="
rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/compiled-res" "$BUILD_DIR/stdlib-classes"

echo "=== Compiling Kotlin ==="
java -cp "$KOTLIN_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    "$SRC" -d "$BUILD_DIR/classes" -cp "$ANDROID_JAR:$KOTLIN_STDLIB" -no-stdlib -jvm-target 1.8

echo "=== Extracting kotlin-stdlib ==="
cd "$BUILD_DIR/stdlib-classes" && jar xf "$KOTLIN_STDLIB"
rm -rf "$BUILD_DIR/stdlib-classes/META-INF"

echo "=== Converting to DEX ==="
"$DX" --dex --min-sdk-version=26 --output="$BUILD_DIR/classes.dex" \
    "$BUILD_DIR/classes" "$BUILD_DIR/stdlib-classes"

echo "=== Compiling resources ==="
aapt2 compile --dir "$RES_DIR" -o "$BUILD_DIR/compiled-res/"

echo "=== Linking APK ==="
COMPILED_RES=$(ls "$BUILD_DIR"/compiled-res/*.flat | tr '\n' ' ')
aapt2 link --manifest "$MANIFEST" -I "$ANDROID_JAR" \
    -o "$BUILD_DIR/app-base.apk" \
    --min-sdk-version 28 --target-sdk-version 34 \
    --version-code 1 --version-name "1.0" \
    $COMPILED_RES

echo "=== Adding DEX to APK ==="
cp "$BUILD_DIR/app-base.apk" "$BUILD_DIR/app-unsigned.apk"
cd "$BUILD_DIR" && zip -j app-unsigned.apk classes.dex

echo "=== Aligning ==="
zipalign -f 4 "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/app-aligned.apk"

echo "=== Signing ==="
if [ ! -f "$BUILD_DIR/debug.keystore" ]; then
    keytool -genkeypair -v -keystore "$BUILD_DIR/debug.keystore" \
        -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi
apksigner sign --ks "$BUILD_DIR/debug.keystore" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out "$SCRIPT_DIR/app-debug.apk" "$BUILD_DIR/app-aligned.apk"

echo "=== Done! ==="
ls -lh "$SCRIPT_DIR/app-debug.apk"
echo "Install with: adb install $SCRIPT_DIR/app-debug.apk"

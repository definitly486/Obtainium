#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Checking Flutter..."
if ! command -v flutter >/dev/null 2>&1; then
  echo "ERROR: flutter not found in PATH. Install Flutter first."
  exit 1
fi

echo "==> flutter pub get..."
flutter pub get

# Ensure local.properties has flutter.sdk
if [ ! -f android/local.properties ]; then
  FLUTTER_ROOT="$(dirname "$(dirname "$(which flutter)")")"
  echo "flutter.sdk=$FLUTTER_ROOT" > android/local.properties
  echo "Created android/local.properties with flutter.sdk=$FLUTTER_ROOT"
fi

echo "==> Building with Gradle (armeabi-v7a only, normal flavor)..."
cd android
chmod +x gradlew 2>/dev/null || true
./gradlew assembleNormalRelease --no-daemon

echo ""
echo "==> Done. APK should be at:"
echo "    ../build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk"
echo "    or"
echo "    ../build/app/outputs/apk/normal/release/*.apk"
find ../build -name "*.apk" 2>/dev/null | head -10

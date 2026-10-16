#!/usr/bin/env bash
set -euo pipefail
serial="${1:-emulator-5584}"
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo "This suite only accepts an emulator serial; physical-device writes are not part of this suite." >&2
  exit 1
fi
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
cd "$(dirname "$0")/.."
adb="$ANDROID_HOME/platform-tools/adb"
"$adb" -s "$serial" get-state
python3 scripts/sync-busybox.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result_file="$(mktemp)"
trap 'rm -f "$result_file"' EXIT
"$adb" -s "$serial" shell am instrument -w com.valhalla.bolt.test/androidx.test.runner.AndroidJUnitRunner | tee "$result_file"
# adb exits zero for assertion failures and crashes; require the runner's success result.
if ! rg -q '^OK \([0-9]+ tests?\)' "$result_file"; then
  echo "Instrumentation did not report success." >&2
  exit 1
fi

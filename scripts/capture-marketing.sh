#!/usr/bin/env bash
set -euo pipefail
serial="${1:-emulator-5584}"
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo "Marketing captures only run on an emulator." >&2
  exit 1
fi
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
cd "$(dirname "$0")/.."
adb="$ANDROID_HOME/platform-tools/adb"
"$adb" -s "$serial" get-state
if "$adb" -s "$serial" shell wm size | grep -q Override; then
  echo "Remove the emulator's existing size override before capturing." >&2
  exit 1
fi
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# This authoring run intentionally clears only the disposable emulator's Bolt data.
"$adb" -s "$serial" shell pm clear com.valhalla.bolt
"$adb" -s "$serial" shell wm size 1080x1920
result_file="$(mktemp)"
trap '"$adb" -s "$serial" shell wm size reset; rm -f "$result_file"' EXIT
"$adb" -s "$serial" shell am instrument -w -e class com.valhalla.bolt.ui.MarketingCaptureInstrumentedTest -e boltMarketingCapture true com.valhalla.bolt.test/androidx.test.runner.AndroidJUnitRunner | tee "$result_file"
grep -Eq '^OK \(1 test\)' "$result_file"
mkdir -p docs/marketing/captures
for name in 01-overview 02-install 03-recovery 04-originals 05-backup 06-appearance-light 07-appearance-amoled 08-readable-log; do
  "$adb" -s "$serial" exec-out run-as com.valhalla.bolt cat "files/marketing-captures/$name.png" > "docs/marketing/captures/$name.png"
done
echo "Eight current app screenshots captured. No installer, restore or reboot ran."

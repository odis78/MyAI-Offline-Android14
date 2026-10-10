#!/usr/bin/env bash
set -euo pipefail

ROOT="$GITHUB_WORKSPACE"
PROJECT="$ROOT/MyAI_Infinix_v0.1.0_ALPHA"
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
EVIDENCE="$ROOT/test-bridge/android-evidence"

echo "Checking debug APK: $APK"
if [[ ! -s "$APK" ]]; then
  echo "Expected APK not found; searching all project outputs."
  find "$PROJECT" -type f -name '*.apk' -print || true
  exit 1
fi

adb start-server
adb wait-for-device

booted=0
for attempt in $(seq 1 60); do
  value="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$value" == "1" ]]; then
    booted=1
    break
  fi
  sleep 5
done
if [[ "$booted" -ne 1 ]]; then
  echo "Android emulator did not finish booting."
  adb devices -l || true
  exit 1
fi

echo "Installing APK"
adb install -r "$APK"
adb shell am force-stop com.dmitry.myai.infinix || true
echo "Launching MainActivity"
adb shell am start -W -n com.dmitry.myai.infinix/.MainActivity
sleep 5

echo "Checking process"
if ! adb shell pidof com.dmitry.myai.infinix; then
  echo "MyAI process is not running after launch."
  adb logcat -d -t 5000 | grep -i -E 'FATAL EXCEPTION|AndroidRuntime|com.dmitry.myai.infinix' | tail -n 120 || true
  exit 1
fi

mkdir -p "$EVIDENCE"
adb exec-out screencap -p > "$EVIDENCE/android-emulator.png"
adb logcat -d -t 5000 > "$EVIDENCE/logcat.txt"
adb shell dumpsys activity activities > "$EVIDENCE/activities.txt"
cp "$APK" "$EVIDENCE/app-debug.apk"
echo "PASS: APK installed and MainActivity process remained running."

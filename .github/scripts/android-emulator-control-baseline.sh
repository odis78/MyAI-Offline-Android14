#!/usr/bin/env bash
# Run 11 control experiment: reproduce Settings -> MyAI without dispatching any
# MyAI tools. This is diagnostic-only and never changes app source or navigation.
set -uo pipefail

ROOT="${GITHUB_WORKSPACE:-$(pwd)}"
PROJECT="$ROOT/MyAI_Infinix_v0.1.0_ALPHA"
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
EVIDENCE="$ROOT/test-bridge/android-evidence/run13-control"
PKG="com.dmitry.myai.infinix"
ACTIVITY="$PKG/.MainActivity"
SERVICE="$PKG/com.dmitry.myai.infinix.bridge.MyAiAccessibilityService"
mkdir -p "$EVIDENCE"

adb_bounded() {
  local limit=30 arg
  for arg in "$@"; do
    case "$arg" in
      wait-for-device) limit=180 ;;
      install) limit=90 ;;
      bugreport) limit=180 ;;
    esac
  done
  timeout --foreground "${limit}s" adb "$@"
}

snapshot() {
  local tag="$1"
  echo "=== RUN11 SNAPSHOT: $tag ==="
  date -u +%FT%TZ > "$EVIDENCE/$tag-time-utc.txt"
  adb_bounded shell settings --user 0 get secure accessibility_enabled > "$EVIDENCE/$tag-accessibility-enabled.txt" 2>&1 || true
  adb_bounded shell settings --user 0 get secure enabled_accessibility_services > "$EVIDENCE/$tag-enabled-services-setting.txt" 2>&1 || true
  adb_bounded shell dumpsys accessibility > "$EVIDENCE/$tag-dumpsys-accessibility.txt" 2>&1 || true
  adb_bounded shell dumpsys activity activities > "$EVIDENCE/$tag-activities.txt" 2>&1 || true
  adb_bounded shell dumpsys activity lastanr > "$EVIDENCE/$tag-lastanr.txt" 2>&1 || true
  adb_bounded shell dumpsys activity processes > "$EVIDENCE/$tag-processes.txt" 2>&1 || true
  adb_bounded shell dumpsys window windows > "$EVIDENCE/$tag-windows.txt" 2>&1 || true
  adb_bounded logcat -d -b main -b system -b events -b crash -v threadtime -t 12000 > "$EVIDENCE/$tag-logcat.txt" 2>&1 || true
  grep -i -E -C 3 'ANR in|am_anr|Input dispatching timed out|not responding|AppNotRespondingDialog|Timed out while trying to bind|AccessibilityManagerService|AccessibilityServiceConnection|SERVICE (CONNECTED|UNBOUND|DESTROYED|REBOUND)|am_crash|FATAL EXCEPTION|Process .* died' "$EVIDENCE/$tag-logcat.txt" > "$EVIDENCE/$tag-logcat-summary.txt" 2>/dev/null || true
  adb_bounded exec-out screencap -p > "$EVIDENCE/$tag-screen.png" 2>/dev/null || true
  # Capture enabled vs actually-bound as separate fields; do not infer binding
  # from a package-name grep alone.
  if grep -Fq "$PKG" "$EVIDENCE/$tag-enabled-services-setting.txt"; then
    echo "enabled_setting=YES" > "$EVIDENCE/$tag-bridge-verdict.txt"
  else
    echo "enabled_setting=NO" > "$EVIDENCE/$tag-bridge-verdict.txt"
  fi
  if awk '/Bound services:/ && /label=MyAI/ { found=1 } /Bound services:/ { in_bound=1; if (/label=MyAI/) found=1; next } /Crashed services:/ { in_bound=0 } in_bound && /label=MyAI/ { found=1 } END { exit !found }' "$EVIDENCE/$tag-dumpsys-accessibility.txt"; then
    echo "bound_service=YES" >> "$EVIDENCE/$tag-bridge-verdict.txt"
  else
    echo "bound_service=NO_OR_UNPARSED" >> "$EVIDENCE/$tag-bridge-verdict.txt"
  fi
  echo "--- $tag bridge verdict ---"
  cat "$EVIDENCE/$tag-bridge-verdict.txt"
  echo "--- $tag foreground ---"
  grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/$tag-activities.txt" | tail -n 8 || true
  echo "--- $tag ANR summary ---"
  tail -n 80 "$EVIDENCE/$tag-logcat-summary.txt" 2>/dev/null || true
}

capture_dropbox() {
  local tag="$1"
  echo "Capturing DropBox ANR entries: $tag"
  adb_bounded shell dumpsys dropbox --print > "$EVIDENCE/$tag-dropbox-full.txt" 2>&1 || true
  grep -i -E -C 3 'system_server_anr|data_app_anr|system_app_anr|ANR|not responding|Input dispatching timed out' "$EVIDENCE/$tag-dropbox-full.txt" > "$EVIDENCE/$tag-dropbox-anr-summary.txt" 2>/dev/null || true
  echo "DropBox bytes: $(wc -c < "$EVIDENCE/$tag-dropbox-full.txt" 2>/dev/null || echo 0)"
}

wait_for_bound() {
  local n state
  for n in $(seq 1 30); do
    state="$(adb_bounded shell dumpsys accessibility 2>/dev/null || true)"
    if printf '%s\n' "$state" | awk -v pkg="$PKG" '
      /Bound services:/ && /Service\[label=MyAI/ { bound=1 }
      /Enabled services:/ && index($0,pkg)>0 { enabled=1 }
      END { exit !(bound && enabled) }
    '; then
      printf '%s\n' "$state" > "$EVIDENCE/accessibility-bound-confirmed.txt"
      return 0
    fi
    sleep 1
  done
  printf '%s\n' "$state" > "$EVIDENCE/accessibility-bound-timeout.txt"
  return 1
}

echo "RUN 11 CONTROL: Settings -> MyAI transition with NO MyAI tool intents."
[[ -s "$APK" ]] || { echo "CONTROL_SETUP_FAIL: APK missing: $APK"; exit 2; }
adb_bounded start-server || true
adb_bounded wait-for-device || { echo "CONTROL_SETUP_FAIL: adb device unavailable"; exit 2; }
booted=0
for n in $(seq 1 90); do
  [[ "$(adb_bounded shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]] && { booted=1; break; }
  sleep 5
done
if [[ "$booted" -ne 1 ]]; then
  echo "CONTROL_SETUP_FAIL: emulator did not finish booting" | tee "$EVIDENCE/control-setup-result.txt"
  snapshot "boot-timeout"
  capture_dropbox "boot-timeout"
  exit 2
fi

adb_bounded install -r "$APK" > "$EVIDENCE/install.txt" 2>&1 || {
  echo "CONTROL_SETUP_FAIL: install failed" | tee "$EVIDENCE/control-setup-result.txt"
  snapshot "install-failure"
  capture_dropbox "install-failure"
  exit 2
}
adb_bounded logcat -c >/dev/null 2>&1 || true
adb_bounded shell am start -n "$ACTIVITY" > "$EVIDENCE/initial-app-launch.txt" 2>&1 || true
sleep 4
snapshot "pre-accessibility-enable"
capture_dropbox "pre-accessibility-enable"

# Use the same explicit secure-setting setup as the existing diagnostic harness.
adb_bounded shell settings --user 0 put secure accessibility_enabled 0 >/dev/null 2>&1 || true
adb_bounded shell settings --user 0 delete secure enabled_accessibility_services >/dev/null 2>&1 || true
sleep 2
adb_bounded shell settings --user 0 put secure enabled_accessibility_services "$SERVICE" > "$EVIDENCE/enable-service-setting.txt" 2>&1 || true
adb_bounded shell settings --user 0 put secure accessibility_enabled 1 > "$EVIDENCE/enable-accessibility-setting.txt" 2>&1 || true
if ! wait_for_bound; then
  echo "CONTROL_SETUP_WARNING: service did not bind before Settings transition" | tee "$EVIDENCE/control-setup-result.txt"
  snapshot "bind-timeout-before-transition"
  capture_dropbox "bind-timeout-before-transition"
  # Continue to observe whether the framework recovers after the screen transition.
else
  echo "CONTROL_SETUP_OK: MyAI Accessibility service bound before transition" | tee "$EVIDENCE/control-setup-result.txt"
fi
snapshot "before-settings-transition"

adb_bounded shell am start -a android.settings.SETTINGS > "$EVIDENCE/settings-launch.txt" 2>&1 || true
sleep 4
snapshot "settings-foreground-no-tools"
capture_dropbox "settings-foreground-no-tools"

# Normal launch over Settings. No smoke intent / no get_screen_state / no scroll /
# no home / no back is sent by this control script.
adb_bounded shell am start -n "$ACTIVITY" > "$EVIDENCE/myai-launch-over-settings.txt" 2>&1 || true
sleep 5
snapshot "myai-foreground-no-tools"
capture_dropbox "myai-foreground-no-tools"
sleep 5
snapshot "myai-after-settle-no-tools"

echo "Attempting best-effort bugreport (may be unavailable on this emulator image)."
adb_bounded bugreport "$EVIDENCE/bugreport-control.zip" > "$EVIDENCE/bugreport-command.txt" 2>&1 || true
if [[ ! -s "$EVIDENCE/bugreport-control.zip" ]]; then
  rm -f "$EVIDENCE/bugreport-control.zip"
  echo "bugreport unavailable or empty" >> "$EVIDENCE/bugreport-command.txt"
fi

if grep -Eiq 'ANR in |am_anr|Input dispatching timed out|Application Not Responding:|AppNotRespondingDialog|Timed out while trying to bind' "$EVIDENCE/"*-logcat.txt "$EVIDENCE/"*-lastanr.txt 2>/dev/null; then
  echo "CONTROL_RESULT=ANR_SIGNATURE_OBSERVED_WITHOUT_MYAI_TOOL_CALLS" | tee "$EVIDENCE/control-result.txt"
else
  echo "CONTROL_RESULT=NO_MATCHING_ANR_SIGNATURE_IN_CAPTURED_LOGS" | tee "$EVIDENCE/control-result.txt"
fi
echo "Control experiment complete. This result is observational; it does not by itself prove emulator-only causation."
exit 0

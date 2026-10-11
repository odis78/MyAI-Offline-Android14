#!/usr/bin/env bash
set -euo pipefail

ROOT="$GITHUB_WORKSPACE"
PROJECT="$ROOT/MyAI_Infinix_v0.1.0_ALPHA"
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
EVIDENCE="$ROOT/test-bridge/android-evidence"
PKG="com.dmitry.myai.infinix"
ACTIVITY="$PKG/.MainActivity"
SERVICE="$PKG/com.dmitry.myai.infinix.bridge.MyAiAccessibilityService"

# Prevent a stuck adb subcommand from hanging this smoke test indefinitely.
# Reconnect/install operations get longer limits; ordinary probes stay bounded.
adb() {
  local limit=30 arg
  for arg in "$@"; do
    case "$arg" in
      wait-for-device) limit=180 ;;
      install) limit=90 ;;
    esac
  done
  timeout --foreground "${limit}s" command adb "$@"
}

mkdir -p "$EVIDENCE"
capture_failure_evidence() {
  local tag="${1:-failure}"
  # Preserve device connectivity evidence before recovery or restarts can overwrite it.
  adb devices -l > "$EVIDENCE/adb-devices-${tag}.txt" 2>&1 || true
  adb version > "$EVIDENCE/adb-version-${tag}.txt" 2>&1 || true
  # Preserve attribution before recovery or restarts can overwrite the evidence.
  adb logcat -d -b main -b system -b events -b crash -v threadtime -t 12000 > "$EVIDENCE/logcat-${tag}.txt" 2>&1 || true
  adb shell dumpsys activity lastanr > "$EVIDENCE/lastanr-${tag}.txt" 2>&1 || true
  adb shell dumpsys activity processes > "$EVIDENCE/processes-${tag}.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$EVIDENCE/activities-${tag}.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/windows-${tag}.txt" 2>&1 || true
  adb shell dumpsys input > "$EVIDENCE/input-${tag}.txt" 2>&1 || true
  adb shell ps -A -o PID,PPID,NAME,ARGS > "$EVIDENCE/ps-${tag}.txt" 2>&1 || adb shell ps -A > "$EVIDENCE/ps-${tag}.txt" 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/screenshot-${tag}.png" 2>/dev/null || true
  grep -i -E -C 4 'ANR in|am_anr|Input dispatching timed out|not responding|Reason:|com\\.dmitry\\.myai\\.infinix' "$EVIDENCE/logcat-${tag}.txt" > "$EVIDENCE/anr-summary-${tag}.txt" 2>/dev/null || true
}
fail() {
  echo "FAIL: $*" >&2
  capture_failure_evidence failure
  exit 1
}

echo "Checking debug APK: $APK"
[[ -s "$APK" ]] || fail "Expected APK missing or empty"
adb start-server
adb wait-for-device

booted=0
for attempt in $(seq 1 90); do
  value="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$value" == "1" ]]; then booted=1; break; fi
  sleep 5
done
[[ "$booted" -eq 1 ]] || fail "Android emulator did not finish booting within 7.5 minutes"

echo "Installing APK"
# API 34 CI images can temporarily stall PackageManager integrity verification
# while System UI is recovering. Keep the original failure evidence, wait for
# the package manager, and retry a bounded number of times before failing.
install_ok=0
for attempt in $(seq 1 3); do
  install_output="$(adb install -r "$APK" 2>&1)" && install_status=0 || install_status=$?
  printf '%s\n' "$install_output" | tee "$EVIDENCE/apk-install-attempt-${attempt}.txt"
  if [[ "$install_status" -eq 0 ]]; then
    install_ok=1
    break
  fi
  if [[ "$attempt" -lt 3 ]]; then
    echo "APK install attempt $attempt failed (exit $install_status); waiting for PackageManager and retrying"
    adb shell dumpsys package > "$EVIDENCE/package-manager-after-install-failure-${attempt}.txt" 2>&1 || true
    adb shell dumpsys activity lastanr > "$EVIDENCE/lastanr-after-install-failure-${attempt}.txt" 2>&1 || true
    sleep $((attempt * 10))
    adb wait-for-device || true
  fi
done
[[ "$install_ok" -eq 1 ]] || fail "APK installation failed after 3 attempts"

# Start the app before changing secure accessibility settings. On fresh API
# emulator images PackageManager/AccessibilityManager can lag behind adb install;
# enabling a component before it appears in the installed-service list can cause
# the framework to immediately discard the setting.
adb logcat -c >/dev/null 2>&1 || true
echo "Launching MainActivity once to initialize package/service discovery"
prestart_output="$(adb shell am start -n "$ACTIVITY" 2>&1 || true)"
printf '%s\n' "$prestart_output" | tee "$EVIDENCE/activity-prestart.txt"
sleep 5

# Capture the pre-Accessibility baseline. Run #32 showed a System UI ANR
# before the test enabled the MyAI Accessibility service, so preserve evidence
# at this boundary instead of attributing the later overlay to MyAI by default.
adb logcat -d -b main -b system -b events -b crash -v threadtime -t 12000 > "$EVIDENCE/logcat-pre-accessibility.txt" 2>&1 || true
adb shell dumpsys activity lastanr > "$EVIDENCE/lastanr-pre-accessibility.txt" 2>&1 || true
adb shell dumpsys window windows > "$EVIDENCE/windows-pre-accessibility.txt" 2>&1 || true
adb shell dumpsys activity processes > "$EVIDENCE/processes-pre-accessibility.txt" 2>&1 || true
grep -i -E -C 4 'ANR in|am_anr|Input dispatching timed out|not responding|Reason:|com\\.dmitry\\.myai\\.infinix' "$EVIDENCE/logcat-pre-accessibility.txt" > "$EVIDENCE/anr-summary-pre-accessibility.txt" 2>/dev/null || true
if grep -Eiq 'ANR in (com[.]android\\.systemui|com[.]android\\.settings|system_server)|Application Not Responding: (system|System UI|Settings)' "$EVIDENCE/logcat-pre-accessibility.txt" "$EVIDENCE/windows-pre-accessibility.txt" "$EVIDENCE/lastanr-pre-accessibility.txt"; then
  echo "WARNING: System/UI ANR signature already exists before Accessibility is enabled; preserve as baseline evidence."
  printf '%s\n' "System/UI ANR signature detected before Accessibility enablement." > "$EVIDENCE/pre-accessibility-anr-marker.txt"
  # The baseline already shows a system ANR before MyAI Accessibility is enabled.
  # Reboot once to avoid testing the agent on a known-unhealthy framework state.
  echo "Rebooting emulator once after pre-Accessibility system ANR"
  adb reboot || fail "Could not reboot emulator after pre-Accessibility system ANR"
  adb wait-for-device || fail "Emulator did not reconnect after pre-Accessibility ANR reboot"
  rebooted=0
  for attempt in $(seq 1 90); do
    value="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$value" == "1" ]]; then rebooted=1; break; fi
    sleep 5
  done
  [[ "$rebooted" -eq 1 ]] || fail "Emulator did not finish rebooting after pre-Accessibility system ANR"
  # A boot-completed property can become true before PackageManager and
  # ActivityTaskManager are responsive again after a system_server/ANR reboot.
  # Wait for Android's core services before deciding that the installed app vanished.
  package_manager_ready=0
  for attempt in $(seq 1 30); do
    package_service="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
    activity_service="$(adb shell service check activity 2>/dev/null | tr -d '\r' || true)"
    package_probe="$(adb shell pm path android 2>/dev/null | tr -d '\r' || true)"
    if printf '%s' "$package_service" | grep -qi 'found' &&
       printf '%s' "$activity_service" | grep -qi 'found' &&
       [[ -n "$package_probe" ]]; then
      package_manager_ready=1
      break
    fi
    echo "Waiting for PackageManager/ActivityManager after reboot (attempt $attempt)"
    sleep 3
  done
  [[ "$package_manager_ready" -eq 1 ]] || {
    adb shell service list > "$EVIDENCE/services-after-reboot.txt" 2>&1 || true
    adb shell dumpsys package > "$EVIDENCE/package-manager-after-reboot.txt" 2>&1 || true
    fail "Android package/activity services did not recover after ANR reboot"
  }

  # Verify package presence only after the framework is responsive. If Android
  # genuinely lost the package registration, reinstall the same already-built APK.
  package_path="$(adb shell pm path "$PKG" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$package_path" ]]; then
    echo "MyAI package is not registered after reboot; reinstalling the same debug APK"
    adb install -r "$APK" > "$EVIDENCE/apk-install-after-reboot.txt" 2>&1 || fail "Could not reinstall APK after ANR reboot"
  fi

  resolved_activity=""
  for attempt in $(seq 1 20); do
    resolved_activity="$(adb shell cmd package resolve-activity --brief "$PKG" 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
    if [[ "$resolved_activity" == *"$PKG"*".MainActivity" || "$resolved_activity" == "$ACTIVITY" ]]; then
      break
    fi
    echo "Waiting for MainActivity resolution after reboot (attempt $attempt)"
    sleep 2
  done
  printf '%s\n' "$resolved_activity" > "$EVIDENCE/resolved-activity-after-reboot.txt"
  if [[ "$resolved_activity" != *"$PKG"*".MainActivity" && "$resolved_activity" != "$ACTIVITY" ]]; then
    adb shell dumpsys package "$PKG" > "$EVIDENCE/package-after-reboot.txt" 2>&1 || true
    adb shell cmd package resolve-activity --brief "$PKG" > "$EVIDENCE/resolve-activity-final.txt" 2>&1 || true
    fail "MainActivity is not resolvable after framework readiness wait: $resolved_activity"
  fi
  adb shell am start -W -n "$ACTIVITY" > "$EVIDENCE/activity-post-anr-reboot.txt" 2>&1 || fail "Could not relaunch MainActivity after baseline ANR reboot"
  if grep -Eiq 'Error: Activity class|does not exist|unable to resolve' "$EVIDENCE/activity-post-anr-reboot.txt"; then
    adb shell dumpsys package "$PKG" > "$EVIDENCE/package-after-reboot.txt" 2>&1 || true
    fail "Android rejected MainActivity after reboot; see package-after-reboot.txt"
  fi
  sleep 5
  adb logcat -d -b main -b system -b events -b crash -v threadtime -t 12000 > "$EVIDENCE/logcat-pre-accessibility-after-reboot.txt" 2>&1 || true
  adb shell dumpsys activity lastanr > "$EVIDENCE/lastanr-pre-accessibility-after-reboot.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/windows-pre-accessibility-after-reboot.txt" 2>&1 || true
  if grep -Eiq 'ANR in (com[.]android\\.systemui|com[.]android\\.settings|system_server)|Application Not Responding: (system|System UI|Settings)' "$EVIDENCE/logcat-pre-accessibility-after-reboot.txt" "$EVIDENCE/windows-pre-accessibility-after-reboot.txt" "$EVIDENCE/lastanr-pre-accessibility-after-reboot.txt"; then
    echo "WARNING: System/UI ANR persists after reboot; evidence will be retained."
    printf '%s\n' "System/UI ANR signature persists after reboot before Accessibility." > "$EVIDENCE/pre-accessibility-anr-persists-marker.txt"
  fi
fi

service_discovered=0
for attempt in $(seq 1 60); do
  installed_count="$(adb shell dumpsys accessibility 2>/dev/null | grep -oE 'installedServiceCount=[0-9]+' | head -n 1 | cut -d= -f2 || true)"
  if [[ "$installed_count" =~ ^[0-9]+$ ]] && (( installed_count >= 2 )); then
    service_discovered=1
    break
  fi
  sleep 1
done
if [[ "$service_discovered" -ne 1 ]]; then
  adb shell dumpsys accessibility > "$EVIDENCE/accessibility-discovery-failure.txt" 2>&1 || true
  adb shell dumpsys package "$PKG" > "$EVIDENCE/package-discovery-failure.txt" 2>&1 || true
  fail "AccessibilityManager did not discover the installed MyAI service"
fi

# Explicitly target Android's primary user and verify every setting write.
adb shell settings --user 0 put secure accessibility_enabled 0 || fail "Could not disable accessibility before rebind"
adb shell settings --user 0 delete secure enabled_accessibility_services || fail "Could not clear old accessibility service list"
sleep 2
adb shell settings --user 0 put secure enabled_accessibility_services "$SERVICE" || fail "Could not enable MyAI accessibility service"
sleep 1
adb shell settings --user 0 put secure accessibility_enabled 1 || fail "Could not enable the accessibility master switch"

settings_ready=0
for attempt in $(seq 1 15); do
  enabled="$(adb shell settings --user 0 get secure accessibility_enabled 2>/dev/null | tr -d '\r' || true)"
  services="$(adb shell settings --user 0 get secure enabled_accessibility_services 2>/dev/null | tr -d '\r' || true)"
  if [[ "$enabled" == "1" ]] && printf '%s' "$services" | grep -Fq "$SERVICE"; then
    settings_ready=1
    break
  fi
  sleep 1
done
if [[ "$settings_ready" -ne 1 ]]; then
  adb shell settings --user 0 get secure enabled_accessibility_services > "$EVIDENCE/enabled-accessibility-services.txt" 2>&1 || true
  adb shell settings --user 0 get secure accessibility_enabled > "$EVIDENCE/accessibility-enabled.txt" 2>&1 || true
  adb shell dumpsys accessibility > "$EVIDENCE/dumpsys-accessibility-settings-failure.txt" 2>&1 || true
  fail "Accessibility settings did not persist after enabling MyAI service"
fi

echo "Accessibility setting enabled: $enabled"
echo "Accessibility services setting: $services"
adb shell dumpsys accessibility > "$EVIDENCE/accessibility-before-launch.txt" 2>&1 || true

echo "Launching MainActivity for UI assertions"
start_output="$(adb shell am start -n "$ACTIVITY" 2>&1 || true)"
printf '%s\n' "$start_output" | tee "$EVIDENCE/activity-start.txt"
sleep 5

adb shell dumpsys activity activities > "$EVIDENCE/activities.txt"
if ! grep -E 'mResumedActivity|topResumedActivity' "$EVIDENCE/activities.txt" | grep -Fq "$ACTIVITY"; then
  fail "MainActivity is not the resumed/foreground activity (process-only check is not enough)"
fi

# Dump actual UI hierarchy. Some emulator images report exit code 0 even when
# uiautomator did not create the target file, so verify the file before pulling.
dump_ui() {
  local output=""
  for attempt in $(seq 1 5); do
    # Remove any previous hierarchy first: uiautomator can fail to dump while
    # the remote file from an earlier ANR dialog still exists. Without this,
    # adb pull may silently reuse stale XML and falsely report every app control missing.
    adb shell rm -f /data/local/tmp/myai-window.xml >/dev/null 2>&1 || true
    output="$(adb shell uiautomator dump /data/local/tmp/myai-window.xml 2>&1 | tr -d '\r' || true)"
    if printf '%s' "$output" | grep -Fq '/data/local/tmp/myai-window.xml' &&
       adb shell test -s /data/local/tmp/myai-window.xml >/dev/null 2>&1; then
      if adb pull /data/local/tmp/myai-window.xml "$EVIDENCE/window.xml" >/dev/null 2>&1; then
        return 0
      fi
    fi
    echo "UI hierarchy dump attempt $attempt did not produce a readable file: $output"
    sleep 2
  done
  fail "Could not create and pull Android UI hierarchy; last uiautomator output: $output"
}
# Headless API 34 emulators can become overloaded and show a system/Settings
# "isn't responding" dialog. Such a dialog overlays app controls; UIAutomator may
# still expose the obscured controls, so tapping their coordinates would hit the
# dialog instead. Dismiss it through the actual Wait button bounds before actions.
# Android 14 headless emulator sometimes raises an ANR dialog for system_server.
# UIAutomator can dump the obscured app tree instead of the dialog, so detect the
# overlay from WindowManager's focused-window state rather than only window.xml.
anr_window_visible() {
  adb shell dumpsys window windows 2>/dev/null |
    grep -Eiq 'mCurrentFocus=.*Application Not Responding|mFocusedWindow=.*Application Not Responding'
}

recover_unresponsive_dialog() {
  dialog_recovered=0
  local focused=""
  focused="$(adb shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedWindow' | tail -n 4 || true)"
  if ! printf '%s\n' "$focused" | grep -Eiq 'Application Not Responding: (system|Settings|System UI)|mCurrentFocus=.*Application Not Responding|mFocusedWindow=.*Application Not Responding'; then
    # A dialog can appear between the focus snapshot and this check. The app
    # hierarchy alone is not authoritative for overlays, so query WindowManager again.
    anr_window_visible || return 0
  fi

  echo "Android ANR overlay detected from WindowManager; focused-window evidence:"
  # Save diagnostics before any key events or relaunch attempts.
  capture_failure_evidence anr
  printf '%s\n' "$focused" | tee "$EVIDENCE/anr-focused-window.txt"
  adb shell dumpsys window windows > "$EVIDENCE/anr-window-manager.txt" 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/anr-overlay.png" 2>/dev/null || true

  # Prefer tapping Android's actual "Wait" button by its accessibility bounds.
  # DPAD navigation is unreliable when system_server itself is overloaded, and
  # ENTER without verified focus could select "Close app".
  dialog_recovered=1
  wait_tapped=0
  for tap_attempt in $(seq 1 3); do
    adb shell rm -f /data/local/tmp/myai-anr-dialog.xml >/dev/null 2>&1 || true
    adb shell uiautomator dump /data/local/tmp/myai-anr-dialog.xml >/dev/null 2>&1 || true
    if adb shell test -s /data/local/tmp/myai-anr-dialog.xml >/dev/null 2>&1 &&
       adb pull /data/local/tmp/myai-anr-dialog.xml "$EVIDENCE/window-anr-dialog.xml" >/dev/null 2>&1; then
      wait_bounds="$(python3 - "$EVIDENCE/window-anr-dialog.xml" <<'PY'
import sys, xml.etree.ElementTree as ET, re
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter('node'):
    rid=node.attrib.get('resource-id','')
    if rid in ('android:id/aerr_wait', 'com.android.systemui:id/aerr_wait') or rid.endswith(':id/aerr_wait'):
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            if x2>x1 and y2>y1:
                print((x1+x2)//2, (y1+y2)//2)
                raise SystemExit(0)
raise SystemExit(1)
PY
)" || true
      if [[ -n "$wait_bounds" ]]; then        read -r wait_x wait_y <<< "$wait_bounds"
        echo "Tapping verified Android ANR 'Wait' button at $wait_x,$wait_y"
        adb shell input tap "$wait_x" "$wait_y" >/dev/null 2>&1 || true
        wait_tapped=1
        break
      fi
    fi
    sleep 1
  done
  if [[ "$wait_tapped" -ne 1 ]]; then
    echo "Could not resolve the Android 'Wait' button bounds; using bounded DPAD fallback"
    adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null 2>&1 || true
    sleep 1
    adb shell input keyevent KEYCODE_ENTER >/dev/null 2>&1 || true
  fi
  for attempt in $(seq 1 12); do
    sleep 2
    if ! anr_window_visible; then
      echo "ANR overlay dismissed; waiting for system services to settle"
      sleep 4
      adb shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
      sleep 3
      dump_ui || return 1
      return 0
    fi
    # If ENTER did not select Wait, BACK dismisses the dialog on some API 34 images.
    if [[ "$attempt" -eq 4 ]]; then
      adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    fi
  done
  echo "ANR overlay remains focused after recovery attempts" >&2
  adb shell dumpsys accessibility > "$EVIDENCE/anr-accessibility.txt" 2>&1 || true
  return 1
}

# Recover before checking visible controls; the ANR window may cover the app while
# UIAutomator continues to return the underlying activity hierarchy. If recovery
# dismisses the system dialog but leaves MainActivity dead/backgrounded, explicitly
# relaunch it and re-check the hierarchy before declaring a UI regression.
recover_unresponsive_dialog || fail "Android system ANR overlay could not be dismissed"

# Only dump the UI after the ANR overlay check/recovery. UiAutomator can return
# a null root while a system dialog owns the active window; dumping first makes
# the recovery path unreachable on exactly the failures it is meant to handle.
dump_ui

ui_has_expected_controls() {
  python3 - "$EVIDENCE/window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
texts = [node.attrib.get("text", "").casefold() for node in root.iter("node")]
joined = "\n".join(texts)
expected = [
    "myai",
    "тест: домой",
    "тест: назад",
    "тест: прочитать экран",
    "тест: tool protocol",
    "тест: прокрутка вниз",
    "тест: прокрутка вверх",
    "журнал действий",
]
missing = [item for item in expected if item.casefold() not in joined]
if missing:
    print("Missing UI text after Unicode case normalization: " + ", ".join(missing), file=sys.stderr)
    raise SystemExit(1)
PY
}

ui_ready=0
for attempt in $(seq 1 3); do
  dump_ui || true
  if ui_has_expected_controls; then
    ui_ready=1
    break
  fi
  if [[ "$attempt" -lt 3 ]]; then
    echo "Expected controls are not visible after recovery; restarting MainActivity (attempt $attempt)"
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    sleep 2
    adb shell am start -n "$ACTIVITY" 2>&1 | tee "$EVIDENCE/activity-recovery-relaunch-$attempt.txt" || true
    sleep 8
  fi
done
[[ "$ui_ready" -eq 1 ]] || fail "Expected UI element/text not found after ANR recovery and activity relaunch attempts"

# Confirm the service itself connected; UI status is also refreshed live by MainActivity.
connected=0
for attempt in $(seq 1 30); do
  if adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -Fq 'SERVICE CONNECTED'; then
    connected=1
    break
  fi
  # Recover using WindowManager focus; UIAutomator may only return the covered app tree.
  recover_unresponsive_dialog || fail "Android system ANR recovery failed while waiting for Accessibility connection"
  sleep 2
done
if [[ "$connected" -ne 1 ]]; then
  # Save the framework's explanation instead of only reporting a missing log.
  adb shell settings get secure enabled_accessibility_services > "$EVIDENCE/enabled-accessibility-services.txt" 2>&1 || true
  adb shell settings get secure accessibility_enabled > "$EVIDENCE/accessibility-enabled.txt" 2>&1 || true
  adb shell dumpsys accessibility > "$EVIDENCE/dumpsys-accessibility-failure.txt" 2>&1 || true
  adb shell dumpsys package "$PKG" > "$EVIDENCE/dumpsys-package-failure.txt" 2>&1 || true
  adb logcat -d -b main -b system -b events -v time > "$EVIDENCE/logcat-accessibility-failure.txt" 2>&1 || true
  echo "---- enabled_accessibility_services ----"
  cat "$EVIDENCE/enabled-accessibility-services.txt" 2>/dev/null || true
  echo "---- accessibility_enabled ----"
  cat "$EVIDENCE/accessibility-enabled.txt" 2>/dev/null || true
  echo "---- accessibility framework service state ----"
  grep -i -E 'myai|Enabled services|Bound services|Crashed services|AccessibilityService' "$EVIDENCE/dumpsys-accessibility-failure.txt" | tail -n 100 || true
  echo "---- relevant Android logcat ----"
  grep -i -E 'myai.infinix|MyAI-Infinix|AccessibilityManager|AccessibilityService|Permission Denial|FATAL EXCEPTION|Exception' "$EVIDENCE/logcat-accessibility-failure.txt" | tail -n 160 || true
  fail "Accessibility Bridge service did not emit SERVICE CONNECTED (diagnostics saved)"
fi


# Require the status node itself to show the connected state; action logs can quote stale text.
status_ok=0
for attempt in $(seq 1 15); do
  recover_unresponsive_dialog || fail "Android system ANR recovery failed while waiting for connected Bridge status"
  adb shell rm -f /data/local/tmp/myai-window.xml >/dev/null 2>&1 || true
  dump_output="$(adb shell uiautomator dump /data/local/tmp/myai-window.xml 2>&1 | tr -d '\r' || true)"
  if printf '%s' "$dump_output" | grep -Fq '/data/local/tmp/myai-window.xml' &&
     adb pull /data/local/tmp/myai-window.xml "$EVIDENCE/window.xml" >/dev/null 2>&1 &&
     python3 - "$EVIDENCE/window.xml" <<'PY'
import sys, re
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
connected = (
    r'text="Control Bridge: ПОДКЛЮЧЕН"[^>]*resource-id="[^"]*:id/status"',
    r'resource-id="[^"]*:id/status"[^>]*text="Control Bridge: ПОДКЛЮЧЕН"',
)
disconnected = (
    r'text="Control Bridge: НЕ ПОДКЛЮЧЕН"[^>]*resource-id="[^"]*:id/status"',
    r'resource-id="[^"]*:id/status"[^>]*text="Control Bridge: НЕ ПОДКЛЮЧЕН"',
)
if any(re.search(p, xml) for p in disconnected):
    raise SystemExit(1)
if any(re.search(p, xml) for p in connected):
    raise SystemExit(0)
raise SystemExit(1)
PY
  then
    status_ok=1
    break
  fi
  echo "Control Bridge label is not connected yet (attempt $attempt)"
  sleep 1
done
[[ "$status_ok" -eq 1 ]] || fail "Control Bridge status stayed disconnected after SERVICE CONNECTED"

# Exercise the in-app tool protocol and screen-state command by tapping controls
# using their actual resource IDs and bounds from the Android accessibility tree.
tap_resource() {
  local resource_id="$1"
  local bounds=""
  recover_unresponsive_dialog || fail "Could not recover from emulator unresponsive dialog before tapping $resource_id"
  # Controls are inside the upper controlScroll. Earlier test actions can leave
  # later buttons below the visible viewport, so scroll that container until
  # the requested resource ID appears instead of failing on a valid off-screen node.
  for attempt in $(seq 1 12); do
    dump_ui || fail "UI dump failed before tap $resource_id"
    bounds="$(python3 - "$EVIDENCE/window.xml" "$resource_id" <<'PY'
import sys, xml.etree.ElementTree as ET, re
root=ET.parse(sys.argv[1]).getroot()
target=sys.argv[2]
for n in root.iter('node'):
    if n.attrib.get('resource-id','').endswith(':id/'+target):
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            if x2 > x1 and y2 > y1:
                print((x1+x2)//2, (y1+y2)//2)
                raise SystemExit(0)
raise SystemExit(1)
PY
)" || true
    if [[ -n "$bounds" ]]; then break; fi
    container_bounds="$(python3 - "$EVIDENCE/window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET, re
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if n.attrib.get('resource-id','').endswith(':id/controlScroll'):
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print(x1,y1,x2,y2)
            raise SystemExit(0)
raise SystemExit(1)
PY
)" || true
    if [[ -n "$container_bounds" ]]; then
      read -r x1 y1 x2 y2 <<< "$container_bounds"
      swipe_x="$(((x1+x2)/2))"
      swipe_start="$((y2-40))"
      swipe_end="$((y1+40))"
    else
      # Some Android 14 UIAutomator dumps omit the ScrollView node even though
      # its child controls are present. Fall back to a swipe within the upper
      # control area, not the lower action-log viewport.
      screen_size="$(adb shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | tail -n 1 || true)"
      if [[ "$screen_size" =~ ^([0-9]+)x([0-9]+)$ ]]; then
        screen_w="${BASH_REMATCH[1]}"
        screen_h="${BASH_REMATCH[2]}"
        swipe_x="$((screen_w/2))"
        swipe_start="$((screen_h*48/100))"
        swipe_end="$((screen_h*18/100))"
        echo "controlScroll node missing from UI hierarchy; using bounded upper-area swipe fallback"
      else
        fail "Control container and emulator screen dimensions unavailable while searching for $resource_id"
      fi
    fi
    # Try both scroll directions. A prior action can leave the viewport either
    # above or below the target; always swiping only upward can miss a valid node.
    if (( attempt % 2 == 0 )); then
      adb shell input swipe "$swipe_x" "$swipe_end" "$swipe_x" "$swipe_start" 350
      echo "Searching $resource_id: swipe toward earlier controls (attempt $attempt)"
    else
      adb shell input swipe "$swipe_x" "$swipe_start" "$swipe_x" "$swipe_end" 350
      echo "Searching $resource_id: swipe toward later controls (attempt $attempt)"
    fi
    sleep 1
  done
  if [[ -z "$bounds" ]]; then
    python3 - "$EVIDENCE/window.xml" "$EVIDENCE/missing-control-tree.txt" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
with open(sys.argv[2], "w", encoding="utf-8") as out:
    for n in root.iter("node"):
        rid=n.attrib.get("resource-id","")
        text=n.attrib.get("text","")
        desc=n.attrib.get("content-desc","")
        if rid or text or desc:
            out.write(f"resource-id={rid!r} text={text!r} content-desc={desc!r} bounds={n.attrib.get('bounds','')}\n")
PY
    fail "Control not found after bidirectional scrolling: $resource_id"
  fi
  read -r x y <<< "$bounds"
  adb shell input tap "$x" "$y"
  sleep 2
}

tap_resource "testScreen"
adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -Fq 'TOOL_RESULT get_screen_state: OK screen captured' || fail "get_screen_state tool did not report success"
dump_ui || fail "UI dump failed after screen-state test"
cp "$EVIDENCE/window.xml" "$EVIDENCE/window-after-screen.xml"
# UI labels or results can vary; require the app to remain foreground and capture the output.
adb shell dumpsys activity activities > "$EVIDENCE/activities-after-screen.txt"
grep -E 'mResumedActivity|topResumedActivity' "$EVIDENCE/activities-after-screen.txt" | grep -Fq "$ACTIVITY" || fail "App left foreground after screen-state tool"

tap_resource "testScrollUp"
adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -Fq 'TOOL_RESULT scroll_up: OK scroll dispatched' || fail "scroll_up tool did not report success"

# The Home tool legitimately backgrounds MainActivity. First dismiss any
# emulator ANR overlay, then tap the control and poll for its result. If an ANR
# overlay raced with the tap, recover it and retry the tap once.
recover_unresponsive_dialog || fail "Could not recover from emulator dialog before tool protocol test"
tap_resource "testTool"
home_ok=0
for attempt in $(seq 1 12); do
  if adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -Fq 'TOOL_RESULT home: OK dispatched'; then
    home_ok=1
    break
  fi
  recover_unresponsive_dialog || fail "Could not recover from emulator dialog while waiting for tool result"
  if [[ "$dialog_recovered" -eq 1 ]]; then
    # If the dialog had intercepted the previous tap, bring the app back and retry.
    adb shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
    sleep 2
    tap_resource "testTool"
  fi
  sleep 2
done
[[ "$home_ok" -eq 1 ]] || fail "Tool protocol home command did not report success after dialog recovery/retry"
dump_ui || fail "UI dump failed after tool protocol test"
cp "$EVIDENCE/window.xml" "$EVIDENCE/window-after-tool.xml"

# Fail on a fatal exception for this package, even if Android leaves a process briefly alive.
adb logcat -d -t 8000 > "$EVIDENCE/logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.dmitry\.myai\.infinix, PID:' "$EVIDENCE/logcat.txt" | grep -q .; then
  fail "Fatal application exception found in logcat"
fi
adb exec-out screencap -p > "$EVIDENCE/android-emulator.png"
adb shell dumpsys activity activities > "$EVIDENCE/activities-final.txt"
cp "$APK" "$EVIDENCE/app-debug.apk"
echo "PASS: install, foreground Activity, visible controls, Accessibility Bridge connection, screen-state action, tool-protocol action, and crash scan all passed."
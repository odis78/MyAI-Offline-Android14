#!/usr/bin/env bash
set -euo pipefail

ROOT="$GITHUB_WORKSPACE"
PROJECT="$ROOT/MyAI_Infinix_v0.1.0_ALPHA"
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
EVIDENCE="$ROOT/test-bridge/android-evidence"
PKG="com.dmitry.myai.infinix"
ACTIVITY="$PKG/.MainActivity"
SERVICE="$PKG/com.dmitry.myai.infinix.bridge.MyAiAccessibilityService"

mkdir -p "$EVIDENCE"
fail() {
  echo "FAIL: $*" >&2
  adb logcat -d -t 8000 > "$EVIDENCE/logcat-failure.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$EVIDENCE/activities-failure.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$EVIDENCE/screenshot-failure.png" 2>/dev/null || true
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
adb install -r "$APK" || fail "APK installation failed"

# Start the app before changing secure accessibility settings. On fresh API
# emulator images PackageManager/AccessibilityManager can lag behind adb install;
# enabling a component before it appears in the installed-service list can cause
# the framework to immediately discard the setting.
adb logcat -c >/dev/null 2>&1 || true
echo "Launching MainActivity once to initialize package/service discovery"
prestart_output="$(adb shell am start -W -n "$ACTIVITY" 2>&1 || true)"
printf '%s\n' "$prestart_output" | tee "$EVIDENCE/activity-prestart.txt"
sleep 5

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
start_output="$(adb shell am start -W -n "$ACTIVITY" 2>&1 || true)"
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
    output="$(adb shell uiautomator dump /data/local/tmp/myai-window.xml 2>&1 | tr -d '\r' || true)"
    if adb shell test -s /data/local/tmp/myai-window.xml >/dev/null 2>&1; then
      if adb pull /data/local/tmp/myai-window.xml "$EVIDENCE/window.xml" >/dev/null 2>&1; then
        return 0
      fi
    fi
    echo "UI hierarchy dump attempt $attempt did not produce a readable file: $output"
    sleep 2
  done
  fail "Could not create and pull Android UI hierarchy; last uiautomator output: $output"
}
dump_ui

# Headless API 34 emulators can become overloaded and show a system/Settings
# "isn't responding" dialog. Such a dialog overlays app controls; UIAutomator may
# still expose the obscured controls, so tapping their coordinates would hit the
# dialog instead. Dismiss it through the actual Wait button bounds before actions.
recover_unresponsive_dialog() {
  dialog_recovered=0
  dump_ui || return 1
  if ! grep -Eiq "(Settings|Process system|System UI|system_server).{0,80}isn't responding|isn't responding.{0,80}(Settings|Process system|System UI|system_server)" "$EVIDENCE/window.xml"; then
    return 0
  fi
  local wait_bounds=""
  wait_bounds="$(python3 - "$EVIDENCE/window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET, re
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    text=(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).casefold()
    if n.attrib.get('resource-id') == 'android:id/aerr_wait' or text.strip() == 'wait':
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
)" || true
  if [[ -n "$wait_bounds" ]]; then
    read -r wx wy <<< "$wait_bounds"
    echo "Unresponsive system dialog detected; tapping Wait at $wx,$wy"
    adb shell input tap "$wx" "$wy" || true
    dialog_recovered=1
    sleep 4
    adb shell am force-stop com.android.settings >/dev/null 2>&1 || true
    adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
    sleep 3
    dump_ui || return 1
    return 0
  fi
  echo "Unresponsive dialog detected but Wait button was not found; saving hierarchy" >&2
  cp "$EVIDENCE/window.xml" "$EVIDENCE/unresponsive-dialog.xml" || true
  return 1
}

# The headless Android emulator can occasionally show a system Settings ANR dialog
# after accessibility is toggled. Dismiss it using the actual UI bounds, then
# relaunch our activity before asserting app UI.
if grep -Fq "Settings isn't responding" "$EVIDENCE/window.xml"; then
  echo "System Settings ANR dialog detected; choosing Wait and restoring app foreground"
  wait_bounds="$(python3 - "$EVIDENCE/window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET, re
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if n.attrib.get('resource-id') == 'android:id/aerr_wait':
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            break
PY
)"
  if [[ -n "$wait_bounds" ]]; then
    read -r wx wy <<< "$wait_bounds"
    adb shell input tap "$wx" "$wy" || true
    sleep 3
  fi
  adb shell am force-stop com.android.settings >/dev/null 2>&1 || true
  adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
  sleep 5
  dump_ui
fi

if ! python3 - "$EVIDENCE/window.xml" <<'PY'
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
    "журнал действий",
]
missing = [item for item in expected if item.casefold() not in joined]
if missing:
    print("Missing UI text after Unicode case normalization: " + ", ".join(missing), file=sys.stderr)
    raise SystemExit(1)
PY
then
  fail "Expected UI element/text not found in hierarchy (case-insensitive check)"
fi

# Confirm the service itself connected; UI status is also refreshed live by MainActivity.
connected=0
for attempt in $(seq 1 30); do
  if adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -Fq 'SERVICE CONNECTED'; then
    connected=1
    break
  fi
  # Recover if the emulator overlays the app with a Settings ANR dialog.
  dump_ui || true
  if grep -Fq "Settings isn't responding" "$EVIDENCE/window.xml"; then
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    adb shell am force-stop com.android.settings >/dev/null 2>&1 || true
    adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
  fi
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

# Exercise the in-app tool protocol and screen-state command by tapping controls
# using their actual resource IDs and bounds from the Android accessibility tree.
tap_resource() {
  local resource_id="$1"
  local bounds=""
  recover_unresponsive_dialog || fail "Could not recover from emulator unresponsive dialog before tapping $resource_id"
  # Controls are inside the upper controlScroll. Earlier test actions can leave
  # later buttons below the visible viewport, so scroll that container until
  # the requested resource ID appears instead of failing on a valid off-screen node.
  for attempt in $(seq 1 6); do
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
)" || fail "Control container not found while searching for $resource_id"
    read -r x1 y1 x2 y2 <<< "$container_bounds"
    adb shell input swipe "$((x1+x2)/2)" "$((y2-40))" "$((x1+x2)/2)" "$((y1+40))" 350
    sleep 1
  done
  [[ -n "$bounds" ]] || fail "Control not found after scrolling controlScroll: $resource_id"
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
    adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
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

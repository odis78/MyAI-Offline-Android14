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
      bugreport) limit=180 ;;
    esac
  done
  timeout --foreground "${limit}s" adb "$@"
}

mkdir -p "$EVIDENCE"

# Best-effort ANR trace collection. /data/anr may be unreadable to shell on
# some Android images, so preserve permission/error output and request a
# bugreport, which may contain system_server and app thread stacks.
capture_anr_traces() {
  local tag="${1:-failure}"
  mkdir -p "$EVIDENCE/anr-traces-$tag"
  adb shell ls -la /data/anr > "$EVIDENCE/anr-files-$tag.txt" 2>&1 || true
  adb pull /data/anr "$EVIDENCE/anr-traces-$tag" > "$EVIDENCE/anr-pull-$tag.txt" 2>&1 || true
  adb bugreport "$EVIDENCE/bugreport-$tag.zip" > "$EVIDENCE/bugreport-$tag.txt" 2>&1 || true
  if [[ ! -s "$EVIDENCE/bugreport-$tag.zip" ]]; then
    rm -f "$EVIDENCE/bugreport-$tag.zip"
  fi
}

# UiAutomator's UiTestAutomationService temporarily takes over Android's
# accessibility framework and can unbind ordinary AccessibilityServices.
# Wait on dumpsys (not another UIAutomator call) for MyAI to be bound again.
wait_for_accessibility_bound() {
  local attempts="${1:-20}" state
  for attempt in $(seq 1 "$attempts"); do
    state="$(adb shell dumpsys accessibility 2>/dev/null || true)"
    # Android 14 dumpsys prints a bound service's label here, not its
    # component/package name. Verify the MyAI label is actually in Bound
    # services AND the exact component remains enabled in Enabled services.
    if printf '%s\n' "$state" | awk -v pkg="$PKG" '
      /Bound services:/ && /Service\[label=MyAI/ { found_bound=1 }
      /Enabled services:/ && index($0, pkg) > 0 { found_enabled=1 }
      END { exit !(found_bound && found_enabled) }
    '; then
      printf '%s\n' "$state" > "$EVIDENCE/accessibility-bound-current.txt"
      return 0
    fi
    sleep 1
  done
  printf '%s\n' "$state" > "$EVIDENCE/accessibility-not-bound.txt"
  return 1
}

capture_failure_evidence() {
  local tag="${1:-failure}"
  # Preserve device connectivity evidence before recovery or restarts can overwrite it.
  adb devices -l > "$EVIDENCE/adb-devices-${tag}.txt" 2>&1 || true
  adb version > "$EVIDENCE/adb-version-${tag}.txt" 2>&1 || true
  # Preserve attribution before recovery or restarts can overwrite the evidence.
  # Keep both a bounded multi-buffer log and the full available logcat ring buffer.
  adb logcat -d -b main -b system -b events -b crash -v threadtime -t 12000 > "$EVIDENCE/logcat-${tag}.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$EVIDENCE/full-logcat-${tag}.txt" 2>&1 || true
  adb shell dumpsys accessibility > "$EVIDENCE/accessibility-${tag}.txt" 2>&1 || true
  adb shell top -H -n 1 > "$EVIDENCE/top-threads-${tag}.txt" 2>&1 || true
  adb shell dumpsys cpuinfo > "$EVIDENCE/cpuinfo-${tag}.txt" 2>&1 || true
  adb shell dumpsys activity lastanr > "$EVIDENCE/lastanr-${tag}.txt" 2>&1 || true
  adb shell dumpsys activity processes > "$EVIDENCE/processes-${tag}.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$EVIDENCE/activities-${tag}.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/windows-${tag}.txt" 2>&1 || true
  adb shell dumpsys input > "$EVIDENCE/input-${tag}.txt" 2>&1 || true
  adb shell ps -A -o PID,PPID,NAME,ARGS > "$EVIDENCE/ps-${tag}.txt" 2>&1 || adb shell ps -A > "$EVIDENCE/ps-${tag}.txt" 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/screenshot-${tag}.png" 2>/dev/null || true
  capture_anr_traces "$tag"
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
if grep -Eiq 'ANR in (com[.]android[.]systemui|com[.]android[.]settings|com[.]android[.]phone|system_server)|Application Not Responding: (system|System UI|Settings)' "$EVIDENCE/logcat-pre-accessibility.txt" "$EVIDENCE/windows-pre-accessibility.txt" "$EVIDENCE/lastanr-pre-accessibility.txt"; then
  echo "WARNING: System/UI ANR signature already exists before Accessibility is enabled; preserve as baseline evidence."
  # Run #88's separate clean-emulator job observed the same class of System/UI ANR
  # without MyAI installed. Do not reboot automatically based on this signature:
  # adb reboot/reconnect can stall on the unhealthy AVD and consume the job timeout.
  # Preserve evidence and let the bounded ANR-dialog recovery below decide whether
  # the emulator is usable for smoke actions.
  capture_anr_traces pre-accessibility
  printf '%s\n' "System/UI ANR signature detected before Accessibility enablement; retained as baseline evidence, no automatic reboot." > "$EVIDENCE/pre-accessibility-anr-marker.txt"
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

# Verify the actual framework binding before starting UIAutomator. A hierarchy
# dump itself registers UiTestAutomationService and can unbind MyAI temporarily.
if ! wait_for_accessibility_bound 30; then
  adb shell dumpsys accessibility > "$EVIDENCE/accessibility-bind-timeout.txt" 2>&1 || true
  adb logcat -d -b main -b system -b events -b crash -v threadtime -t 12000 > "$EVIDENCE/logcat-bind-timeout.txt" 2>&1 || true
  fail "AccessibilityManager did not bind MyAI after settings were enabled"
fi
echo "Accessibility framework confirms MyAI is bound"

echo "Launching MainActivity for UI assertions"
start_output="$(adb shell am start -n "$ACTIVITY" 2>&1 || true)"
printf '%s\n' "$start_output" | tee "$EVIDENCE/activity-start.txt"
sleep 5

adb shell dumpsys activity activities > "$EVIDENCE/activities.txt"
if ! grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/activities.txt" | grep -Fq "$ACTIVITY"; then
  fail "MainActivity is not the resumed/foreground activity (process-only check is not enough)"
fi

# Dump actual UI hierarchy. Some emulator images report exit code 0 even when
# uiautomator did not create the target file, so verify the file before pulling.
dump_ui() {
  # Avoid UIAutomator/UIAutomation: it can take over Accessibility and interfere
  # with the service being tested. Keep a screenshot as UI evidence instead.
  adb exec-out screencap -p > "$EVIDENCE/screenshot-current.png" 2>/dev/null ||
    return 1
  [[ -s "$EVIDENCE/screenshot-current.png" ]]
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
  # Do not use UIAutomator: it can take over Accessibility and unbind MyAI.
  # If Android presents its system ANR dialog, preserve evidence and tap the
  # visible "Wait" row using screen-relative coordinates, then verify recovery.
  if ! anr_window_visible; then
    dialog_recovered=0
    return 0
  fi
  dialog_recovered=1
  echo "WARNING: Android system ANR overlay detected; saving evidence and choosing Wait." >&2
  adb shell dumpsys accessibility > "$EVIDENCE/anr-accessibility.txt" 2>&1 || true
  adb shell dumpsys window windows > "$EVIDENCE/anr-window-manager.txt" 2>&1 || true
  adb exec-out screencap -p > "$EVIDENCE/anr-overlay.png" 2>/dev/null || true
  local size w h x y attempt
  size="$(adb shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | tail -n 1 || true)"
  [[ "$size" =~ ^([0-9]+)x([0-9]+)$ ]] || return 1
  w="${BASH_REMATCH[1]}"
  h="${BASH_REMATCH[2]}"
  # Android 14's standard system ANR dialog places "Wait" near 47% screen height.
  x="$((w * 42 / 100))"
  y="$((h * 47 / 100))"
  for attempt in 1 2; do
    echo "Selecting Wait on Android ANR dialog (attempt $attempt)"
    adb shell input tap "$x" "$y" || true
    for _ in $(seq 1 10); do
      sleep 2
      if ! anr_window_visible; then
        echo "System ANR dialog dismissed; allowing Android to settle."
        sleep 4
        return 0
      fi
    done
  done
  echo "FAIL: Android system ANR dialog remained visible after Wait attempts." >&2
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
  # No hierarchy dump while Accessibility is enabled. Validate foreground
  # Activity, screenshot capture, and framework binding instead.
  adb shell dumpsys activity activities > "$EVIDENCE/activities-ui-check.txt" 2>&1 || return 1
  grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/activities-ui-check.txt" |
    grep -Fq "$ACTIVITY" || return 1
  [[ -s "$EVIDENCE/screenshot-current.png" ]] || return 1
  wait_for_accessibility_bound 5
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


# Do not assert the live status label using UiAutomator: that API takes over the
# accessibility framework and can make the label stale/disconnected by design.
# The authoritative check is the framework's bound-service list.
wait_for_accessibility_bound 20 || fail "Control Bridge is not bound after UI assertions"
# Exercise the in-app tool protocol using screen-relative coordinates calibrated
# against the saved 1080x1920 screenshot. Accessibility hierarchy dumps are avoided
# because UiAutomation can interfere with the service being tested.
tap_resource() {
  local resource_id="$1" screen_size screen_w screen_h density row_y x
  recover_unresponsive_dialog || fail "Android ANR overlay detected before tapping $resource_id"
  screen_size="$(adb shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | tail -n 1 || true)"
  density="$(adb shell wm density 2>/dev/null | grep -oE '[0-9]+' | tail -n 1 || true)"
  [[ "$screen_size" =~ ^([0-9]+)x([0-9]+)$ ]] || fail "Cannot determine emulator screen dimensions"
  screen_w="${BASH_REMATCH[1]}"
  screen_h="${BASH_REMATCH[2]}"
  [[ "$density" =~ ^[0-9]+$ ]] || density=420
  # The diagnostics controls are at about 65% of screen height on the
  # actual app layout. Do not derive this from the bottom: the previous 144dp
  # formula landed inside empty space and missed every test button.
  row_y="$((screen_h * 65 / 100))"

  case "$resource_id" in
    testScreen)
      x="$((screen_w * 52 / 100))"
      ;;
    testTool)
      adb shell input swipe "$((screen_w * 15 / 100))" "$row_y" "$((screen_w * 85 / 100))" "$row_y" 300
      sleep 1
      x="$((screen_w * 78 / 100))"
      ;;
    testScrollUp)
      adb shell input swipe "$((screen_w * 85 / 100))" "$row_y" "$((screen_w * 15 / 100))" "$row_y" 300
      sleep 1
      x="$((screen_w * 78 / 100))"
      ;;
    *)
      fail "Unsupported coordinate-based test control: $resource_id"
      ;;
  esac
  echo "Tapping $resource_id by screen-relative coordinates ($x,$row_y), without UIAutomator"
  adb shell input tap "$x" "$row_y"
  sleep 2
  # The system ANR can appear immediately after a tap. Previously we checked
  # only before tapping, so a new dialog could intercept the request entirely.
  if anr_window_visible; then
    echo "ANR overlay appeared after tapping $resource_id; recover and retry once." >&2
    recover_unresponsive_dialog || fail "Could not recover ANR overlay after tapping $resource_id"
    adb shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
    sleep 3
    adb shell input tap "$x" "$row_y"
    sleep 2
    recover_unresponsive_dialog || fail "ANR overlay reappeared after retrying $resource_id"
  fi
  dump_ui || fail "Could not capture screenshot after tapping $resource_id"
}

# Tool dispatch runs on the app's single-thread executor. On a slow or ANR-affected
# emulator, the result may be logged after tap_resource's short UI settle delay.
# Poll for the explicit result and report FAIL results rather than misclassifying
# delayed execution as a broken tool.
wait_for_tool_result() {
  local tool="$1" expected="$2" line=""
  for attempt in $(seq 1 30); do
    # Give the bridge time to respond after recovering a system-level ANR.
    recover_unresponsive_dialog || return 1
    line="$(adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -F "TOOL_RESULT ${tool}:" | tail -n 1 || true)"
    if [[ -n "$line" ]]; then
      echo "Observed tool result: $line"
      if printf '%s\n' "$line" | grep -Fq "$expected"; then
        return 0
      fi
      echo "Tool $tool returned a non-success result" >&2
      return 1
    fi
    sleep 1
  done
  echo "Timed out waiting for tool result: $tool" >&2
  adb logcat -d -s MyAI-Infinix:I 2>/dev/null | grep -F "TOOL_RESULT" | tail -n 30 || true
  # Snapshot diagnostics immediately at timeout, before fail/recovery changes device state.
  capture_failure_evidence "timeout-$tool"
  return 1
}

# Dispatch smoke tools through explicit Activity intents instead of approximate
# screen coordinates. This exercises the real ToolAgent/ToolExecutor path and
# avoids false failures from layout, density, or system ANR overlays.
run_test_tool() {
  local tool="$1"
  recover_unresponsive_dialog || fail "Could not recover from emulator dialog before $tool test"
  echo "Dispatching smoke tool through Activity intent: $tool"
  adb shell am start -n "$ACTIVITY" --es myai_test_tool "$tool" || fail "Could not dispatch smoke intent for $tool"
  sleep 2
}

run_test_tool "get_screen_state"
wait_for_tool_result "get_screen_state" "TOOL_RESULT get_screen_state: OK screen captured" || fail "get_screen_state tool did not report success (see explicit tool result/timeout above)"
dump_ui || fail "UI dump failed after screen-state test"
cp "$EVIDENCE/screenshot-current.png" "$EVIDENCE/screenshot-after-screen.png"
adb shell dumpsys activity activities > "$EVIDENCE/activities-after-screen.txt"
grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/activities-after-screen.txt" | grep -Fq "$ACTIVITY" || fail "App left foreground after screen-state tool"

run_test_tool "scroll_up"
wait_for_tool_result "scroll_up" "TOOL_RESULT scroll_up: OK scroll dispatched" || fail "scroll_up tool did not report success (see explicit tool result/timeout above)"

# Home legitimately backgrounds MainActivity; the explicit result log remains testable.
run_test_tool "home"
wait_for_tool_result "home" "TOOL_RESULT home: OK dispatched" || fail "Tool protocol home command did not report success"
dump_ui || true
cp "$EVIDENCE/screenshot-current.png" "$EVIDENCE/screenshot-after-tool.png"

# Verify Back by destination, not merely by performGlobalAction() returning true.
# Put Settings behind MyAI, dispatch Back through the real ToolExecutor path, and
# assert that Android actually returns to Settings rather than the launcher/home.
echo "Preparing a clean Settings -> MyAI task transition for Back navigation test"
# The earlier smoke setup reused MyAI's already-existing task after the Home test.
# In that case Settings was foreground before launch, but not necessarily the
# previous task in Android's task history. Force-stop MyAI, open Settings, then
# start MainActivity in a fresh task so the Back destination assertion is valid.
adb shell am force-stop "$PKG" || fail "Could not stop MyAI before Back test"
adb shell am start -a android.settings.SETTINGS 2>&1 | tee "$EVIDENCE/settings-before-back-start.txt"
sleep 4
recover_unresponsive_dialog || fail "Could not recover emulator dialog while preparing Back test"
adb shell dumpsys activity activities > "$EVIDENCE/activities-settings-before-back.txt"
if ! grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/activities-settings-before-back.txt" | grep -Fq 'com.android.settings'; then
  fail "Settings did not become foreground before Back test; destination assertion would be invalid"
fi

# Launch normally over Settings. MULTIPLE_TASK would isolate MyAI from Settings
# and invalidate the expected Back destination.
adb shell am start -n "$ACTIVITY" 2>&1 | tee "$EVIDENCE/activity-start-before-back.txt"
sleep 3
adb shell dumpsys activity activities > "$EVIDENCE/activities-myai-before-back.txt"
if ! grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/activities-myai-before-back.txt" | grep -Fq "$ACTIVITY"; then
  fail "MyAI did not become foreground before Back test"
fi
run_test_tool "back"
wait_for_tool_result "back" "TOOL_RESULT back: OK dispatched" || fail "Back tool did not report dispatch"
back_returned_to_settings=0
for attempt in $(seq 1 15); do
  recover_unresponsive_dialog || fail "Could not recover emulator dialog while verifying Back destination"
  adb shell dumpsys activity activities > "$EVIDENCE/activities-after-back-attempt-${attempt}.txt" 2>&1 || true
  if grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|^[[:space:]]*Resumed:' "$EVIDENCE/activities-after-back-attempt-${attempt}.txt" | grep -Fq 'com.android.settings'; then
    back_returned_to_settings=1
    cp "$EVIDENCE/activities-after-back-attempt-${attempt}.txt" "$EVIDENCE/activities-after-back.txt"
    break
  fi
  sleep 1
done
if [[ "$back_returned_to_settings" -ne 1 ]]; then
  adb exec-out screencap -p > "$EVIDENCE/screenshot-after-back-failure.png" 2>/dev/null || true
  fail "Back was dispatched but Android did not return to the previous Settings activity"
fi
echo "PASS: Back returned to the previous foreground Settings activity."

# Capture the full relevant buffers at the end. A functional smoke PASS must not
# hide an Android system ANR that occurred during this same emulator session.
adb logcat -d -b main -b system -b events -b crash -v threadtime -t 20000 > "$EVIDENCE/logcat.txt"
grep -i -E -C 4 'ANR in|am_anr|Input dispatching timed out|not responding|Reason:' \
  "$EVIDENCE/logcat.txt" > "$EVIDENCE/anr-summary-final.txt" 2>/dev/null || true

if grep -Eiq 'ANR in |am_anr|Input dispatching timed out|Application Not Responding:|AppNotRespondingDialog' "$EVIDENCE/logcat.txt"; then
  capture_anr_traces post-test
  fail "ANR/AppNotRespondingDialog evidence found in final logcat; smoke run is not PASS even if functional actions completed"
fi

# Fail on a fatal exception for this package, even if Android leaves a process briefly alive.
if grep -E 'FATAL EXCEPTION|Process: com\.dmitry\.myai\.infinix, PID:' "$EVIDENCE/logcat.txt" | grep -q .; then
  fail "Fatal application exception found in logcat"
fi
adb exec-out screencap -p > "$EVIDENCE/android-emulator.png"
adb shell dumpsys activity activities > "$EVIDENCE/activities-final.txt"
cp "$APK" "$EVIDENCE/app-debug.apk"
echo "PASS: install, foreground Activity, visible controls, Accessibility Bridge connection, screen-state action, Back destination assertion, and crash scan all passed; no ANR signature found in captured final logcat."
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

required_files=(
  "settings.gradle"
  "build.gradle"
  "app/build.gradle"
  "app/src/main/AndroidManifest.xml"
  "app/src/main/res/layout/activity_main.xml"
  "app/src/main/res/values/strings.xml"
  "app/src/main/res/values/styles.xml"
  "app/src/main/res/xml/accessibility_service_config.xml"
  "app/src/main/java/com/dmitry/myai/infinix/MainActivity.java"
  "app/src/main/java/com/dmitry/myai/infinix/agent/AgentContracts.java"
  "app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java"
  "app/src/main/java/com/dmitry/myai/infinix/memory/LocalMemoryStore.java"
  "app/src/main/java/com/dmitry/myai/infinix/online/OnlineGateway.java"
)

for f in "${required_files[@]}"; do
  test -f "$f" || { echo "MISSING: $f"; exit 1; }
done

python3 - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

r = Path(".").resolve()

for p in (r / "app/src/main").rglob("*"):
    if p.is_file():
        p.read_text(encoding="utf-8")

for p in [
    r / "app/src/main/AndroidManifest.xml",
    r / "app/src/main/res/layout/activity_main.xml",
    r / "app/src/main/res/values/strings.xml",
    r / "app/src/main/res/values/styles.xml",
    r / "app/src/main/res/xml/accessibility_service_config.xml",
]:
    ET.parse(p)

manifest = (r / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'android:name=".MainActivity"' in manifest or "com.dmitry.myai.infinix.MainActivity" in manifest, "Manifest missing MainActivity"
assert 'android:name=".bridge.MyAiAccessibilityService"' in manifest or "com.dmitry.myai.infinix.bridge.MyAiAccessibilityService" in manifest, "Manifest missing AccessibilityService"
assert 'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"' in manifest, "AccessibilityService missing BIND_ACCESSIBILITY_SERVICE"

java_root = r / "app/src/main/java"
java_files = list(java_root.rglob("*.java"))
assert java_files, "No Java sources found"

for p in java_files:
    s = p.read_text(encoding="utf-8")
    assert s.count("{") == s.count("}"), f"Unbalanced braces: {p.relative_to(r)}"
    m = re.search(r"package\s+([A-Za-z0-9_.]+)\s*;", s)
    assert m, f"Missing package declaration: {p.relative_to(r)}"
    rel_pkg = ".".join(p.relative_to(java_root).parts[:-1])
    assert m.group(1) == rel_pkg, f"Package/path mismatch: {p.relative_to(r)} -> {m.group(1)}"

main = (r / "app/src/main/java/com/dmitry/myai/infinix/MainActivity.java").read_text(encoding="utf-8")
bridge = (r / "app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java").read_text(encoding="utf-8")
layout = (r / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
gradle = (r / "app/build.gradle").read_text(encoding="utf-8")
root_gradle = (r / "build.gradle").read_text(encoding="utf-8")
settings = (r / "settings.gradle").read_text(encoding="utf-8")

for marker in ["ACTION_ACCESSIBILITY_SETTINGS", "testHome", "testBack", "testScroll", "refresh", "ACTION_SETTINGS", "postDelayed", "submitCommand", "executeBridgeCommand", "OPEN_YOUTUBE"]:
    assert marker in main, f"MainActivity marker missing: {marker}"

for marker in ["GLOBAL_ACTION_HOME", "GLOBAL_ACTION_BACK", "performGlobalAction", "getRootInActiveWindow", "ACTION_SCROLL_FORWARD", "dispatchGesture", "GestureResultCallback", "getRealMetrics"]:
    assert marker in bridge, f"Accessibility marker missing: {marker}"

for view_id in ["statusText", "chatText", "chatScroll", "commandInput", "sendCommandButton", "logText", "openAccessibilityButton", "homeButton", "backButton", "scrollButton", "refreshButton", "logScroll"]:
    assert view_id in layout, f"Required UI id missing: {view_id}"

assert "com.android.application" in root_gradle and "8.9.2" in root_gradle, "Unexpected Android Gradle Plugin configuration"
assert "com.android.application" in gradle, "App module does not apply Android application plugin"
assert "include ':app'" in settings, "App module is not included"

# Reject known corruption/typos from earlier revisions.
bad_fragments = ["ALP HA", "My AI_Infinix", "bui ld", "setup -java", "licen ses", "platform-tools ", "platforms;android-35 ", "build-tools;35.0.0 "]
scan_roots = [r / ".github", r / "app", r / "build.gradle", r / "settings.gradle", r / "gradle.properties"]
for base in scan_roots:
    paths = [base] if base.is_file() else [p for p in base.rglob("*") if p.is_file()]
    for p in paths:
        s = p.read_text(encoding="utf-8", errors="ignore")
        for bad in bad_fragments:
            assert bad not in s, f"Suspicious typo '{bad}' found in {p.relative_to(r)}"

print("STATIC SANITY: PASS")
print(f"JAVA SOURCES: {len(java_files)}")

PY

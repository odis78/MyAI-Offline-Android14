#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

test -f settings.gradle
test -f build.gradle
test -f app/build.gradle
test -f app/src/main/AndroidManifest.xml
test -f app/src/main/res/layout/activity_main.xml
test -f app/src/main/res/values/strings.xml
test -f app/src/main/res/values/styles.xml
test -f app/src/main/res/xml/accessibility_service_config.xml

python3 - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

r = Path(".").resolve()

expected = [
    r / "app/src/main/java/com/dmitry/myai/infinix/MainActivity.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/agent/AgentContracts.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/memory/LocalMemoryStore.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/online/OnlineGateway.java",
]
missing = [str(p.relative_to(r)) for p in expected if not p.is_file()]
if missing:
    raise SystemExit("Missing required source files: " + ", ".join(missing))

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
if not ("android:name=".MainActivity"" in manifest or "com.dmitry.myai.infinix.MainActivity" in manifest):
    raise SystemExit("Manifest missing MainActivity")
if not ("android:name=".bridge.MyAiAccessibilityService"" in manifest or "com.dmitry.myai.infinix.bridge.MyAiAccessibilityService" in manifest):
    raise SystemExit("Manifest missing AccessibilityService")
if 'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"' not in manifest:
    raise SystemExit("AccessibilityService missing BIND_ACCESSIBILITY_SERVICE protection")

java_files = list((r / "app/src/main/java").rglob("*.java"))
for p in java_files:
    text = p.read_text(encoding="utf-8")
    if text.count("{") != text.count("}"):
        raise SystemExit(f"Unbalanced braces: {p.relative_to(r)}")
    m = re.search(r"package\s+([A-Za-z0-9_.]+)\s*;", text)
    if not m:
        raise SystemExit(f"Missing package declaration: {p.relative_to(r)}")
    rel_pkg = ".".join(p.relative_to(r / "app/src/main/java").parts[:-1])
    if m.group(1) != rel_pkg:
        raise SystemExit(f"Package/path mismatch: {p.relative_to(r)} -> {m.group(1)}")

main = (r / "app/src/main/java/com/dmitry/myai/infinix/MainActivity.java").read_text(encoding="utf-8")
bridge = (r / "app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java").read_text(encoding="utf-8")
layout = (r / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

for marker in [
    "ACTION_ACCESSIBILITY_SETTINGS", "testHome", "testBack", "testScroll", "refresh",
    "ACTION_SETTINGS", "postDelayed"
]:
    if marker not in main:
        raise SystemExit(f"MainActivity marker missing: {marker}")

for marker in [
    "GLOBAL_ACTION_HOME", "GLOBAL_ACTION_BACK", "performGlobalAction",
    "getRootInActiveWindow", "ACTION_SCROLL_FORWARD", "dispatchGesture",
    "GestureResultCallback", "getRealMetrics"
]:
    if marker not in bridge:
        raise SystemExit(f"Accessibility marker missing: {marker}")

for view_id in [
    "statusText", "logText", "openAccessibilityButton", "homeButton",
    "backButton", "scrollButton", "refreshButton", "logScroll"
]:
    if view_id not in layout:
        raise SystemExit(f"Required UI id missing: {view_id}")

for bad in ["ALP HA", "My AI_Infinix", "bui ld", "setup -java", "licen ses", "platform-tools "]:
    for p in r.rglob("*"):
        if p.is_file() and ".git" not in p.parts and p.name != "sanity_check.sh" and ".github" not in p.parts:
            if bad in p.read_text(encoding="utf-8", errors="ignore"):
                raise SystemExit(f"Suspicious typo '{bad}' found in {p.relative_to(r)}")

print("STATIC SANITY: PASS")
PY

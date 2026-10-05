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
test -f app/src/main/res/xml/data_extraction_rules.xml
test -f app/src/main/res/drawable/ic_launcher_foreground.xml
test -f app/src/main/res/mipmap-anydpi/ic_launcher.xml
test -f app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
test -f app/src/main/res/values/colors.xml

python3 - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

r = Path(".").resolve()

expected = [
    r / "app/src/main/java/com/dmitry/myai/infinix/MainActivity.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/GatewayActivity.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/CommandParser.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/agent/AgentCommandParser.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/agent/AgentResultCodec.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/agent/AgentContracts.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/memory/LocalMemoryStore.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/online/OnlineGateway.java",
    r / "app/src/main/java/com/dmitry/myai/infinix/online/HttpOnlineGateway.java",
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
    r / "app/src/main/res/layout/activity_gateway.xml",
    r / "app/src/main/res/values/strings.xml",
    r / "app/src/main/res/values/styles.xml",
    r / "app/src/main/res/xml/accessibility_service_config.xml",
    r / "app/src/main/res/xml/data_extraction_rules.xml",
]:
    ET.parse(p)

manifest = (r / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if not ('android:name=".MainActivity"' in manifest or "com.dmitry.myai.infinix.MainActivity" in manifest):
    raise SystemExit("Manifest missing MainActivity")
if not ('android:name=".bridge.MyAiAccessibilityService"' in manifest or "com.dmitry.myai.infinix.bridge.MyAiAccessibilityService" in manifest):
    raise SystemExit("Manifest missing AccessibilityService")
if 'android:icon="@mipmap/ic_launcher"' not in manifest:
    raise SystemExit("Manifest missing launcher icon")
if 'android:roundIcon="@mipmap/ic_launcher_round"' not in manifest:
    raise SystemExit("Manifest missing round launcher icon")
if 'android:dataExtractionRules="@xml/data_extraction_rules"' not in manifest:
    raise SystemExit("Manifest missing data extraction rules")
if 'android:exported="false"' not in manifest or 'android:name=".GatewayActivity"' not in manifest:
    raise SystemExit("GatewayActivity must remain non-exported")
if 'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"' not in manifest:
    raise SystemExit("AccessibilityService missing BIND_ACCESSIBILITY_SERVICE protection")
if 'android:name="com.openai.chatgpt"' not in manifest:
    raise SystemExit("Manifest missing ChatGPT package visibility")

java_files = list((r / "app/src/main/java").rglob("*.java"))
for p in java_files:
    text = p.read_text(encoding="utf-8")
    # Java syntax/brace correctness is verified by Gradle/javac below.
    # Do not duplicate a Java parser here; comments and string literals may contain braces.
    m = re.search(r"package\s+([A-Za-z0-9_.]+)\s*;", text)
    if not m:
        raise SystemExit(f"Missing package declaration: {p.relative_to(r)}")
    rel_pkg = ".".join(p.relative_to(r / "app/src/main/java").parts[:-1])
    if m.group(1) != rel_pkg:
        raise SystemExit(f"Package/path mismatch: {p.relative_to(r)} -> {m.group(1)}")

main = (r / "app/src/main/java/com/dmitry/myai/infinix/MainActivity.java").read_text(encoding="utf-8")
parser = (r / "app/src/main/java/com/dmitry/myai/infinix/CommandParser.java").read_text(encoding="utf-8")
agent_parser = (r / "app/src/main/java/com/dmitry/myai/infinix/agent/AgentCommandParser.java").read_text(encoding="utf-8")
gateway = (r / "app/src/main/java/com/dmitry/myai/infinix/online/HttpOnlineGateway.java").read_text(encoding="utf-8")
bridge = (r / "app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java").read_text(encoding="utf-8")
layout = (r / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
gateway_activity = (r / "app/src/main/java/com/dmitry/myai/infinix/GatewayActivity.java").read_text(encoding="utf-8")
gateway_layout = (r / "app/src/main/res/layout/activity_gateway.xml").read_text(encoding="utf-8")

for marker in [
    "ACTION_ACCESSIBILITY_SETTINGS", "testHome", "testBack", "testScroll", "refresh",
    "ACTION_SETTINGS", "submitCommand", "executeBridgeCommand",
    "OPEN_YOUTUBE", "OPEN_CHATGPT", "com.openai.chatgpt", "findLaunchIntentByName", "executeAgentCommand", "AgentResultCodec"
]:
    if marker not in main:
        raise SystemExit(f"MainActivity marker missing: {marker}")

for marker in [
    "normalize", "stripPolitePrefix", "пожалуйста ", "можешь ", "extractApp"
]:
    if marker not in parser:
        raise SystemExit(f"CommandParser marker missing: {marker}")

for marker in ["fromNaturalLanguage", "fromModelJson", "open_app", "tool_call"]:
    if marker not in agent_parser:
        raise SystemExit(f"AgentCommandParser marker missing: {marker}")

for marker in ["HttpURLConnection", "setConnectTimeout", "setReadTimeout", "Content-Type", "session_id", "tool_call", "MAX_REQUEST_TEXT", "MAX_RESPONSE_BYTES", "getUserInfo", "getRef"]:
    if marker not in gateway:
        raise SystemExit(f"HttpOnlineGateway marker missing: {marker}")

all_source = "\n".join(
    p.read_text(encoding="utf-8", errors="ignore")
    for p in (r / "app/src/main").rglob("*") if p.is_file()
)
for forbidden in ["sk-", "OPENAI_API_KEY", "Authorization: Bearer", "api_key"]:
    if forbidden in all_source:
        raise SystemExit(f"Potential credential leakage marker found: {forbidden}")

for marker in [
    "GLOBAL_ACTION_HOME", "GLOBAL_ACTION_BACK", "performGlobalAction",
    "getRootInActiveWindow", "ACTION_SCROLL_FORWARD", "dispatchGesture",
    "GestureResultCallback", "getRealMetrics"
]:
    if marker not in bridge:
        raise SystemExit(f"Accessibility marker missing: {marker}")

accessibility_config = (r / "app/src/main/res/xml/accessibility_service_config.xml").read_text(encoding="utf-8")
if "typeViewTextChanged" in accessibility_config:
    raise SystemExit("Accessibility config contains unnecessary typeViewTextChanged event")

for view_id in [
    "statusText", "chatText", "chatScroll", "commandInput", "sendCommandButton", "gatewayButton",
    "chatgptButton", "logText", "logScroll", "openAccessibilityButton", "homeButton",
    "backButton", "scrollButton", "refreshButton", "logText"
]:
    if view_id not in layout:
        raise SystemExit(f"Required UI id missing: {view_id}")

checked = r / "app/build.gradle"
raw = checked.read_text(encoding="utf-8")
if r"\\n" in raw:
    raise SystemExit(f"Literal \\n sequence found where real line breaks are required: {checked}")

for bad in ["ALP HA", "My AI_Infinix", "bui ld", "setup -java", "licen ses", "platform-tools "]:
    for p in r.rglob("*"):
        if p.is_file() and ".git" not in p.parts and p.name != "sanity_check.sh" and ".github" not in p.parts:
            if bad in p.read_text(encoding="utf-8", errors="ignore"):
                raise SystemExit(f"Suspicious typo '{bad}' found in {p.relative_to(r)}")

if layout.count("<ScrollView") != 2:
    raise SystemExit("Main layout must contain exactly two ScrollViews: chat + journal")
if gateway_layout.count("<ScrollView") != 1:
    raise SystemExit("Gateway layout must contain exactly one ScrollView")
print("STATIC SANITY: PASS")
PY

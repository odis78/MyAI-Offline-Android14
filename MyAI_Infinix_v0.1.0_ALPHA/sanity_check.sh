#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "$0")" && pwd)"
PROJECT_NAME="MyAI_Infinix_v0.1.0_ALPHA"
APP_ID="com.dmitry.myai.infinix"
test "$(basename "$R")" = "$PROJECT_NAME"

required=(
  settings.gradle build.gradle gradle.properties app/build.gradle
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/dmitry/myai/infinix/MainActivity.java
  app/src/main/java/com/dmitry/myai/infinix/agent/AgentContracts.java
  app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java
  app/src/main/java/com/dmitry/myai/infinix/memory/LocalMemoryStore.java
  app/src/main/java/com/dmitry/myai/infinix/online/OnlineGateway.java
  app/src/main/java/com/dmitry/myai/infinix/tools/ToolCall.java
  app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java
  app/src/main/java/com/dmitry/myai/infinix/tools/ToolProtocol.java
  app/src/main/java/com/dmitry/myai/infinix/tools/ToolResult.java
  app/src/main/java/com/dmitry/myai/infinix/safety/PolicyGate.java
  app/src/main/java/com/dmitry/myai/infinix/verification/VerificationEngine.java
  app/src/main/res/layout/activity_main.xml
  app/src/main/res/values/strings.xml
  app/src/main/res/values/styles.xml
  app/src/main/res/xml/accessibility_service_config.xml
)
for f in "${required[@]}"; do test -f "$R/$f" || { echo "MISSING: $f"; exit 1; }; done

grep -Fq "rootProject.name='$PROJECT_NAME'" "$R/settings.gradle"
grep -Fq "applicationId '$APP_ID'" "$R/app/build.gradle"
grep -Fq "namespace '$APP_ID'" "$R/app/build.gradle"
grep -Fq "compileSdk 35" "$R/app/build.gradle"
grep -Fq "targetSdk 35" "$R/app/build.gradle"
grep -Fq "minSdk 26" "$R/app/build.gradle"
grep -Fq "sourceCompatibility JavaVersion.VERSION_17" "$R/app/build.gradle"
grep -Fq "targetCompatibility JavaVersion.VERSION_17" "$R/app/build.gradle"

MANIFEST="$R/app/src/main/AndroidManifest.xml"
SERVICE="$R/app/src/main/java/com/dmitry/myai/infinix/bridge/MyAiAccessibilityService.java"
CONFIG="$R/app/src/main/res/xml/accessibility_service_config.xml"
grep -Fq 'android:name=".bridge.MyAiAccessibilityService"' "$MANIFEST"
grep -Fq 'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"' "$MANIFEST"
grep -Fq 'android:name=".MainActivity"' "$MANIFEST"
grep -Fq 'android:canRetrieveWindowContent="true"' "$CONFIG"
grep -Fq 'android:canPerformGestures="true"' "$CONFIG"
grep -Fq 'GLOBAL_ACTION_BACK' "$SERVICE"
grep -Fq 'GLOBAL_ACTION_HOME' "$SERVICE"
grep -Fq 'dispatchGesture' "$SERVICE"

for id in status log accessibility testHome testBack testScroll testScreen testTool logScroll; do
  grep -Fq "@+id/$id" "$R/app/src/main/res/layout/activity_main.xml"
  grep -Fq "R.id.$id" "$R/app/src/main/java/com/dmitry/myai/infinix/MainActivity.java"
done

python3 - "$R" <<'PY'
import pathlib,sys,re,xml.etree.ElementTree as ET
r=pathlib.Path(sys.argv[1])
for p in [r/"app/src/main/AndroidManifest.xml",r/"app/src/main/res/xml/accessibility_service_config.xml",r/"app/src/main/res/values/strings.xml",r/"app/src/main/res/values/styles.xml",r/"app/src/main/res/layout/activity_main.xml"]:
    ET.parse(p)
for p in r.rglob("*.java"):
    s=p.read_text(encoding="utf-8")
    if s.count("{")!=s.count("}"): raise SystemExit(f"Unbalanced braces: {p}")
    if "\x00" in s: raise SystemExit(f"NUL byte: {p}")
    m=re.search(r"package\s+([A-Za-z0-9_.]+)\s*;",s)
    if not m: raise SystemExit(f"Missing package: {p}")
    rel=".".join(p.relative_to(r/"app/src/main/java").parts[:-1])
    if m.group(1)!=rel: raise SystemExit(f"Package/path mismatch: {p}")
for p in (r/"app/src/main").rglob("*"):
    if p.is_file():
        s=p.read_text(encoding="utf-8",errors="ignore")
        for pat in [r"sk-[A-Za-z0-9]{20,}",r"AIza[0-9A-Za-z_-]{30,}",r"ghp_[A-Za-z0-9]{20,}"]:
            if re.search(pat,s): raise SystemExit(f"Possible secret: {p}")
print("STATIC SANITY: PASS")
PY

grep -Fq 'class ToolProtocol' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolProtocol.java"
grep -Fq 'class ToolExecutor' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java"
grep -Fq 'class PolicyGate' "$R/app/src/main/java/com/dmitry/myai/infinix/safety/PolicyGate.java"
grep -Fq 'class VerificationEngine' "$R/app/src/main/java/com/dmitry/myai/infinix/verification/VerificationEngine.java"
grep -Fq 'get_screen_state' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java"
grep -Fq 'type_text' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java"
grep -Fq 'recents' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java"
grep -Fq 'scroll_down' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java"
grep -Fq 'scroll_down' "$R/app/src/main/java/com/dmitry/myai/infinix/safety/PolicyGate.java"
grep -Fq 'new JSONObject(json)' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolProtocol.java"
grep -Fq 'node.recycle()' "$R/app/src/main/java/com/dmitry/myai/infinix/tools/ToolExecutor.java"
grep -Fq 'dispatchJson' "$R/app/src/main/java/com/dmitry/myai/infinix/agent/AgentContracts.java"

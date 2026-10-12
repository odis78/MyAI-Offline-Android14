#!/usr/bin/env python3
"""Evidence-based Android smoke-test diagnostics; do not infer root cause from one signal."""
from pathlib import Path
import json, re, sys
from datetime import datetime, timezone

root = Path(sys.argv[1] if len(sys.argv) > 1 else "test-bridge/android-evidence")
root.mkdir(parents=True, exist_ok=True)
# Exclude generated reports so reruns do not cite their own previous output.
GENERATED_REPORTS = {"diagnosis.md", "diagnosis.json"}
files = sorted(p for p in root.iterdir() if p.is_file() and p.name not in GENERATED_REPORTS)
text_by_file = {}
for p in files:
    if p.suffix.lower() in {".txt", ".log", ".xml", ".md"}:
        try:
            text_by_file[p.name] = p.read_text(encoding="utf-8", errors="replace")
        except OSError:
            pass

findings = []
def collect(rx, name_pred=None, limit=12):
    matches = []
    for name, body in text_by_file.items():
        if name_pred is not None and not name_pred(name):
            continue
        for line_no, line in enumerate(body.splitlines(), 1):
            if rx.search(line):
                matches.append({"file": name, "line": line_no, "evidence": line.strip()[:400]})
                if len(matches) >= limit:
                    return matches
    return matches

def add(code, summary, next_step, evidence):
    if evidence:
        findings.append({"code": code, "summary": summary,
                         "recommended_next_step": next_step, "evidence": evidence})

add("APP_ANR", "Есть признак ANR процесса MyAI.",
    "Откройте ANR trace и проверьте main thread, блокирующие I/O и долгие операции UI-потока.",
    collect(re.compile(r"ANR in .*com\.dmitry\.myai\.infinix|Input dispatching timed out.*com\.dmitry\.myai\.infinix", re.I)))
add("JAVA_CRASH", "В логах есть признак падения приложения.",
    "Найдите FATAL EXCEPTION, тип исключения и первую строку стека из пакета приложения.",
    collect(re.compile(r"FATAL EXCEPTION.*com\.dmitry\.myai\.infinix|com\.dmitry\.myai\.infinix.*Fatal signal", re.I)))
add("ACCESSIBILITY_BINDING", "Есть признаки ошибки подключения или падения Accessibility.",
    "Сверьте enabled_accessibility_services, dumpsys accessibility и Permission Denial; успешное SERVICE CONNECTED само по себе не является ошибкой.",
    collect(re.compile(r"Permission Denial.*[Aa]ccessibility|Crashed services:\{[^}\n]*[Mm]y[Aa][Ii]|AccessibilityService.*(died|crash)", re.I)))
system_anr = collect(re.compile(r"ANR in (?:system_server|com\.android\.(?:systemui|settings|phone|bluetooth|permissioncontroller)|android\.ext\.services)\b|Application Not Responding: (?:system|System UI|Settings)", re.I))
if system_anr:
    pre = [m for m in system_anr if "pre-accessibility" in m["file"] or "clean-baseline" in m["file"]]
    summary = "Обнаружен системный ANR; это само по себе не доказывает ошибку MyAI. "
    if pre:
        summary += "Такая сигнатура присутствует до включения Accessibility или в чистом baseline. "
    summary += "Причину (например, перегрузку CPU) нужно подтвердить метриками и сравнением с чистым AVD."
    add("EMULATOR_SYSTEM_ANR", summary,
        "Сопоставьте время с baseline без MyAI, сохраните ANR trace и метрики эмулятора. Не меняйте код приложения без стека, указывающего на MyAI.",
        system_anr)

status_disconnected = re.compile(r'text="Control Bridge: НЕ ПОДКЛЮЧЕН"[^>]*resource-id="[^"]*:id/status"|resource-id="[^"]*:id/status"[^>]*text="Control Bridge: НЕ ПОДКЛЮЧЕН"')
stale = []
for name, body in text_by_file.items():
    if name.startswith("window") and name.endswith(".xml"):
        m = status_disconnected.search(body)
        if m:
            stale.append({"file": name, "line": body[:m.start()].count("\\n") + 1, "evidence": m.group(0)[:400]})
service_connected = any("SERVICE CONNECTED" in body or "SERVICE REBOUND" in body for body in text_by_file.values())
# UiAutomator registers UiTestAutomationService and temporarily takes over the
# accessibility framework. A window.xml captured during that interval can show
# a stale "disconnected" label and generate expected UNBOUND/REBOUND log pairs.
ui_automation = collect(re.compile(r"Registering UiTestAutomationService"), name_pred=lambda n: "logcat" in n.lower())
if stale and service_connected:
    if ui_automation:
        add("TEST_HARNESS_ACCESSIBILITY_TAKEOVER",
            "Статус «НЕ ПОДКЛЮЧЕН» снят в window.xml, но в тех же логах есть регистрация UiTestAutomationService. UIAutomator временно забирает Accessibility и может вызвать именно такое состояние; этот снимок не доказывает ошибку ConnectionListener.",
            "Не менять код приложения по этому снимку. Проверять подключение по dumpsys accessibility после завершения UIAutomator и искать статусную регрессию только в снимке, сделанном вне takeover.",
            ui_automation[:6])
    else:
        add("BRIDGE_STATUS_STALE", "Сервис подключался, но видимый узел статуса остался «НЕ ПОДКЛЮЧЕН»; в логах не найдено явного захвата Accessibility тестовым UiAutomator.",
            "Сопоставьте время снимка window.xml с ConnectionListener, onResume и состоянием Bound services. Не меняйте код без подтверждения, что снимок сделан вне UiAutomator takeover.",
            stale)

rebind_body = "\\n".join(body for name, body in text_by_file.items() if "logcat" in name.lower())
rebind_count = len(re.findall(r"SERVICE REBOUND|SERVICE CONNECTED", rebind_body))
if rebind_count >= 5:
    rebind_evidence = collect(re.compile(r"SERVICE REBOUND|SERVICE CONNECTED|SERVICE UNBOUND|SERVICE DESTROYED"), name_pred=lambda n: "logcat" in n.lower())
    if ui_automation:
        add("TEST_HARNESS_ACCESSIBILITY_REBIND",
            f"Обнаружено {rebind_count} подключений/переподключений при наличии событий UiTestAutomationService. Это совместимо с временным захватом Accessibility тестовым UIAutomator и само по себе не доказывает нестабильность службы MyAI.",
            "Сопоставлять UNBOUND/DESTROYED с временными метками UiTestAutomationService. Подозревать код MyAI только если отключение воспроизводится вне тестового захвата или сопровождается стеком/крашем MyAI.",
            ui_automation[:6] + rebind_evidence[:6])
    else:
        add("ACCESSIBILITY_REBIND", f"Обнаружено {rebind_count} событий подключения/переподключения в собранных логах.",
            "Сверьте временные метки SERVICE UNBOUND / SERVICE DESTROYED и системные ANR. Частые события не доказывают краш без дополнительных признаков.",
            rebind_evidence)

add("UI_ASSERTION", "Smoke-тест не нашёл ожидаемый UI-элемент.",
    "Сохраните window.xml и screenshot; проверьте resource-id, видимость, прокрутку и наличие системного диалога.",
    collect(re.compile(r"Expected UI element/text not found|MainActivity is not the resumed|Control not found|Control Bridge status stayed disconnected|UI hierarchy dump", re.I)))
add("BUILD_FAILURE", "Есть признак ошибки сборки/компиляции.",
    "Используйте первую ошибку Gradle/компилятора выше итогового BUILD FAILED.",
    collect(re.compile(r"BUILD FAILED|Compilation failed|Manifest merger failed|Could not resolve", re.I)))

anr_files = [p.name for p in files if any(k in p.name.lower() for k in ("lastanr", "anr-summary", "processes-anr", "logcat-anr", "windows-anr"))]
if not anr_files:
    findings.append({"code": "MISSING_ANR_DIAGNOSTICS",
        "summary": "В evidence не найдено ожидаемых ANR-диагностических файлов.",
        "recommended_next_step": "Проверьте capture_failure_evidence и содержимое артефакта."})
if not findings:
    findings.append({"code": "NO_MATCHING_SIGNATURE",
        "summary": "Известные сигнатуры не обнаружены; это не означает, что ошибок нет.",
        "recommended_next_step": "Проверьте screenshot, window.xml и полные логи."})

report = {"generated_utc": datetime.now(timezone.utc).isoformat(),
          "evidence_directory": str(root), "files_seen": [p.name for p in files],
          "diagnostics": findings,
          "caveat": "Автоматический анализ находит сигнатуры, но не подтверждает первопричину без trace и воспроизводимого теста."}
(root / "diagnosis.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
lines = ["# Android smoke-test diagnosis", "", f"Generated: {report['generated_utc']}", ""]
for item in findings:
    lines += [f"## {item['code']}", "", item["summary"], "",
              f"**Next:** {item['recommended_next_step']}", ""]
    for ev in item.get("evidence", []):
        lines.append(f"- `{ev['file']}:{ev['line']}` — {ev['evidence']}")
    lines.append("")
(root / "diagnosis.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

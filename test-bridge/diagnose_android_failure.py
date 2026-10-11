#!/usr/bin/env python3
"""Analyze Android smoke-test evidence and produce a ranked, evidence-backed diagnosis.

This tool reports signals and next checks; it does not claim root cause without a
matching ANR trace or stack. It is intentionally stdlib-only for CI portability.
"""
from pathlib import Path
import json, re, sys
from datetime import datetime, timezone

root = Path(sys.argv[1] if len(sys.argv) > 1 else "test-bridge/android-evidence")
root.mkdir(parents=True, exist_ok=True)
patterns = [
    ("APP_ANR", re.compile(r"ANR in .*com\.dmitry\.myai\.infinix|Process: com\.dmitry\.myai\.infinix.*Reason:|Input dispatching timed out.*com\.dmitry\.myai\.infinix", re.I),
     "Есть признак ANR процесса MyAI.", "Откройте lastanr и ANR trace; проверьте main thread, блокирующие I/O, синхронные ожидания и долгие операции в UI-потоке."),
    ("SYSTEM_SERVER_ANR", re.compile(r"ANR in system_server|Application Not Responding: system|system_server.*Input dispatching timed out", re.I),
     "Есть признак ANR system_server.", "Сопоставьте lastanr, logcat и нагрузку эмулятора; проверьте, воспроизводится ли проблема на чистом API 34 AVD и на реальном устройстве."),
    ("PRE_ACCESSIBILITY_SYSTEM_ANR", re.compile(r"System/UI ANR signature detected before Accessibility enablement|ANR in (com\.android\.systemui|com\.android\.settings|system_server)|Application Not Responding: (system|System UI|Settings)", re.I),
     "Системный ANR зарегистрирован в базовом этапе до включения Accessibility либо найден в ANR evidence. Это не подтверждает вину MyAI.", "Сначала сравните logcat-pre-accessibility.txt и lastanr-pre-accessibility.txt с логами после включения сервиса. Если ANR уже есть до включения сервиса, отдельно воспроизведите чистый запуск эмулятора без MyAI Accessibility."),
    ("SYSTEM_UI_OR_SETTINGS_ANR", re.compile(r"ANR in (com\.android\.systemui|com\.android\.settings)|Application Not Responding: (System UI|Settings)", re.I),
     "Есть признак ANR System UI или Settings.", "Сопоставьте время ANR с baseline до включения Accessibility и последующими логами; не приписывайте его MyAI без стека приложения."),
    ("JAVA_CRASH", re.compile(r"FATAL EXCEPTION.*com\.dmitry\.myai\.infinix|Process: com\.dmitry\.myai\.infinix.*Exception|com\.dmitry\.myai\.infinix.*Fatal signal", re.I),
     "В логах есть возможное падение приложения.", "Найдите ближайший FATAL EXCEPTION, тип исключения, stack trace и первую строку из пакета приложения; исправляйте первопричину, а не последующие строки."),
    ("ACCESSIBILITY_BINDING", re.compile(r"AccessibilityService.*(crash|exception|bind)|Permission Denial.*accessibility|SERVICE CONNECTED", re.I),
     "Есть сигналы подключения/ошибки Accessibility.", "Сверьте enabled_accessibility_services, accessibility_enabled и dumpsys accessibility; проверьте manifest, service config и исключения сервиса."),
    ("EMULATOR_BOOT_OR_QEMU", re.compile(r"emulator.*(not responding|failed|crash)|qemu-system.*(error|failed)|virtio: zero sized buffers", re.I),
     "Есть сообщение от эмулятора/QEMU; само по себе оно не доказывает причину ANR.", "Сравните повторный запуск на чистом AVD, сохраните версию emulator/system image и отделите ошибки QEMU от ANR приложения."),
    ("UI_ASSERTION", re.compile(r"Expected UI element/text not found|MainActivity is not the resumed|Control not found|UI hierarchy dump", re.I),
     "Smoke-тест не нашёл ожидаемый UI-элемент.", "Сохраните window.xml и screenshot; проверьте resource-id, видимость/прокрутку контейнера, локаль и фактическую Activity."),
    ("BUILD_FAILURE", re.compile(r"BUILD FAILED|Compilation failed|Could not resolve|Manifest merger failed|error: ", re.I),
     "Есть признак ошибки сборки/компиляции.", "Используйте первую ошибку Gradle/компилятора выше итогового BUILD FAILED; проверьте конфликт зависимостей, manifest и несовместимость SDK."),
]
files = sorted(p for p in root.iterdir() if p.is_file())
text_by_file = {}
for p in files:
    if p.suffix.lower() in {".txt", ".log", ".xml", ".md"}:
        try:
            text_by_file[p.name] = p.read_text(encoding="utf-8", errors="replace")
        except OSError:
            pass
combined = "\n".join(f"--- FILE: {name} ---\n{body}" for name, body in text_by_file.items())
findings = []
for code, rx, summary, next_step in patterns:
    matches = []
    for name, body in text_by_file.items():
        for line_no, line in enumerate(body.splitlines(), 1):
            if rx.search(line):
                matches.append({"file": name, "line": line_no, "evidence": line.strip()[:400]})
                if len(matches) >= 12:
                    break
        if len(matches) >= 12:
            break
    if matches:
        findings.append({"code": code, "summary": summary, "recommended_next_step": next_step, "evidence": matches})
anr_files = [p.name for p in files if any(k in p.name.lower() for k in ("lastanr", "anr-summary", "processes-anr", "logcat-anr", "windows-anr"))]
if not anr_files:
    findings.append({
        "code": "MISSING_ANR_DIAGNOSTICS",
        "summary": "В evidence не найдено ожидаемых ANR-диагностических файлов.",
        "recommended_next_step": "Проверьте, что capture_failure_evidence вызывается до завершения сценария и что артефакт содержит test-bridge/android-evidence/."
    })
if not findings:
    findings.append({
        "code": "NO_MATCHING_SIGNATURE",
        "summary": "Известные сигнатуры не обнаружены в доступных текстовых логах; это не означает, что ошибок нет.",
        "recommended_next_step": "Проверьте screenshot, window.xml и полные логи; добавьте точную сигнатуру после установления фактической причины."
    })
report = {
    "generated_utc": datetime.now(timezone.utc).isoformat(),
    "evidence_directory": str(root),
    "files_seen": [p.name for p in files],
    "diagnostics": findings,
    "caveat": "Автоматический анализ выявляет сигнатуры и предлагает проверки. Причина считается подтверждённой только после проверки исходного stack trace/ANR reason и воспроизводимого теста."
}
(root / "diagnosis.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
lines = ["# MyAI Android test — диагностический отчёт", "", f"Создано (UTC): {report['generated_utc']}", "", f"Файлов evidence: {len(files)}", "", "## Найденные признаки", ""]
for item in findings:
    lines += [f"### {item['code']}", item["summary"], "", "**Следующий шаг:** " + item["recommended_next_step"], ""]
    for ev in item.get("evidence", []):
        lines.append(f"- `{ev['file']}:{ev['line']}` — {ev['evidence']}")
    lines.append("")
lines += ["## Файлы, просмотренные анализатором", ""]
lines += [f"- `{p.name}`" for p in files]
lines += ["", "> Важно: это ранжирование диагностических гипотез, а не автоматическое доказательство первопричины. Не отключайте проверки и не меняйте код приложения только по одному совпадению.", ""]
(root / "diagnosis.md").write_text("\n".join(lines), encoding="utf-8")
print(f"Diagnostic report written: {root / 'diagnosis.md'}")
print(f"JSON report written: {root / 'diagnosis.json'}")
print(f"Evidence files scanned: {len(files)}; findings: {len(findings)}")

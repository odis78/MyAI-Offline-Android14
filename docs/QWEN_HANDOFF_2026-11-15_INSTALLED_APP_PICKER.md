# Qwen handoff — MyAI Infinix installed-app picker
Date: 2026-11-15
Status: implementation committed to an isolated feature branch; CI result and physical-device behavior still pending.

## User request and objective
Continue real development and provide complete technical context for Qwen. The immediate feature is a user-visible launcher-app picker in MyAI Infinix. This addresses the existing limitation where the chat command "открой галерею" cannot reliably guess the vendor gallery package.

## Repository isolation
- Repository: odis78/MyAI-Offline-Android14
- Feature branch: feature/infinix-installed-app-picker
- Base branch: rebuild-run104-signed-apk
- Exact base commit: 683314a0681a8ab36447b20113e6393ba6b042b3
- App package: com.dmitry.myai.infinix
- Target device: Infinix X678B, Android 14, ARM64
- Do not merge this feature branch into the stable branch until CI and device checks pass.
- Do not touch Realme, REA Android, Termux REA, Tunnel Client, or REA-Termux-Bridge.

## Implemented behavior
A new "Приложения" button opens an Android dialog containing apps that expose a launcher activity (MAIN + LAUNCHER). Chat phrases "открой галерею", "открой фото", and "открой фотографии" now open the same picker rather than guessing a package name. The list is sorted alphabetically by visible app label. Query uses flags 0 rather than MATCH_DEFAULT_ONLY because launcher activities need not declare CATEGORY_DEFAULT. The user explicitly selects one app. MyAI then dispatches the existing open_app tool with that app's package name through ToolAgent -> ToolExecutor -> PackageManager.getLaunchIntentForPackage. Result is written to chat and diagnostic log.

This is a user-selected launch, not automatic app guessing and not background automation.

## Package visibility and permissions
AndroidManifest already declares a package-visibility query for:
- android.intent.action.MAIN
- android.intent.category.LAUNCHER

No QUERY_ALL_PACKAGES permission was added. The feature enumerates only launcher-visible applications. The app excludes its own package from the picker. It uses the existing open_app tool and does not add a new ToolProtocol operation, shell, ADB, network endpoint, or Accessibility action.

## Files changed
1. MyAI_Infinix_v0.1.0_ALPHA/app/src/main/res/layout/activity_main.xml
   - Replaced the full-width Accessibility button with a horizontal row.
   - Added appPicker button with label "Приложения".
2. MyAI_Infinix_v0.1.0_ALPHA/app/src/main/java/com/dmitry/myai/infinix/MainActivity.java
   - Added AlertDialog, PackageManager, ResolveInfo imports.
   - Added appPicker click handler.
   - Added showInstalledApps(): query MAIN/LAUNCHER, exclude own package, sort labels, show selection dialog.
   - Added launchSelectedApp(): dispatch existing open_app through the single-thread command executor; publish result.
   - JSON package argument uses JSONObject.quote to avoid unchecked/checked JSON construction issue.
   - Gallery/photo command phrases route to the picker; startup help mentions the new button and command.
   - Launcher query uses `queryIntentActivities(intent, 0)` to avoid filtering out launcher activities that lack CATEGORY_DEFAULT (follow-up commit `20d4c9cca95c40c6a9b54e8df8ac14f4fe8830b1`).
3. MyAI_Infinix_v0.1.0_ALPHA/sanity_check.sh
   - Checks appPicker ID and handler.
   - Checks launcher query and manifest MAIN/LAUNCHER declarations.
   - Fails if broad QUERY_ALL_PACKAGES permission is introduced.
4. .github/workflows/build-myai-infinix-v0.1.0-alpha.yml
   - Added this feature branch to push triggers so its own CI can run.
5. docs/QWEN_HANDOFF_2026-11-15_INSTALLED_APP_PICKER.md
   - This handoff record.

## Commit sequence on feature branch
- 82e140087bd8eca04ca0ab8fefdf70d0fca402ad — add picker entry point to layout.
- e573602eeef3fe2eb4e5e11ab5d7b08bf45f7b87 — implement launcher app picker and selected launch.
- 18ca729a59e45b2fe1d46708bca11053cd6b5816 — fix JSON construction to use JSONObject.quote.
- 29b621b415cab1478372137f625f6f4ea3e00801 — add feature branch to CI push trigger.
- 4eb9f8dcd7ec53f15fe038f2039f2a0495644f88 — extend static sanity checks.

## Verification status
Source-level review performed:
- Confirmed PackageManager query uses MAIN + LAUNCHER.
- Confirmed manifest already declares matching <queries> intent.
- Confirmed no QUERY_ALL_PACKAGES permission is introduced.
- Confirmed selected package goes through existing open_app tool and PolicyGate.
- Confirmed open_app is already allowed by existing NORMAL policy and ToolExecutor.
- Confirmed no new runtime permission, service, receiver, background task, or network access was added.

Not yet verified:
- GitHub Actions run for this feature branch.
- Gradle compile/test/lint.
- APK signing fingerprint and artifact SHA-256.
- Installation/update compatibility on physical Infinix X678B.
- Whether vendor-specific launcher entries (e.g. Gallery / Photos) appear as expected on the actual device.
- Behavior when selected app is disabled, removed between listing and launch, or has no launch intent.

Do not claim these checks passed until the workflow and device observations are available.

## CI expectations
Workflow: .github/workflows/build-myai-infinix-v0.1.0-alpha.yml
The workflow checks static sanity, Gradle tests, lintRelease, assembles release APK, verifies:
- applicationId com.dmitry.myai.infinix
- versionCode = 100 + GitHub Actions run number
- versionName = 0.1.<run number>-INFINIX-ALPHA
- permanent certificate SHA-256:
  19:E7:DD:5A:EB:7A:23:52:69:B8:F6:F4:33:F6:8F:1B:E0:F3:C3:BC:1B:14:5B:C2:1F:DB:A5:26:EB:1E:CB:64

Expected artifact includes APK, APK .sha256, and lint HTML report. If the workflow fails, inspect the exact failing step and fix only the feature branch.

## Next actions
1. Confirm final branch HEAD and inspect diff against base.
2. Wait for and inspect GitHub Actions result. Do not call an APK ready before successful artifact and signature verification.
3. If CI passes, download artifact and independently verify APK package, version, certificate, file size and SHA-256.
4. Give user the APK only after exact artifact file is available and verified.
5. User installs manually; do not install remotely.
6. Ask user to tap "Приложения", verify the gallery/photos app is listed, select it, and confirm launch. Also verify another common app and cancellation.
7. Record results, then plan next feature. Do not merge to stable automatically.

## Safety and project rules
- Keep feature branch isolated.
- Never change stable Infinix baseline APK #102/#104/#105.
- Never mix MyAI Infinix and Realme projects.
- Never alter REA Android or the working Termux/Tunnel/Bridge stack as part of this feature.
- No uninstall, app-data deletion, or installation by assistant.
- No broad package visibility permission.
- No shell or arbitrary command execution.
- Keep user confirmation explicit for any phone state-changing action.
- Report all failed and pending tests honestly.

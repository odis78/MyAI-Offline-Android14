# MyAI Test Bridge v0.1

Independent test harness for the Infinix branch. This branch is based on `infinix` but does not change or merge into that branch.

## Browser simulator

Requirements: Node.js 20+.

```bash
cd test-bridge
npm install
npx playwright install chromium
npm test
```

The deterministic simulator fixture is in `simulator/index.html`. Playwright produces an HTML report in `playwright-report/`, JSON results in `test-results/results.json` on CI, plus screenshots, video, and traces for failures.

To test a separately hosted simulator instead, set `MYAI_SIMULATOR_URL` to its URL before running `npm test`. The external page must expose the same accessible controls/IDs used by the tests.

## Android APK smoke test

The workflow separately builds a debug APK from `MyAI_Infinix_v0.1.0_ALPHA`, launches it on an Android emulator, and uploads the screenshot and logcat. This checks that the APK builds, installs, and starts; it does **not** prove Accessibility gestures or app-to-app commands work correctly. Those require explicit UI Automator tests and eventually a physical Infinix Note 30 Pro run.

## Evidence policy

A test is only marked passed when the runner reports success. Simulator checks and Android APK checks are reported separately. A successful browser test is not evidence that Android behavior works.

## Artifacts

- Playwright HTML report
- Playwright JSON result
- Failure screenshot/video/trace
- Android emulator screenshot and logcat
- Debug APK used by the emulator smoke test

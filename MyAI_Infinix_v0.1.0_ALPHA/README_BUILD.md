# MyAI Infinix v0.1.0 ALPHA — FIXED

Target: Infinix Note 30 Pro / Android 14.
Project directory name is intentionally exact:
`MyAI_Infinix_v0.1.0_ALPHA`

Application ID:
`com.dmitry.myai.infinix`

This package is the corrected source branch. The GitHub Actions workflow uses the same exact project directory name.

Before installation, run static checks, unit-test task, lint task, debug APK build, manifest/package/version verification, APK signature verification, SHA-256 generation, and APK size warning.

The home-server extension point remains architectural only; no home-server dependency is introduced in this ALPHA build.

## Build hardening
- Android AccessibilityService is explicitly exported for system binding and protected by `BIND_ACCESSIBILITY_SERVICE`.
- App Java source/target compatibility is pinned to Java 17 to match the CI JDK.

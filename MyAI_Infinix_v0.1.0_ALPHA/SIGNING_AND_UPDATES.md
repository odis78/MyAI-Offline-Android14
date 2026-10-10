# MyAI Infinix permanent signing and updates

The Infinix branch now produces a **release APK signed with one permanent certificate**. Future APKs can be installed over the previous MyAI installation as long as the package name remains `com.dmitry.myai.infinix` and the versionCode increases.

## One-time migration

The APK currently installed from the old Run #19 was signed with the old CI/debug certificate. It cannot be upgraded to the new permanent signing certificate. Therefore, **only once**, uninstall the old Run #19 APK and install the first permanently signed APK.

After that, do not uninstall MyAI for normal updates.

## GitHub Actions secrets

The workflow expects these repository Actions secrets:

- `MYAI_INFINIX_KEYSTORE_B64`
- `MYAI_INFINIX_KEYSTORE_PASSWORD`
- `MYAI_INFINIX_KEY_ALIAS`
- `MYAI_INFINIX_KEY_PASSWORD`

The keystore itself must never be committed to the repository.

The permanent certificate SHA-256 fingerprint is:

`19:E7:DD:5A:EB:7A:23:52:69:B8:F6:F4:33:F6:8F:1B:E0:F3:C3:BC:1B:14:5B:C2:1F:DB:A5:26:EB:1E:CB:64`

The workflow refuses to publish an APK signed by a different certificate.

## Versioning

CI automatically calculates:

- versionCode = 100 + GitHub Actions run number
- versionName = `0.1.<run number>-INFINIX-ALPHA`

This guarantees increasing versionCode values for normal workflow runs without manually editing `app/build.gradle`.

## Security

The private signing key is intentionally kept outside Git history. Android's documentation recommends protecting the keystore/private key and not putting passwords directly into the build file.


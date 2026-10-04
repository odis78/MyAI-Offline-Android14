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

`FD:5D:81:CA:CB:DB:A2:5E:96:C6:E6:1B:CE:5A:C4:0C:BD:68:0B:E6:85:19:26:64:19:45:3B:CA:C5:9C:B9:06`

The workflow refuses to publish an APK signed by a different certificate.

## Versioning

CI automatically calculates:

- versionCode = 100 + GitHub Actions run number
- versionName = `0.1.<run number>-INFINIX-ALPHA`

This guarantees increasing versionCode values for normal workflow runs without manually editing `app/build.gradle`.

## Security

The private signing key is intentionally kept outside Git history. Android's documentation recommends protecting the keystore/private key and not putting passwords directly into the build file.


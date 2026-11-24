# DocScan Signing Keystore

This keystore signs release APKs so they can be installed directly on Android devices without certificate errors.

- **Keystore File:** `release.jks`
- **Keystore Password:** `docscan123`
- **Key Alias:** `docscan`
- **Key Password:** `docscan123`

Environment variables `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` can be set to override these defaults in custom CI/CD pipelines.

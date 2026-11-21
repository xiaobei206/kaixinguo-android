# Android Release signing

## Automatic signing

Release APKs are signed by GitHub Actions with the fixed `kaixinguo-release.jks` key stored in this repository root. No GitHub CLI, local build software, or Actions Secrets setup is needed. Every release build uses the same keystore and runs signature verification on all four APK variants.

## Important trade-off: this key is public

At the repository owner's explicit request, this simple personal-use app keeps the keystore and its password file in the public repository so CI can sign releases without extra setup. **This means the signing key is not private. Anyone can download it and create an APK with the same signing certificate. The signature can no longer prove that an APK was produced by the repository owner.** A third party would still need to get their APK installed on a device to replace the app, but anyone relying on this app should understand the reduced supply-chain protection. Do not reuse this key for sensitive apps or apps that handle accounts, payments, or private data.

## Key identity

- Keystore file: `kaixinguo-release.jks`
- Credential file: `signing-secrets.txt`
- Alias: `kaixinguo-release`
- Certificate SHA-256 fingerprint: `FB:AF:97:3E:63:79:1B:A2:59:FA:B5:B4:EB:D4:96:6A:05:84:DB:F5:43:4E:6E:EE:25:E3:51:BF:57:5A:92:34`
- Certificate validity: 2026-11-21 through 2054-04-08

Keep a separate copy of `kaixinguo-android-signing-backup.zip` offline. The public repository copy is convenient for build automation, but do not rely on GitHub being the only copy of the key.

## Existing installs

Android allows an in-place update only when the new APK's application ID and signing certificate match the installed app. The v0.4.7 APKs were produced before this fixed key was configured; they may have a different certificate. If the phone rejects the first update because of a signature mismatch, install the new release after uninstalling the old app only after considering that uninstalling can erase local app data. Subsequent releases built by this workflow will use the fixed certificate.

## Build checks

CI verifies that every generated APK has a valid signature, all ABI variants use the same signing certificate, and the certificate matches the fixed keystore. A release is published only after these checks pass.

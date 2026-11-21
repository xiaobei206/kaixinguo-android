# Release APK signing

The repository's GitHub Actions workflow uses a **persistent release key** when these repository Actions secrets exist:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow verifies APK signatures and confirms that all ABI APKs use the same certificate. If the secrets are absent, CI may still build debug-signed APKs for testing, but it will **not publish a GitHub Release**. Never use a debug-signed artifact as the stable upgrade package.

## One-time setup on Windows

1. Keep the private signing-backup bundle somewhere private. Extract `kaixinguo-release.jks` and `signing-secrets.txt` into the repository root. Both are excluded from Git by `.gitignore`.
2. Install [GitHub CLI](https://cli.github.com/) if needed, then run `gh auth login` with an account that can write repository Actions secrets.
3. From the repository root, run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\setup-release-signing-secrets.ps1
   ```

4. In **Settings → Secrets and variables → Actions**, confirm that the four secret names exist. Their values are hidden by GitHub.
5. Trigger a workflow run or push a subsequent version change. The Release job will publish only when the persistent signing configuration is available and signature checks pass.

The script reads the local private files, uploads the values as GitHub Actions secrets, and does not print secret values. If setting secrets manually, the Base64 value for `ANDROID_KEYSTORE_BASE64` is produced in PowerShell by:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.\kaixinguo-release.jks'))
```

## Key backup and recovery

- **Never put the private keystore or its passwords in this public repository.** Anyone who obtains both could sign an APK that Android treats as an update to this app.
- Keep at least one offline copy of the private backup bundle. GitHub Actions secrets are configuration, not a recoverable backup of the original files.
- Keystore alias: `kaixinguo-release`.
- Certificate SHA-256 fingerprint: `FB:AF:97:3E:63:79:1B:A2:59:FA:B5:B4:EB:D4:96:6A:05:84:DB:F5:43:4E:6E:EE:25:E3:51:BF:57:5A:92:34`.

## Existing installs

The v0.4.7 release was built before this persistent key was configured. Android permits an in-place update only when the new APK's signing certificate matches the installed app. If v0.4.7's certificate differs from this key, a one-time uninstall/reinstall may be necessary; later releases will use the persistent key and can update in place. Uninstalling can clear local app data, so do not do it until necessary.

# 07 — Build & Deploy

From source code on the PC to a working journal on an Android phone. Do this once; repeat only when a new version exists.

## Prerequisites

- Doc 03 completed (JDK + Android SDK installed and verified)
- PowerShell is open at the repository root

## Step 1 — Create the signing keystore (once, ever)

Android requires every APK to be signed. The keystore is the private key that says "this update came from me." Same key = phones accept updates over the old install.

```powershell
$securePassword = Read-Host "Choose LifeNote signing password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $releasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
      -keystore "app\keystore.jks" `
      -alias lifenote -keyalg RSA -keysize 2048 -validity 10950 `
      -storepass $releasePassword -keypass $releasePassword `
      -dname "CN=LifeNote"
} finally {
    $releasePassword = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
```

- Validity **10950 days = 30 years** — outlives the project
- ⚠️ **Back up `keystore.jks` + the password immediately** (doc 08 explains why)

## Step 2 — Build

```powershell
$securePassword = Read-Host "LifeNote signing password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:LIFENOTE_KEYSTORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Release build failed." }
    Copy-Item app\build\outputs\apk\release\app-release.apk release\LifeNote-v1.0.3.apk -Force
} finally {
    Remove-Item Env:LIFENOTE_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
```

The release build runs R8 code optimization and resource shrinking. `Read-Host` keeps the password out of source files, Gradle properties, and PowerShell history; the `finally` block clears the temporary environment value and unmanaged memory.

First run downloads Gradle itself (~2 min). Output:

```
app\build\outputs\apk\release\app-release.apk
```

The command above copies the signed APK into the gitignored local `release/` staging folder. Before the private release keystore exists, an installable debug build is available with:

```powershell
.\gradlew.bat assembleDebug
Copy-Item app\build\outputs\apk\debug\app-debug.apk release\LifeNote-v1.0.3-debug.apk -Force
```

The debug APK uses a different certificate from the release APK. Install it only on a disposable test installation; it cannot update an installed release build without uninstalling that build and deleting its app-private journal.

The signed local v1.0.3 APK is 118,436 bytes with SHA-256 `29A4273B222C42C153529F5151DF4EBECC99FFE2BF3756C266F5821774E7EBF6`. The latest documented GitHub release remains [v1.0.2](https://github.com/Keith11-1313/LifeNote/releases/tag/v1.0.2) until v1.0.3 completes the checklist below and is published.

## Step 3 — Get the APK onto the Android device

Any of these works — the APK is just a file:

| Method | How |
|---|---|
| USB debugging | `adb install -r release\LifeNote-v1.0.3.apk` performs an in-place update when the signing certificate matches |
| USB file transfer | Copy the APK to the phone's Downloads folder, then open it there |
| Nearby Share or Bluetooth | Transfer the ~116 KB APK, then open it on the phone |

## Step 4 — Install on the Android device

1. Open the **Files** app → Downloads → tap `LifeNote-v1.0.3.apk` (or the provided `LifeNote-v1.0.3-debug.apk` test build)
2. Android: *"For your security, this phone is not allowed to install unknown apps"* → tap **Settings** → allow installs **from that Files app only** (scoped permission — safe, standard sideload flow)
3. Back → **Install** → done
4. For updates, Android retains the journal only when the application ID and release signing certificate match the installed build

## Step 5 — First launch

1. Open LifeNote; no password prompt appears on a fresh install
2. Create a short test entry
3. Optionally enable an app-lock password in Settings and verify change/remove controls
4. Settings → Export backup → save the zip somewhere outside the app

Updating from v1.0.0 clears its legacy mandatory PIN once. App lock remains off until explicitly enabled in Settings.

## Step 6 — Verify (the manual test checklist)

| # | Test | Expected |
|---|---|---|
| 1 | Airplane mode ON, write an entry | Saved locally, no errors |
| 2 | Type, pause for at least 900 ms, then leave the editor | Status reaches Saved and the entry remains after reopen |
| 3 | Edit an entry twice, open History, select the earlier version, review it, then restore | No restore occurs on list tap; the preview shows the selected content; after confirmation the earlier text returns and the displaced current version remains in History |
| 4 | Export backup | Chosen destination receives `journal/*.md` plus bounded `.history` files |
| 5 | Add a second local entry, then merge the earlier backup from the phone's document provider | Picker opens regardless of the provider's zip MIME label; second entry remains; imported entries/history are added or kept by `updated`; a native result message appears |
| 6 | Replace from the earlier backup after confirming | Current journal and its included history become exactly the backup; a native result message appears |
| 7 | Select a malformed or unrelated zip | Import fails without changing current entries |
| 8 | With app lock off, kill and reopen; then enable it and repeat | No prompt while off; custom password screen while on; data intact |
| 9 | Search a word from an old entry | Found |
| 10 | Compare headings/body text with the bundled font samples and inspect reader/editor actions in light and dark modes | Chubbo and Supreme render instead of Android fallback fonts; Back is a large unboxed icon; action and formatting buttons have visible boundaries and press states |

Focused physical Android verification on 2026-09-04 confirms that the signed v1.0.3 APK matches the installed release certificate, updates v1.0.2 in place, preserves existing journal data, launches without an immediate storage or crash error, serves the bundled fonts, and renders titleless cards without duplicating their first body line. A fresh export was copied off-device and structurally validated before installation. The remaining checklist interactions still require a final manual pass before v1.0.3 is published on GitHub.

All 10 passing = patch accepted.

## Upgrading later (new version exists)

Build (Step 2) → copy new APK to phones → tap it → **Install** (it updates in place, data untouched). Requires same keystore — hence the backup warning.

## Uninstall / clean rebuild

```powershell
.\gradlew.bat clean                # wipe build outputs (source + keystore untouched)
```

Uninstalling the app on a phone **deletes its journal folder** — export first. (Doc 08.)

# 07 — Build & Deploy

From source code on the PC to a working journal on an Android phone. Do this once; repeat only when a new version exists.

## Prerequisites

- Doc 03 completed (JDK + Android SDK installed and verified)
- This repo cloned/present at `C:\Users\Jerald\Desktop\LifeNote`

## Step 1 — Create the signing keystore (once, ever)

Android requires every APK to be signed. The keystore is the private key that says "this update came from me." Same key = phones accept updates over the old install.

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore "C:\Users\Jerald\Desktop\LifeNote\app\keystore.jks" `
  -alias lifenote -keyalg RSA -keysize 2048 -validity 10950 `
  -storepass <choose-a-password> -keypass <same-password> `
  -dname "CN=LifeNote"
```

- Validity **10950 days = 30 years** — outlives the project
- ⚠️ **Back up `keystore.jks` + the password immediately** (doc 08 explains why)

## Step 2 — Build

```powershell
cd C:\Users\Jerald\Desktop\LifeNote
$env:LIFENOTE_KEYSTORE_PASSWORD = '<the-password-from-step-1>'
.\gradlew assembleRelease
```

The release build runs R8 code optimization and resource shrinking. The password stays in the current terminal environment and is never written into the repository.

First run downloads Gradle itself (~2 min). Output:

```
app\build\outputs\apk\release\app-release.apk
```

Copy/rename it into the repo root for safekeeping:

```powershell
Copy-Item app\build\outputs\apk\release\app-release.apk release\LifeNote-v1.0.3.apk
```

Before the private release keystore exists, an installable debug build is available with:

```powershell
.\gradlew assembleDebug
Copy-Item app\build\outputs\apk\debug\app-debug.apk release\LifeNote-v1.0.3-debug.apk
```

The debug APK is suitable for personal device testing and is signed by the local Android debug key. Future release builds use the backed-up private keystore from Step 1.

The final signed v1.0.2 APK is published at [github.com/Keith11-1313/LifeNote/releases/tag/v1.0.2](https://github.com/Keith11-1313/LifeNote/releases/tag/v1.0.2). Its SHA-256 is `6A151C4117F264B3580B1C5003DBFBDCB136B802EADDE542D75EC791063CACE8`.

## Step 3 — Get the APK onto the phones

Any of these works — the APK is just a file:

| Method | How |
|---|---|
| USB cable | Copy file to phone's Downloads folder (MTP) |
| WiFi | Share via nearby share, or upload to Drive and download on phone |
| Bluetooth | Slow but fine for a ~2 MB file |

## Step 4 — Install on each phone (once per phone)

1. Open the **Files** app → Downloads → tap `LifeNote-v1.0.3.apk` (or the provided `LifeNote-v1.0.3-debug.apk` test build)
2. Android: *"For your security, this phone is not allowed to install unknown apps"* → tap **Settings** → allow installs **from that Files app only** (scoped permission — safe, standard sideload flow)
3. Back → **Install** → done
4. Repeat on phone 2

## Step 5 — First launch

1. Open LifeNote; no password prompt appears on a fresh install
2. Create a short test entry
3. Optionally enable an app-lock password in Settings and verify change/remove controls
4. Settings → Export backup → save the zip somewhere outside the app

Updating from v1.0.0 clears its legacy mandatory PIN once. App lock remains off until explicitly enabled in Settings.

## Step 6 — Verify (the manual test checklist)

| # | Test | Expected |
|---|---|---|
| 1 | Airplane mode ON, write entry on A | Saved locally, no errors |
| 2 | Type, pause for at least 900 ms, then leave the editor | Status reaches Saved and the entry remains after reopen |
| 3 | Edit an entry twice, open History, select the earlier version, review it, then restore | No restore occurs on list tap; the preview shows the selected content; after confirmation the earlier text returns and the displaced current version remains in History |
| 4 | Export backup | Chosen destination receives `journal/*.md` plus bounded `.history` files |
| 5 | Add a second local entry, then merge the earlier backup from the phone's document provider | Picker opens regardless of the provider's zip MIME label; second entry remains; imported entries/history are added or kept by `updated`; a native result message appears |
| 6 | Replace from the earlier backup after confirming | Current journal and its included history become exactly the backup; a native result message appears |
| 7 | Select a malformed or unrelated zip | Import fails without changing current entries |
| 8 | With app lock off, kill and reopen; then enable it and repeat | No prompt while off; custom password screen while on; data intact |
| 9 | Search a word from an old entry | Found |
| 10 | Compare headings/body text with the bundled font samples and inspect reader/editor actions in light and dark modes | Chubbo and Supreme render instead of Android fallback fonts; Back is a large unboxed icon; action and formatting buttons have visible boundaries and press states |

Physical acceptance on a Samsung SM-A525F running Android 14 verifies the v1.0.2 document picker, merge, replace, local API refresh, cold-start journal load, editor autosave, process background/resume, and result feedback with a 196-entry backup. Merge preserves all existing entries and reports its kept/imported counts; replace removes a controlled extra entry and restores exactly 196 entries. A controlled editor entry is written through the real UI, survives background and reopen, and is removed after verification without changing the 196-entry journal.

All 10 passing = patch accepted.

## Upgrading later (new version exists)

Build (Step 2) → copy new APK to phones → tap it → **Install** (it updates in place, data untouched). Requires same keystore — hence the backup warning.

## Uninstall / clean rebuild

```powershell
.\gradlew clean                    # wipe build outputs (source + keystore untouched)
```

Uninstalling the app on a phone **deletes its journal folder** — export first. (Doc 08.)

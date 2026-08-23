# 07 — Build & Deploy

From source code on the PC to a working journal on both phones. Do this once; repeat only when a new version exists.

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
.\gradlew assembleRelease
```

First run downloads Gradle itself (~2 min). Output:

```
app\build\outputs\apk\release\app-release.apk
```

Copy/rename it into the repo root for safekeeping:

```powershell
Copy-Item app\build\outputs\apk\release\app-release.apk release\LifeNote-v1.0.apk
```

## Step 3 — Get the APK onto the phones

Any of these works — the APK is just a file:

| Method | How |
|---|---|
| USB cable | Copy file to phone's Downloads folder (MTP) |
| WiFi | Share via nearby share, or upload to Drive and download on phone |
| Bluetooth | Slow but fine for a ~2 MB file |

## Step 4 — Install on each phone (once per phone)

1. Open the **Files** app → Downloads → tap `LifeNote-v1.0.apk`
2. Android: *"For your security, this phone is not allowed to install unknown apps"* → tap **Settings** → allow installs **from that Files app only** (scoped permission — safe, standard sideload flow)
3. Back → **Install** → done
4. Repeat on phone 2

## Step 5 — Pair the two phones (once, ever)

1. Phone A: open LifeNote → set a lock PIN → Settings shows `This device: 192.168.1.34` and sync PIN
2. Phone B: open LifeNote → Settings → enter A's IP + PIN
3. Phone A Settings: enter B's IP + B's PIN (both directions stored)
4. Press **Sync** on either phone → expect "✓ synced"
5. Optional but recommended: reserve both phones' IPs in the router (DHCP reservation) so this never needs repeating

## Step 6 — Verify (the manual test checklist)

| # | Test | Expected |
|---|---|---|
| 1 | Airplane mode ON, write entry on A | Saved locally, no errors |
| 2 | WiFi back on, open B, sync | A's new entry appears on B |
| 3 | Edit same entry on both, sync | Newer wins; `_conflict-` copy exists on loser's phone |
| 4 | Delete entry on A, sync | Gone on B too (tombstone) |
| 5 | Export on A → Import zip on B | Entries duplicated correctly |
| 6 | Kill app, reopen | PIN gate → all data intact |
| 7 | Search a word from an old entry | Found |

All 7 passing = v1 shipped.

## Upgrading later (new version exists)

Build (Step 2) → copy new APK to phones → tap it → **Install** (it updates in place, data untouched). Requires same keystore — hence the backup warning.

## Uninstall / clean rebuild

```powershell
.\gradlew clean                    # wipe build outputs (source + keystore untouched)
```

Uninstalling the app on a phone **deletes its journal folder** — export first. (Doc 08.)

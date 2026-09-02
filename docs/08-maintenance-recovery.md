# 08 — Maintenance & Recovery

This doc exists for Future You, possibly years from now, possibly stressed. Everything that can go wrong, and the exact fix.

## Expected lifespan

| Component | Lifespan | Why |
|---|---|---|
| The APK on an installed phone | 10+ years | Installed apps keep running across OS upgrades; WebView self-updates |
| Reinstalling on a future Android | Many years | We target a modern SDK; minimum-install requirements move slowly |
| The `.md` files | Forever | Plain text is the most durable data format in computing |

Realistic maintenance schedule: **none**, plus one 10-second habit below.

## The one habit: export monthly-ish

Settings → **Export** → zip lands in Downloads → copy anywhere (Google Drive, PC, SD card, email to yourself).

The time since the latest off-device export is the maximum writing window a lost or broken phone can take with it. The backup stays durable because:

- Entries are plain files — no proprietary lock-in, ever
- Each entry's newest 20 prior versions are included for recovery from accidental edits
- Merge and replace restore modes cover both recovery and journal-combination workflows

## Failure playbook

### 🔴 Phone lost / broken / stolen
1. Install the same APK on the replacement phone (doc 07)
2. Open LifeNote, choose a new lock PIN, and select **Replace this journal**
3. Choose the latest off-device export

### 🔴 ALL phones lost at once
1. Restore latest exported zip onto whichever phone comes next (Import button)
2. Loss window = time since last export. This is why the habit exists.

### 🟡 App won't open / behaves weirdly
1. Export first (if it opens at all)
2. Reinstall same APK over the top (update-install keeps data)
3. Nuclear option: uninstall → reinstall → Replace from the latest export. Data lives in the zip, not the app.

### 🟡 Import rejected
1. Confirm the file is a LifeNote export zip
2. Do not add unrelated files or nested folders inside `journal/`
3. Try exporting a fresh backup from the source installation
4. A rejected import does not alter the current journal

### 🟠 New phone in the future, APK won't install ("target old Android version")
Far-future problem (many Android years away). Fix path documented for whoever's around:
1. This repo + docs 03–07 rebuild the environment and produce a fresh APK targeting whatever SDK is then required
2. Any developer (or future AI assistant) can do it in an afternoon — the docs ARE the maintenance plan

### 🔴 Keystore (`keystore.jks`) lost
Consequence: cannot *upgrade*-install; must export, uninstall, reinstall, then replace from the export.
Prevention: keystore lives in 3 places minimum (PC, cloud drive, USB stick) **starting today**. GitHub does NOT hold it — `*.jks` is gitignored by policy and never attached to Releases.

## Off-device assets on GitHub

| Asset | Location | Recovery value |
|---|---|---|
| Source + all docs | repo `main` branch | Full rebuild capability from any machine (docs 03–07) |
| Every shipped APK | GitHub Releases (`/releases`) | Reinstall LifeNote without the dev PC |
| Entry data | **never on GitHub** | Journals stay off the internet by charter; exports live only where you put them |

## Migration to a brand-new phone (planned, not failure)

1. Export zip from any existing phone → copy to new phone
2. Install APK on new phone (doc 07)
3. Choose **Replace this journal** and select the copied zip
4. Verify several entries, then export a fresh backup from the new phone

## What this system will NEVER need

- Subscription renewals, account recovery emails
- Certificate renewals, domains, routers, or peer addresses
- Server patches, dependency security updates
- Play Store compliance migrations

The cost curve of LifeNote is: some effort now, then flat zero. Every design choice above was made to keep that line flat.

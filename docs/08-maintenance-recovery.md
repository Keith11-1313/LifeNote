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

30 days of unexported writing is the maximum you can ever lose to a lost/broken phone. That's the entire backup strategy, and it's enough because:

- Entries are plain files — no proprietary lock-in, ever
- Both phones hold near-complete copies of each other anyway (dual redundancy by design)

## Failure playbook

### 🔴 Phone lost / broken / stolen
1. Data: near-zero loss (other phone has everything since last sync; export from it)
2. Replace phone → install same APK (doc 07) → pair again in Settings → sync

### 🔴 BOTH phones lost at once
1. Restore latest exported zip onto whichever phone comes next (Import button)
2. Loss window = time since last export. This is why the habit exists.

### 🟡 App won't open / behaves weirdly
1. Export first (if it opens at all)
2. Reinstall same APK over the top (update-install keeps data)
3. Nuclear option: uninstall → reinstall → Import zip → re-pair. Data lives in the zip, not the app.

### 🟡 Sync stopped working
Checklist in order:
1. Both phones on the same WiFi?
2. App open on the peer? (server runs while app is open)
3. Peer IP changed? (router reassignment — check IP shown on peer's Settings screen, update it)
4. Still dead: open `http://<peer-ip>:8420/api/ping` in the phone's browser — error message tells you which layer broke

### 🟠 Router replaced / WiFi renamed
New IPs likely → update peer address in both phones' Settings once. Nothing else changes.

### 🟠 New phone in the future, APK won't install ("target old Android version")
Far-future problem (many Android years away). Fix path documented for whoever's around:
1. This repo + docs 03–07 rebuild the environment and produce a fresh APK targeting whatever SDK is then required
2. Any developer (or future AI assistant) can do it in an afternoon — the docs ARE the maintenance plan

### 🔴 Keystore (`keystore.jks`) lost
Consequence: cannot *upgrade*-install; must uninstall + reinstall + import export zip + re-pair.
Prevention: keystore lives in 3 places minimum (PC, cloud drive, USB stick) **starting today**.

## Migration to a brand-new phone (planned, not failure)

1. Export zip from old phone → copy to new phone
2. Install APK on new phone (doc 07)
3. Import zip → enter other phone's IP/PIN in Settings
4. On the *old surviving* phone: update its peer address to the new phone's IP
5. Done — the network re-forms around the new member

## What this system will NEVER need

- Subscription renewals, account recovery emails
- Certificate renewals (LAN HTTP), domain names
- Server patches, dependency security updates
- Play Store compliance migrations

The cost curve of LifeNote is: some effort now, then flat zero. Every design choice above was made to keep that line flat.

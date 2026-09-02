# 09 — Development Guide

How development actually happens on this project: the loop, the tooling, the rules. Written for any developer (or future assistant) picking up the repo cold.

## The development loop

```
edit index.html ──► refresh PC browser (mock mode)      ← seconds, no build
edit Kotlin     ──► gradlew assembleDebug ──► emulator   ← ~1 min
ship            ──► gradlew assembleRelease ──► release/ ──► phone
```

The two-tier setup exists because UI iteration must not pay the Android build tax.

### Tier 1 — Browser-only UI work

`index.html` contains a mock adapter that runs only when loaded with explicit `?mock=1`. A production API failure never exposes demo entries and instead reports that the local journal is unavailable.

```powershell
# from repo root — any static server works; Python's is fine
python -m http.server 8080 -Directory app\src\main\assets
# open http://localhost:8080/index.html?mock=1
```

What this tier validates: layout, all six views, autosave/history behavior, calendar logic, Markdown rendering, interaction flows.
What it cannot validate: storage, real networking, APK behavior.

### Tier 2 — Emulator / device integration

```powershell
# create a device once (SDK avdmanager), then:
emulator -avd LifeNoteTest &

# install + launch debug build
.\gradlew installDebug
adb shell am start -n com.lifenote/.MainActivity

# stream logs while testing
adb logcat --pid=$(adb shell pidof com.lifenote)
```

Physical phone: enable Developer Options → USB debugging → `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Engineering rules (binding)

| Rule | Rationale |
|---|---|
| Zero new dependencies, always | Each dependency = someone else's deprecation schedule. Exceptions require rewriting doc 01 principles |
| No file > ~300 lines of logic | Forces small, replaceable units; the whole codebase stays readable in one sitting |
| Never crash on malformed data | Parser contract doc 06 rule 5; journal corruption is worse than journal ugliness |
| All writes atomic | Write to temp file → rename. A mid-write crash must never half-save an entry |
| UTF-8 + `\n` everywhere | Cross-decade, cross-tool portability |
| Comments only where *why* is non-obvious | The docs carry the why; code carries the what |
| Every user-visible string survives being read aloud | It's a journal; tone matters more than cleverness |

## Definition of done (per feature)

1. Works in browser mock mode
2. Works on emulator against real files
3. Passes relevant rows of the [doc 07 checklist](07-build-deploy.md)
4. Docs updated **in the same change** if behavior or contracts moved
5. APK rebuilt into `release/` with bumped version

## Release process

```powershell
# 1. bump versionName/versionCode in app/build.gradle.kts
# 2. build signed release
$env:LIFENOTE_KEYSTORE_PASSWORD = '<release-key-password>'
.\gradlew assembleRelease
# 3. archive with versioned name
Copy-Item app\build\outputs\apk\release\app-release.apk release\LifeNote-v<version>.apk
# 4. copy to the phone, tap-to-install (doc 07 step 4)
# 5. publish GitHub Release — the permanent APK shelf, downloadable from any phone browser:
gh release create v<version> release\LifeNote-v<version>.apk `
  --title "LifeNote v<version>" `
  --notes "Changes in this version."
# 6. push source + docs
git push origin main
```

GitHub Releases are the canonical off-device APK shelf: every shipped build remains downloadable at `github.com/Keith11-1313/LifeNote/releases` indefinitely. The local `release/` folder is generated staging; APKs and private journal zips there are gitignored. Keystore files are never attached to releases and never committed (`*.jks` is gitignored) — doc 08's manual backup policy still governs them.

Release builds run R8 optimization and resource shrinking. Set `LIFENOTE_KEYSTORE_PASSWORD` in the current PowerShell session; Gradle signs only when both that value and `app\keystore.jks` exist. Never place the password in source, docs, Gradle properties, or shell history.

Rollback policy: previous APKs stay on GitHub Releases; local copies may also remain in the gitignored `release/` folder. Reinstalling an older APK over a newer one is supported (same keystore); data files are forward-compatible by parser contract.

## Environment facts (this PC)

| Item | State |
|---|---|
| JDK 17.0.12 (Temurin) at `C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot` | ✅ verified |
| Android SDK cmdline-tools + platform 35 + build-tools 35 | ✅ verified |
| Gradle | ✅ wrapper-provisioned on first build |

## Onboarding path (cold start)

New developer, zero context:

1. Read [01 overview](01-overview.md) → know what and why
2. Read [02 architecture](02-architecture.md) → know how it hangs together
3. Run Tier 1 (`?mock=1`) → see the product in 60 seconds
4. Complete [03 environment](03-environment-setup.md) → be able to build
5. Read [05 local API/restore](05-sync-protocol.md) + [06 storage](06-storage-format.md) → know the two hard contracts
6. Ship something small (calendar marker styling, search ranking tweak) through the full definition-of-done cycle

Total cold-start to first merged change: under a day.

# 09 — Development Guide

How development actually happens on this project: the loop, the tooling, the rules. Written for any developer (or future assistant) picking up the repo cold.

## The development loop

```
edit index.html ──► refresh PC browser (mock mode)      ← seconds, no build
edit Kotlin     ──► gradlew assembleDebug ──► emulator   ← ~1 min
ship            ──► gradlew assembleRelease ──► release/ ──► both phones
```

The two-tier setup exists because UI iteration must not pay the Android build tax.

### Tier 1 — Browser-only UI work

`index.html` contains a mock adapter: when loaded with `?mock=1`, or when the local API is unreachable, it runs against an in-memory dataset (~10 fake entries) and simulates sync results.

```powershell
# from repo root — any static server works; Python's is fine
python -m http.server 8080 -Directory app\src\main\assets
# open http://localhost:8080/index.html?mock=1
```

What this tier validates: layout, all five views, calendar logic, Markdown rendering, interaction flows.
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

Two emulators on the same host network can pair with each other via `10.0.2.15`-style addresses for end-to-end sync tests without physical phones.

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
.\gradlew assembleRelease
# 3. archive with versioned name
Copy-Item app\build\outputs\apk\release\app-release.apk release\LifeNote-v<version>.apk
# 4. copy to both phones, tap-to-install (doc 07 step 4)
```

Rollback policy: previous APKs stay in `release/` forever. Reinstalling an older APK over a newer one is supported (same keystore); data files are forward-compatible by parser contract.

## Environment facts (this PC)

| Item | State |
|---|---|
| JDK 17.0.12 (Temurin) at `C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot` | ✅ verified |
| Android SDK cmdline-tools + platform 35 + build-tools 35 | ⬜ install per [doc 03](03-environment-setup.md) |
| Gradle | ✅ wrapper-provisioned on first build |

## Onboarding path (cold start)

New developer, zero context:

1. Read [01 overview](01-overview.md) → know what and why
2. Read [02 architecture](02-architecture.md) → know how it hangs together
3. Run Tier 1 (`?mock=1`) → see the product in 60 seconds
4. Complete [03 environment](03-environment-setup.md) → be able to build
5. Read [05 sync](05-sync-protocol.md) + [06 storage](06-storage-format.md) → know the two hard contracts
6. Ship something small (calendar marker styling, search ranking tweak) through the full definition-of-done cycle

Total cold-start to first merged change: under a day.

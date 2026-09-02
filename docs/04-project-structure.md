# 04 — Project Structure

Authoritative map of the repository. Every file has one stated job; files without a stated job do not get created.

## Repository layout

```
LifeNote/
├── README.md                        ← project front page + doc index
├── docs/                            ← normative documentation set (01–09)
├── design/font-sources/             ← original Chubbo and Supreme font packages
│
├── settings.gradle.kts              ← declares the single module
├── build.gradle.kts                 ← plugin versions (AGP, Kotlin)
├── gradle.properties                ← JVM args, AndroidX flags
├── gradle/wrapper/                  ← pins exact Gradle version → reproducible builds forever
├── gradlew / gradlew.bat            ← wrapper launchers (no global Gradle needed)
│
├── app/
│   ├── build.gradle.kts             ← sdkVersion 26 min / 35 target, app id, signing config
│   ├── proguard-rules.pro            ← preserves the native JS bridge during R8 optimization
│   ├── keystore.jks                 ← locally generated release key; absent until doc 07 Step 1
│   └── src/main/
│       ├── AndroidManifest.xml      ← app label/icon, INTERNET permission, adjustResize
│       ├── assets/
│       │   ├── index.html           ← ENTIRE UI: 5 views + CSS + JS + mock-mode adapter
│       │   └── fonts/               ← bundled Chubbo Bold + Supreme Regular WOFF2 only
│       └── java/com/lifenote/
│           ├── MainActivity.kt      ← WebView host, app-lock/archive bridges, server lifecycle
│           ├── HttpServer.kt        ← hand-written HTTP/1.1 on ServerSocket, port 8420
│           ├── JournalStore.kt      ← CRUD + front-matter parsing over journal/*.md
│           ├── HistoryStore.kt      ← bounded, atomic per-entry revision snapshots
│           ├── ArchiveManager.kt    ← zip export + staged merge/replace import
│           └── Settings.kt          ← local API token, internal device label, optional lock password
│
├── release/
│   ├── LifeNote-v1.0.2-debug.apk    ← installable development build
│   ├── LifeNote-v1.0.2.apk          ← current release-signed artifact after keystore setup
│   ├── LifeNote-v1.0.1.apk          ← retained prior patch release for rollback
│   └── LifeNote-v1.0.0.apk          ← retained first release for rollback
│
└── export/                          ← example export zip for format reference
```

## Component budget

| File | ~Lines | Sole responsibility |
|---|---|---|
| `index.html` | ~1365 | All views, autosave, custom app lock, Markdown renderer, explicit mock adapter |
| `MainActivity.kt` | ~205 | Shell lifecycle, document picker, app-lock/archive WebView bridges |
| `HttpServer.kt` | ~245 | Loopback TCP accept, HTTP parse, token auth, entry/history routes |
| `JournalStore.kt` | ~240 | File CRUD, atomic writes, merge/replace operations, tombstone purge |
| `ArchiveManager.kt` | ~145 | Zip export, validation, staged import, imported-history pruning |
| `HistoryStore.kt` | ~75 | Deduplicated atomic snapshots and newest-20 retention |
| `Settings.kt` | ~65 | Install-local API token, password verification value, internal device label |

Total first-party code: **~2200 lines.** Third-party runtime dependencies: **0.**

## Dependency rules (enforced by review, not tooling)

```
MainActivity ──► HttpServer ──► JournalStore
      │                └──────► HistoryStore ──► JournalStore
      ├────────────► ArchiveManager ──► JournalStore
      └────────────► Settings

index.html ──fetch()──► 127.0.0.1:8420   (own app's server only)
```

1. `JournalStore` never imports network classes — testable with airplane mode on
2. `HttpServer` binds only to loopback and never accepts LAN clients
3. No component initiates outbound network connections
4. The UI touches files only through the local API and the narrow archive bridge
5. No file exceeds 300 lines of logic; split when it does

## Why specific items exist

| Item | Reason it must exist |
|---|---|
| `gradle/wrapper/` | Pins Gradle version; a rebuild in 2031 produces identical output |
| `keystore.jks` in-repo location | Single known path; doc 07 generates it here; doc 08 mandates its backup |
| `release/` folder | Phones receive APK copies from here; versioned filenames prevent "which file?" confusion |
| `export/` sample | Locks the export contract with a concrete artifact |
| `design/font-sources/` | Keeps original font packages out of the APK while preserving their source files |
| `app/proguard-rules.pro` | Keeps the annotated WebView bridge stable while R8 removes unreachable release code |

## Deliberately absent (and why)

| Absent item | Justification |
|---|---|
| `package.json`, `node_modules`, bundlers | Vanilla JS requires no build chain; a bundler would be the first dependency to rot |
| Third-party libraries in `app/build.gradle.kts` | Every library is a maintenance liability with someone else's roadmap |
| CI/CD pipelines | Local builds only; there is no team, no remote, no need |
| Automated test suites | v1 relies on the 9-point checklist in [doc 07](07-build-deploy.md); the compact Kotlin layer is cheaper to verify manually than to burden with a test framework |

## Conventions

- Language: Kotlin official style; XML resources minimized (one manifest, no layout files — WebView owns UI)
- Encoding: UTF-8 everywhere, Unix line endings in `.md` assets
- Naming: entry IDs `<compact-ISO-timestamp>-<6 hex>` per doc 06; no other identity scheme permitted

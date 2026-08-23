# 04 — Project Structure

Authoritative map of the repository. Every file has one stated job; files without a stated job do not get created.

## Repository layout

```
LifeNote/
├── README.md                        ← project front page + doc index
├── docs/                            ← normative documentation set (01–09)
│
├── settings.gradle.kts              ← declares the single module
├── build.gradle.kts                 ← plugin versions (AGP, Kotlin)
├── gradle.properties                ← JVM args, AndroidX flags
├── gradle/wrapper/                  ← pins exact Gradle version → reproducible builds forever
├── gradlew / gradlew.bat            ← wrapper launchers (no global Gradle needed)
│
├── app/
│   ├── build.gradle.kts             ← sdkVersion 26 min / 35 target, app id, signing config
│   ├── keystore.jks                 ← release signing key — 30-year validity, BACKED UP (doc 08)
│   └── src/main/
│       ├── AndroidManifest.xml      ← app label/icon, INTERNET permission, nothing else
│       ├── assets/
│       │   └── index.html           ← ENTIRE UI: 5 views + CSS + JS + mock-mode adapter
│       └── java/com/lifenote/
│           ├── MainActivity.kt      ← PIN gate, WebView host, server lifecycle
│           ├── HttpServer.kt        ← hand-written HTTP/1.1 on ServerSocket, port 8420
│           ├── JournalStore.kt      ← CRUD + front-matter parsing over journal/*.md
│           ├── SyncEngine.kt        ← peer protocol: ping→index→diff→pull/push (LWW)
│           └── Settings.kt          ← peer address, token, device name, lock PIN
│
├── release/
│   └── LifeNote-v1.0.0.apk          ← shippable artifact (renamed build output)
│
└── export/                          ← example export zip for format reference
```

## Component budget

| File | ~Lines | Sole responsibility |
|---|---|---|
| `index.html` | ~1200 | All five views, Markdown renderer (~80 lines), mock adapter |
| `MainActivity.kt` | ~100 | Shell lifecycle only |
| `HttpServer.kt` | ~200 | TCP accept, HTTP parse, route dispatch |
| `JournalStore.kt` | ~180 | File CRUD, front matter parse/serialize |
| `SyncEngine.kt` | ~160 | Protocol execution, LWW decisions |
| `Settings.kt` | ~60 | Three-to-four key-value reads/writes |

Total first-party code: **~1900 lines.** Third-party runtime dependencies: **0.**

## Dependency rules (enforced by review, not tooling)

```
MainActivity ──► HttpServer ──► JournalStore
      │                             ▲
      └────► SyncEngine ────────────┘
                    │
                    └──► Settings

index.html ──fetch()──► 127.0.0.1:8420   (own app's server only)
```

1. `JournalStore` never imports network classes — testable with airplane mode on
2. `HttpServer` contains no sync policy — it is a dispatcher, not a decision-maker
3. `SyncEngine` is the sole initiator of outbound sockets
4. The UI never touches persistence except via the local API
5. No file exceeds 300 lines of logic; split when it does

## Why specific items exist

| Item | Reason it must exist |
|---|---|
| `gradle/wrapper/` | Pins Gradle version; a rebuild in 2031 produces identical output |
| `keystore.jks` in-repo location | Single known path; doc 07 generates it here; doc 08 mandates its backup |
| `release/` folder | Phones receive APK copies from here; versioned filenames prevent "which file?" confusion |
| `export/` sample | Locks the export contract with a concrete artifact |

## Deliberately absent (and why)

| Absent item | Justification |
|---|---|
| `package.json`, `node_modules`, bundlers | Vanilla JS requires no build chain; a bundler would be the first dependency to rot |
| Third-party libraries in `app/build.gradle.kts` | Every library is a maintenance liability with someone else's roadmap |
| CI/CD pipelines | Local builds only; there is no team, no remote, no need |
| Automated test suites | v1 relies on the 7-point checklist in [doc 07](07-build-deploy.md); ~650 lines of Kotlin logic is cheaper to verify manually than to maintain framework for |

## Conventions

- Language: Kotlin official style; XML resources minimized (one manifest, no layout files — WebView owns UI)
- Encoding: UTF-8 everywhere, Unix line endings in `.md` assets
- Naming: entry IDs `<compact-ISO-timestamp>-<6 hex>` per doc 06; no other identity scheme permitted

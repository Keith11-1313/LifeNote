# 02 — System Architecture

## Canonical diagram

One APK, six components, one storage format, and no remote system.

```
┌─────────────────── LifeNote.apk ──────────────────────────────┐
│                                                               │
│   ┌─────────────────────────────┐   ┌───────────────────────┐ │
│   │        MainActivity          │   │  Embedded HTTP Server │ │
│   │  (Kotlin, native shell)      │   │     port 8420         │ │
│   │                              │   │                       │ │
│   │  PIN gate → WebView ─────────┼──►│ GET  /api/config      │ │
│   │                              │   │ GET  /api/index       │ │
│   └─────────────────────────────┘   │ GET  /api/entries/{id}│ │
│                                     │ PUT  /api/entries/{id}│ │
│                                     │ *    /api/history/{id}│ │
│                                     │ GET  /               │ │
│                                     └──────────┬────────────┘ │
│                                                │              │
│                                     ┌──────────▼────────────┐ │
│                                     │     JournalStore       │ │
│                                     │ journal/*.md files     │
│                                     └──────────▲────────────┘ │
│                                                │              │
│   ┌─────────────────────────────┐              │              │
│   │       ArchiveManager         │──────────────┘              │
│   │ export · merge · replace     │                             │
│   └─────────────────────────────┘                             │
└───────────────────────────────────────────────────────────────┘
```

## Component contracts

### MainActivity — `app/src/main/java/com/lifenote/MainActivity.kt`

| Responsibility | Rule |
|---|---|
| Server lifecycle | Starts HttpServer in `onResume`, stops it in `onPause`. The server never runs while the app is backgrounded |
| PIN gate | Renders a native lock dialog before WebView or server startup; unlocks via a SHA-256 verification value stored in Settings |
| WebView | Loads `file:///android_asset/index.html`; JavaScript enabled; DOM storage ON (settings persist via localStorage); JS alert/confirm/prompt bridged to native dialogs (WebView blocks them by default); no external origins except `127.0.0.1:8420` |
| Document picker | Bridges export, merge import, and replace import to Android's Storage Access Framework; backup operations run only while the activity is open |
| Back behavior | Back from editor flushes pending autosave before closing; Back closes History, Reader, and Editor overlays in order |

### UI — `app/src/main/assets/index.html` (single file: HTML + CSS + JS)

Owns all pixels and interactions. Contains six views:

| View | Function | Requirement IDs |
|---|---|---|
| Timeline | Date-grouped entry list, search bar on top | F4, F6 |
| Editor | Notes-app style title + WYSIWYG body; autosaves after 900 ms idle and flushes on Done/Back | F1–F3, F7–F9 |
| Calendar | Month grid, entry-day markers, day drill-down | F5 |
| Reader | Rendered Markdown view of one entry | F7 |
| History | Newest 20 prior versions with explicit restore | F9 |
| Settings | Appearance (theme/accent/text size), device name, export, merge import, replace import | D2–D4 |

**Mock mode contract:** when opened with `?mock=1` (or when `fetch` to the local API fails), the UI runs against an in-memory fake dataset. This enables browser-only development with zero Android tooling.

**Communication rule:** the UI performs no direct file access and no remote networking. Entry changes round-trip through Kotlin at `http://127.0.0.1:8420/api/*`; Android's document picker is reached through the narrow `LifeNoteFiles` JavaScript bridge.

### JournalStore — `JournalStore.kt`

CRUD over the journal folder. Parses/serializes front matter per doc 06. **Never touches the network.** Malformed files are surfaced as-is, never dropped — data loss is not an acceptable parser outcome.

### HttpServer — `HttpServer.kt`

Hand-written HTTP/1.1 listener on Java `ServerSocket`, zero dependencies. It binds only to `127.0.0.1:8420`, serves the local WebView, requires the install-local token on API routes, and closes connections on malformed input.

### ArchiveManager — `ArchiveManager.kt`

Reads and writes export-format zip streams supplied by Android's document picker. Imports are fully extracted and validated in app-private staging before merge or atomic directory replacement. It never opens a network connection.

### HistoryStore — `HistoryStore.kt`

Stores byte-preserved entry snapshots under `.history/<entry-id>/`, deduplicates consecutive identical snapshots, prunes to the newest 20, and uses atomic writes. History is local data and is included in exports.

## Request flow example

```
User types entry → pauses for 900 ms (or taps Done)
  index.html JS    : PUT http://127.0.0.1:8420/api/entries/20260823T143012-a1b2c3
  HttpServer       : validates token header → routes → JournalStore.write()
  JournalStore     : serializes front matter + body → atomic write of .md file
  response         : {"saved": true}
  index.html JS    : updates timeline list
```

Opening an existing entry for editing first calls `POST /api/history/{id}/snapshot`. Restore snapshots the current version before writing the selected revision with a fresh `updated` timestamp.

No endpoint is exposed to another device.

## Rejected alternatives (with reasons, so nobody relitigates)

| Alternative | Fatal flaw for this project |
|---|---|
| Cloud backend (Firebase/Supabase) | Accounts, quotas, deprecation cycles — violates principles 1 and 4 |
| Syncthing folder sync | Depends on third-party apps whose maintenance future we don't control |
| Pure PWA without APK | Browsers forbid pages from listening on ports; the APK exists precisely to host the server |
| Jetpack Compose native UI | Freezes rendering in today's compiled binary; system WebView receives decade-long automatic updates instead |
| SQLite storage | Binary format, unreadable outside tools; Markdown files achieve the same performance at journal scale (~MBs over years) |

## Security posture

Threat model: someone with casual access to the unlocked phone or exported backup. Not a targeted attacker with root access.

- Server binds only to loopback and is unreachable from the LAN
- Token header required on every API route
- No code initiates outbound network connections
- Import rejects unrelated files, unsafe paths, oversized archives, and excessive entry counts before changing the journal
- Content confidentiality at rest delegated to full-disk device encryption

## Longevity mechanism

The architecture's age-resistance comes from three deliberate properties:

1. **UI renders in system WebView** — Google updates the engine on the installed phone automatically
2. **Zero third-party runtime dependencies** — nothing can be deprecated *at* us
3. **Data format equals export format** — no migration path can break because none is needed

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
│   │  WebView + optional lock ─────┼──►│ GET  /api/config      │ │
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
| Server lifecycle | Starts HttpServer in `onResume`, stops it in `onPause`, and restarts it before processing a document-picker result. Resume then signals the WebView to refresh an empty journal or retry a dirty save; local API calls retry brief restart races. The server never runs while the app is backgrounded |
| Optional app lock | Starts unlocked when no password exists. When enabled, the WebView initially hides journal UI behind a custom lock screen and verifies through the narrow `LifeNoteSecurity` bridge |
| WebView | Loads the packaged `index.html` from `http://127.0.0.1:8420/`, making UI and API same-origin; JavaScript enabled; DOM storage ON; JS alert/confirm bridged to native dialogs; no external origins |
| Document picker | Bridges export, merge import, and replace import to Android's Storage Access Framework; import accepts provider-specific MIME labels, validates content internally, reports picker/result status through native Android messages, and retries the post-import UI refresh while the local API resumes |
| Back behavior | Back from editor flushes pending autosave before closing; Back closes History, Reader, and Editor overlays in order |

### UI — `app/src/main/assets/index.html` (single file: HTML + CSS + JS)

Startup renders the journal timeline from index metadata first, then hydrates missing entry bodies with at most six concurrent loopback reads. Opening an entry still fetches its body on demand, so a large journal does not hold the whole interface behind a blank loading state.

Owns all pixels and interactions. Contains six views:

| View | Function | Requirement IDs |
|---|---|---|
| Timeline | Date-grouped entry list, search bar on top | F4, F6 |
| Editor | Notes-app style title + WYSIWYG body; autosaves after 900 ms idle and flushes on Done/Back | F1–F3, F7–F9 |
| Calendar | Month grid, entry-day markers, day drill-down | F5 |
| Reader | Rendered Markdown view of one entry | F7 |
| History | Newest 20 prior versions with explicit restore | F9 |
| Settings | Appearance, optional app-lock controls, export, merge import, replace import, version | P1, D2–D4 |

**Mock mode contract:** only an explicit `?mock=1` URL enables the in-memory fake dataset. Production startup and API failures never fall back to demo content; the UI reports local-journal unavailability instead.

**Communication rule:** the UI performs no direct file access and no remote networking. Entry changes round-trip through Kotlin at `http://127.0.0.1:8420/api/*`; Android's document picker and optional app lock are reached through the narrow `LifeNoteFiles` and `LifeNoteSecurity` bridges.

Serving both UI and API from the same loopback origin avoids WebView CORS/private-network preflights. The server still emits defensive CORS and private-network headers without exposing the listener beyond loopback.

### JournalStore — `JournalStore.kt`

CRUD over the journal folder. Parses/serializes front matter per doc 06. **Never touches the network.** Malformed files are surfaced as-is, never dropped — data loss is not an acceptable parser outcome.

### HttpServer — `HttpServer.kt`

Hand-written HTTP/1.1 listener on Java `ServerSocket`, zero dependencies. It binds explicitly to IPv4 `127.0.0.1:8420` rather than Android's device-dependent generic loopback address, serves the local WebView, requires the install-local token on API routes, and closes connections on malformed input.

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

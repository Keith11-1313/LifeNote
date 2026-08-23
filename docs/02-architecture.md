# 02 — System Architecture

## Canonical diagram

One APK, five components, one storage format. Both phones run byte-identical software.

```
┌─────────────────── LifeNote.apk ──────────────────────────────┐
│                                                               │
│   ┌─────────────────────────────┐   ┌───────────────────────┐ │
│   │        MainActivity          │   │  Embedded HTTP Server │ │
│   │  (Kotlin, native shell)      │   │     port 8420         │ │
│   │                              │   │                       │ │
│   │  PIN gate → WebView ─────────┼──►│ POST /api/ping        │ │
│   │                              │   │ GET  /api/index       │ │
│   └─────────────────────────────┘   │ GET  /api/entries/{id}│ │
│                                     │ PUT  /api/entries/{id}│ │
│                                     │ GET  /               │ │
│                                     └──────────┬────────────┘ │
│                                                │              │
│                                     ┌──────────▼────────────┐ │
│                                     │     JournalStore       │ │
│                                     │ journal/*.md files     │
│                                     └──────────▲────────────┘ │
│                                                │              │
│                                     ┌──────────┴────────────┐ │
│                                     │      SyncEngine        │ │
│                                     │ ping→index→pull/push   │ │
│                                     └───────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

## Component contracts

### MainActivity — `app/src/main/java/com/lifenote/MainActivity.kt`

| Responsibility | Rule |
|---|---|
| Server lifecycle | Starts HttpServer in `onResume`, stops it in `onPause`. The server never runs while the app is backgrounded |
| PIN gate | Renders lock screen before WebView; unlocks via Settings-stored PIN |
| WebView | Loads `file:///android_asset/index.html`; JavaScript enabled; no external origins whitelisted except `127.0.0.1:8420` |
| Back behavior | Back from editor = unsaved-changes prompt |

### UI — `app/src/main/assets/index.html` (single file: HTML + CSS + JS)

Owns all pixels and interactions. Contains five views:

| View | Function | Requirement IDs |
|---|---|---|
| Timeline | Date-grouped entry list, search bar on top | F4, F6 |
| Editor | Notes-app style: title field + WYSIWYG body toolbar, serialized to the Markdown subset on save | F1–F3, F7 |
| Calendar | Month grid, entry-day markers, day drill-down | F5 |
| Reader | Rendered Markdown view of one entry | F7 |
| Settings | Appearance (theme/accent/text size), peer registry CRUD, device name, export/import, sync button | S1–S5, D2–D3 |

**Mock mode contract:** when opened with `?mock=1` (or when `fetch` to the local API fails), the UI runs against an in-memory fake dataset. This enables browser-only development with zero Android tooling.

**Communication rule:** the UI performs no file access, no persistence logic, and no networking beyond `fetch()` to `http://127.0.0.1:8420/api/*`. All state changes round-trip through Kotlin. This single rule is what lets the same code path serve the local screen and the remote peer phone.

### JournalStore — `JournalStore.kt`

CRUD over the journal folder. Parses/serializes front matter per doc 06. **Never touches the network.** Malformed files are surfaced as-is, never dropped — data loss is not an acceptable parser outcome.

### HttpServer — `HttpServer.kt`

Hand-written HTTP/1.1 listener on Java `ServerSocket`, ~200 lines, zero dependencies. Accepts exactly the five routes above. Binds to `0.0.0.0:8420` (peers must reach it) but rejects every request lacking the token header. Closes connections on malformed input. Rationale for hand-rolling: HTTP libraries get abandoned; 200 owned lines do not rot.

### SyncEngine — `SyncEngine.kt`

The only component that initiates outbound connections. Executes the protocol defined in [doc 05](05-sync-protocol.md) against every entry in the Settings peer registry, in turn. Runs synchronously during app-open and on manual trigger; reports per-peer results to the UI (`B ✓ 3 pulled · C ✗ unreachable`).

## Request flow example

```
User types entry → taps Save
  index.html JS    : PUT http://127.0.0.1:8420/api/entries/20260823T143012-a1b2c3
  HttpServer       : validates token header → routes → JournalStore.write()
  JournalStore     : serializes front matter + body → atomic write of .md file
  response         : {"saved": true}
  index.html JS    : updates timeline list
```

The identical endpoint serves Phone B's SyncEngine over WiFi — same code, different caller.

## Rejected alternatives (with reasons, so nobody relitigates)

| Alternative | Fatal flaw for this project |
|---|---|
| Cloud backend (Firebase/Supabase) | Accounts, quotas, deprecation cycles — violates principles 1 and 4 |
| Syncthing folder sync | Depends on third-party apps whose maintenance future we don't control |
| Pure PWA without APK | Browsers forbid pages from listening on ports; the APK exists precisely to host the server |
| Jetpack Compose native UI | Freezes rendering in today's compiled binary; system WebView receives decade-long automatic updates instead |
| SQLite storage | Binary format, unreadable outside tools; Markdown files achieve the same performance at journal scale (~MBs over years) |

## Security posture

Threat model: a nosy household member or random device on the home network. Not a targeted attacker.

- Server reachable only on LAN; port forwarding to internet is prohibited by project charter
- Token header required on every route including `/api/ping`
- Each device generates its own token at first launch and reveals it only during pairing; every paired device stores a peer registry (address + port) locally
- Content confidentiality at rest delegated to full-disk device encryption

## Longevity mechanism

The architecture's age-resistance comes from three deliberate properties:

1. **UI renders in system WebView** — Google updates the engine on both phones automatically, indefinitely
2. **Zero third-party runtime dependencies** — nothing can be deprecated *at* us
3. **Data format equals export format** — no migration path can break because none is needed

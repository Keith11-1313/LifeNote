# 01 — Product Overview

## Definition

LifeNote is a personal journal application running identically on any number of Android phones (practically bounded by household size; tested target ≥ 4 devices). Entries are written offline, persisted as plain text files locally, and synchronized directly among all paired devices whenever they share a local network, using an embedded HTTP server in each app instance.

No third machine participates. No cloud service exists. The system is a flat mesh of equal peer devices confined to the LAN — nothing else.

## Design principles (normative)

All implementation decisions defer to these four rules. A change that violates one requires rewriting this document first.

1. **Boring technology only.** No frameworks, no databases, no third-party runtime dependencies. Every moving part is built into Android or owned as first-party source.
2. **Local-first.** On-device storage is the primary copy. Sync is opportunistic enrichment. Full functionality exists with networking disabled.
3. **Data outlives the app.** Entries are UTF-8 Markdown readable in any editor indefinitely. Export produces the identical internal format — no conversion layer exists to rot.
4. **Zero recurring operations.** Nothing renews, expires, phones home, or requires a running process anywhere. Build once, install twice, done.

## Hardware context

```
Phone A        Phone B        Phone C        Phone D
LifeNote.apk   LifeNote.apk   LifeNote.apk   LifeNote.apk
     ▲              ▲              ▲              ▲
     └──────────────┴── local network (WiFi) ──────────┘
        every device may sync with every other device
```

No PC, NAS, or Raspberry Pi is required. All devices hold equal authority; none depends on any other for local operation. The only network constraint: peers must share a LAN — the protocol has no internet relay, by charter.

## Functional specification

### Journaling

| ID | Requirement |
|---|---|
| F1 | Create entries containing a short title (≤ 60 chars) and a formatted body |
| F2 | Edit any entry; `updated` timestamp changes on save |
| F3 | Delete any entry; deletion creates a tombstone that propagates on sync |
| F4 | Timeline view: all non-deleted entries, newest first, grouped by date |
| F5 | Calendar view: current month grid, dot marker on days containing entries, tap day → filtered list |
| F6 | Search: case-insensitive substring match across titles and bodies, results ranked newest first |
| F7 | Notes-app style editing (WYSIWYG toolbar over the Markdown subset per [doc 06](06-storage-format.md)); Markdown rendering in reader; plain-text preview in lists |

### Sync

| ID | Requirement |
|---|---|
| S1 | Automatic sync attempt on app open and on manual trigger only — never background |
| S2 | Pairwise transfer protocol per [doc 05](05-sync-protocol.md), executed against every configured peer in turn |
| S3 | Unreachable peers are skipped silently; dirty entries persist until a successful exchange |
| S4 | Conflict resolution: last-write-wins on `updated`; losing side preserved as `_conflict-*` entry |
| S5 | Peer registry: Settings supports adding, editing, and removing any number of peer addresses; convergence is transitive (A↔B↔C spreads entries to all three) |

### Data safety

| ID | Requirement |
|---|---|
| D1 | All entries stored per [doc 06](06-storage-format.md) — one UTF-8 `.md` file each |
| D2 | Export: zip of the journal folder via Android share sheet |
| D3 | Import: accept export-format zip, merge into local store |
| D4 | Both phones independently hold near-complete copies (dual redundancy) |

### Privacy & access

| ID | Requirement |
|---|---|
| P1 | Startup PIN gate before any content renders |
| P2 | Every HTTP request requires the shared sync token header; violations get `403` |
| P3 | Outbound connections occur exclusively to the paired peer IP. Nothing else, ever |
| P4 | At-rest protection delegated to Android device encryption |

## Explicit non-goals

Enforced exclusions — proposals to add any of these require a document rewrite:

- Cloud backup, accounts, analytics, telemetry
- Photos, audio, attachments (text-only v1)
- Custom encryption at rest
- Background processes, push notifications, widgets
- Internet relay of any kind — sync is LAN-only by design (this is the feature, not a limitation)
- Theming systems, animation libraries, WYSIWYG editors
- Play Store distribution

## Acceptance criteria

The project is correct when, after five years of OS updates on all paired phones:

1. Every app opens and operates without reinstall or reconfiguration
2. Every entry written on any phone exists on all phones that were paired at write time and have synced since
3. Zero rebuilds, zero payments, zero support actions occurred in between

## Current PC status (verified 2026-08-23)

| Toolchain item | State |
|---|---|
| JDK 17 (Temurin 17.0.12) | ✅ installed, `JAVA_HOME` configured |
| Android SDK cmdline-tools | ❌ install required ([doc 03](03-environment-setup.md)) |
| SDK platform + build-tools | ❌ install required |
| Gradle | ✅ n/a — wrapper self-provisions |

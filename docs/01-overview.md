# 01 — Product Overview

## Definition

LifeNote is a personal Android journal. Entries are written offline, persisted as plain text files locally, and moved between installations only through explicit export and import actions.

No cloud service, remote server, peer connection, or automatic network transfer exists.

## Design principles (normative)

All implementation decisions defer to these four rules. A change that violates one requires rewriting this document first.

1. **Boring technology only.** No frameworks, no databases, no third-party runtime dependencies. Every moving part is built into Android or owned as first-party source.
2. **Local-first.** On-device storage is the primary copy. Full functionality exists with networking disabled.
3. **Data outlives the app.** Entries are UTF-8 Markdown readable in any editor indefinitely. Export produces the identical internal format — no conversion layer exists to rot.
4. **Zero recurring operations.** Nothing renews, expires, phones home, or requires a running process anywhere. Build once, install twice, done.

## Hardware context

```
Android phone
LifeNote.apk
     │
     ├── journal/*.md
     └── explicit export/import zip
```

No PC, NAS, network, account, or companion service is required for normal use.

## Functional specification

### Journaling

| ID | Requirement |
|---|---|
| F1 | Create entries containing a short title (≤ 60 chars) and a formatted body |
| F2 | Edit any entry; `updated` timestamp changes on save |
| F3 | Delete any entry; deletion creates a 30-day tombstone that prevents resurrection when merging an older backup |
| F4 | Timeline view: all non-deleted entries, newest first, grouped by date; entries without an explicit title show one body excerpt instead of duplicating the first line as both title and preview |
| F5 | Calendar view: current month grid, dot marker on days containing entries, tap day → filtered list |
| F6 | Search: case-insensitive substring match across titles and bodies, results ranked newest first |
| F7 | Notes-app style editing (WYSIWYG toolbar over the Markdown subset per [doc 06](06-storage-format.md)); Markdown rendering in reader; plain-text preview in lists; packaged Supreme body text and Chubbo headings; restrained solid-color elevated surfaces, semantic primary actions, clearly bounded action controls, borderless large Back icons, and tactile press states give the UI depth without gradients, with accent surfaces adapted for both light and dark themes |
| F8 | Editor autosaves non-empty changes after a 900 ms idle pause; Done and Android Back flush pending changes before closing, with visible Unsaved/Saving/Saved state |
| F9 | Each editing session snapshots the prior entry version; Reader exposes the newest 20 revisions, opens a read-only preview before confirmation, and restores the selected revision while preserving the displaced current version |

### Data safety

| ID | Requirement |
|---|---|
| D1 | All entries stored per [doc 06](06-storage-format.md) — one UTF-8 `.md` file each |
| D2 | Export: save a zip of the journal folder, including bounded per-entry history, through Android's document picker |
| D3 | Merge import: retain local entries, add missing imported entries, and use `updated` timestamps when the same ID exists |
| D4 | Replace import: validate and stage the backup, then atomically replace every current journal entry after explicit confirmation |

### Privacy & access

| ID | Requirement |
|---|---|
| P1 | App lock is optional and disabled by default; v1.0.1 removes the legacy mandatory v1.0.0 PIN once, and when the user later enables a lock, a custom LifeNote screen requires the saved password before journal content renders and Settings allows changing or removing it |
| P2 | The embedded HTTP API binds only to loopback and requires its install-local token header |
| P3 | The app initiates no outbound network connections |
| P4 | At-rest protection delegated to Android device encryption |

## Explicit non-goals

Enforced exclusions — proposals to add any of these require a document rewrite:

- Cloud backup, accounts, analytics, telemetry
- Photos, audio, attachments (text-only v1)
- Custom encryption at rest
- Background processes, push notifications, widgets
- Automatic device-to-device sync or internet relay of any kind
- Theming systems, animation libraries, WYSIWYG editors
- Play Store distribution

## Acceptance criteria

The project is correct when, after five years of OS updates:

1. Every app opens and operates without reinstall or reconfiguration
2. Exported backups remain importable in both merge and replace modes
3. Zero rebuilds, zero payments, zero support actions occurred in between

## Current PC status (verified 2026-09-02)

| Toolchain item | State |
|---|---|
| JDK 17 (Temurin 17.0.12) | ✅ installed, `JAVA_HOME` configured |
| Android SDK cmdline-tools | ✅ installed ([doc 03](03-environment-setup.md)) |
| SDK platform + build-tools | ✅ platform 35 and build-tools 35.0.0 |
| Gradle | ✅ n/a — wrapper self-provisions |

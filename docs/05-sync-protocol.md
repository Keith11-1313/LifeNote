# 05 — Local API & Backup Restore

LifeNote does not sync over WiFi or connect to other devices. Moving journal data is an explicit user action through export-format zip files.

## Local-only architecture

The WebView uses a small HTTP server because it cannot access Android app-private files directly. The server binds only to `127.0.0.1:8420`; it is unreachable from the LAN. Every API request also carries an install-local token obtained from the loopback-only configuration route.

LifeNote initiates no outbound network connections. The manifest's internet permission and cleartext allowance exist solely for WebView-to-loopback HTTP on the same device.

## Export

Settings → **Export backup** opens Android's document picker. The user chooses the destination and filename. LifeNote writes every `.md` file byte-for-byte into:

```text
LifeNote-2026-09-02.zip
└── journal/
    ├── 2026-08-01_090512_k3f9a1.md
    ├── .history/20260801T090512-k3f9a1/1788350400000.md
    └── …
```

The export excludes the optional app-lock password and appearance preferences. An empty journal produces a valid zip containing no entries. The Settings screen reports picker, success, cancellation, and failure status.

## Merge import

Settings → **Merge a backup** retains the current journal and incorporates the selected backup:

The Android import picker accepts any visible file because document providers assign inconsistent MIME types to zip archives. LifeNote does not trust the picker label: it validates the zip signature, paths, file count, expanded size, and entry structure before changing the journal.

Merge indexes the existing journal once before comparing imported IDs, avoiding repeated full-directory scans. After either import mode completes, the UI reports the result immediately and retries its local API refresh for up to four seconds so a brief activity/server lifecycle transition cannot leave stale content on screen.

All UI calls to the loopback API retry transient connection failures for up to 1.8 seconds. When Android resumes LifeNote, the native shell starts the server first and then asks the WebView to refresh an empty index or retry an unsaved edit.

Initial load fetches `/api/index` before revealing the timeline. Entry bodies then hydrate through a six-request worker pool; reader access remains on-demand. This keeps startup responsive without creating one thread per archived entry.

| Situation | Result |
|---|---|
| ID exists only in backup | Imported |
| ID exists only locally | Kept |
| Same ID, backup has newer `updated` | Backup version replaces local version |
| Same ID, local is equally new or newer | Local version is kept |
| Malformed Markdown file | Preserved under its filename; a collision receives `_import-N` suffix |
| Revision history | Missing revision files are added for IDs present after merge; every entry remains capped at its newest 20 revisions |

The entire zip is validated and extracted to an app-private staging directory before any journal file changes. Each accepted merge write remains atomic.

## Replace import

Settings → **Replace this journal** first shows a destructive confirmation. After selection, LifeNote:

1. validates and extracts the complete backup into a sibling staging directory;
2. atomically renames the current journal directory aside;
3. atomically renames the staged journal into place;
4. restores the original directory if the second rename fails;
5. removes the old directory only after replacement succeeds.

Replace affects journal entries and their included revision history. The app-lock password, theme, accent, and text size remain unchanged.

## Import safeguards

- Only `journal/<filename>.md` entries and `journal/.history/<entry-id>/<millis>.md` revisions are accepted
- Revision metadata must match its history folder; traversal paths and unrelated files reject the whole import
- Maximum 20,000 files and 100 MiB uncompressed data
- Invalid or interrupted imports leave the current journal unchanged during staging
- Zip reads and writes occur only while the app is open

## Local API reference

All `/api/*` requests require `X-LifeNote-Token`. The server accepts connections only from loopback. UI and API share the same `http://127.0.0.1:8420` origin, so authenticated reads and writes do not depend on WebView cross-origin/private-network preflights. Defensive `OPTIONS` responses still include the allowed methods, token/content headers, and private-network allowance.

| Method | Path | Body | Response |
|---|---|---|---|
| `GET` | `/api/index` | — | entry metadata array |
| `GET` | `/api/entries/{id}` | — | full entry Markdown |
| `PUT` | `/api/entries/{id}` | full entry Markdown | `200` saved, `409` when the stored version is newer |
| `POST` | `/api/history/{id}/snapshot` | — | snapshots the current entry once unless identical to the newest revision |
| `GET` | `/api/history/{id}` | — | newest-first revision metadata, maximum 20 |
| `GET` | `/api/history/{id}/{key}` | — | full revision Markdown |
| `POST` | `/api/history/{id}/{key}/restore` | — | snapshots current content, then restores the revision with a fresh `updated` |
| `GET` | `/api/config` | — | local token and device label |
| `GET` | `/` | — | debug UI |
| `GET` | `/fonts/Chubbo-Bold.woff2`, `/fonts/Supreme-Regular.woff2` | — | exact packaged font bytes used by the UI |
| `OPTIONS` | any | — | CORS preflight response |

Anything else returns `404`; missing or incorrect tokens return `403`; malformed HTTP closes the connection.

## Deletions

Delete marks an entry with `deleted: true`. Tombstones remain hidden and are retained for 30 days so merging an older backup does not immediately resurrect a recently deleted entry. They are purged locally after that window.

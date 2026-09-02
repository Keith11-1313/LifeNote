# 06 — Storage Format

Normative contract. The data format is the project's most durable interface: it must remain readable by any text editor in 2056. Breaking changes to this document are prohibited; extensions follow the forward-compatibility rules below.

## Location and layout

```
/data/data/com.lifenote/files/journal/      ← app-private, per phone
├── 2026-08-01_090512_k3f9a1.md
├── 2026-08-15_223041_p8x2m7.md
├── 2026-08-23_143012_a1b2c3.md
└── .history/
    └── 20260823T143012-a1b2c3/
        └── 1788350400000.md
```

| Rule | Value |
|---|---|
| File granularity | One file per entry |
| Filename pattern | `YYYY-MM-DD_HHmmss_<id6>.md` (creation instant + id suffix) |
| Encoding | UTF-8, Unix (`\n`) line endings |
| Directory protection | Android app sandbox + device-level encryption |

`.history/<entry-id>/` contains at most 20 byte-preserved prior versions. A snapshot is created once when an existing entry begins an editing session and before delete or restore. Restoring a revision gives it a fresh `updated` timestamp and first snapshots the displaced current version. Purging a tombstone also removes that entry's history directory.

The folder is private to the app; uninstalling deletes it. Export (doc 08) is the mandated countermeasure.

## File anatomy

```markdown
---
id: 20260823T143012-a1b2c3
created: 2026-08-23T14:30:12+08:00
updated: 2026-08-23T15:10:44+08:00
device: pixel-a
deleted: false
---

Watched the sunset from the roof with Ella.
She said something I want to remember: "slow is also a direction."
```

## Field specification

| Field | Required | Purpose | Rules |
|---|---|---|---|
| `id` | ✅ | Permanent entry identity | `<YYYYMMDDTHHmmss>-<6 digits>`; immutable; never reused |
| `created` | ✅ | Write instant | ISO-8601 with timezone offset |
| `updated` | ✅ | Last-edit instant; **sole LWW arbiter** | ISO-8601 with offset; bumped on every mutation including deletes |
| `device` | ✅ | Origin of current version | Internal Android model label retained for backup conflict review; not user-facing |
| `title` | ⬜ optional | Display title in lists/reader | ≤ 60 chars, single line; omitted when empty |
| `deleted` | ✅ | Tombstone flag | `true` → hidden from all views; purged locally 30 days after `updated` |

The UI writes a dedicated `title:` key. Bodies without one fall back to "first body line displays as title" for hand-written files — both forms render identically everywhere.

## Parser contract

Binding for `JournalStore.kt` and every future tool that reads these files:

1. Front matter = lines between the first two `---` lines; `key: value`; one pair per line
2. Unknown keys are **preserved verbatim** through read-modify-write cycles (forward compatibility)
3. Missing known keys get defaults at parse time — never a crash
4. Body = everything after the second `---` plus one blank line
5. Malformed front matter ⇒ treat entire file as body text, display as-is. **Silently dropping user data is prohibited**

## Markdown rendering contract (F7)

Entries are stored as raw Markdown. Storage is always the source of truth; rendering is display-only and never rewrites stored bytes.

**Editor:** WYSIWYG-style (title field + formatted body, notes-app interaction). The toolbar applies real visual formatting; after 900 ms idle, and again on Done or Back when needed, content is serialized onto exactly the Markdown subset below. Hand-written Markdown symbols in stored files remain fully supported — both authoring paths produce identical storage.

**Reader view renders exactly this subset:**

| Syntax | Renders as |
|---|---|
| `**text**` / `__text__` | bold |
| `*text*` / `_text_` | italic |
| `` `code` `` | inline code |
| `# `, `## `, `### ` line prefixes | headings h1–h3 |
| `- ` or `* ` line prefix | bullet list item |
| `1. ` line prefix | numbered list item |
| `> ` line prefix | blockquote |
| blank line | paragraph break |

Anything else renders literally — no HTML passthrough, no images, no link auto-detection beyond bare text. Rationale: an ~80-line renderer covers real journal needs; a full Markdown engine is a dependency that violates principle 1.

**List/calendar views:** first non-empty body line, symbols stripped, truncated to 80 chars.

## Capacity model

Sustained heavy use ≈ 365 entries/year × ~2 KB ≈ **700 KB/year**, ≈ 7 MB/decade.

Consequence: the app may build its timeline and compare merge-import metadata from the full file set (milliseconds). No incremental database index is required.

## Export and import format (D2–D4)

Export zips the journal folder byte-for-byte:

```
journal-2026-08-23.zip
└── journal/
    ├── 2026-08-01_090512_k3f9a1.md
    ├── .history/20260823T143012-a1b2c3/1788350400000.md
    └── …
```

Import accepts exactly this shape. **Merge** retains local-only entries, adds imported-only entries, keeps the version with the newer `updated` timestamp when IDs collide, and incorporates valid history for IDs that exist after merging. **Replace** validates and stages the complete archive, then atomically substitutes the journal directory including history. Both modes cap each history at 20 and preserve malformed top-level `.md` files rather than dropping them. Appearance and the optional app-lock password are not part of the archive.

Internal format ≡ export format: there is no conversion layer, therefore no conversion layer can break.

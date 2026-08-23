# 06 — Storage Format

Normative contract. The data format is the project's most durable interface: it must remain readable by any text editor in 2056. Breaking changes to this document are prohibited; extensions follow the forward-compatibility rules below.

## Location and layout

```
/data/data/com.lifenote/files/journal/      ← app-private, per phone
├── 2026-08-01_090512_k3f9a1.md
├── 2026-08-15_223041_p8x2m7.md
└── 2026-08-23_143012_a1b2c3.md
```

| Rule | Value |
|---|---|
| File granularity | One file per entry |
| Filename pattern | `YYYY-MM-DD_HHmmss_<id6>.md` (creation instant + id suffix) |
| Encoding | UTF-8, Unix (`\n`) line endings |
| Directory protection | Android app sandbox + device-level encryption |

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
| `id` | ✅ | Permanent sync identity | `<YYYYMMDDTHHmmss>-<6 hex>`; immutable; never reused |
| `created` | ✅ | Write instant | ISO-8601 with timezone offset |
| `updated` | ✅ | Last-edit instant; **sole LWW arbiter** | ISO-8601 with offset; bumped on every mutation including deletes |
| `device` | ✅ | Origin of current version | Freeform label from Settings; aids human conflict review |
| `deleted` | ✅ | Tombstone flag | `true` → hidden from all views; purged 30 days after `updated`, on both peers, independently |

No dedicated title field exists in v1. First body line ≤ 60 characters renders as the list title. Less structure = fewer rules to maintain.

## Parser contract

Binding for `JournalStore.kt` and every future tool that reads these files:

1. Front matter = lines between the first two `---` lines; `key: value`; one pair per line
2. Unknown keys are **preserved verbatim** through read-modify-write cycles (forward compatibility)
3. Missing known keys get defaults at parse time — never a crash
4. Body = everything after the second `---` plus one blank line
5. Malformed front matter ⇒ treat entire file as body text, display as-is. **Silently dropping user data is prohibited**

## Markdown rendering contract (F7)

Entries are stored as raw Markdown. Storage is always the source of truth; rendering is display-only and never rewrites stored bytes.

**Editor:** plain textarea. A toolbar inserts symbol pairs but performs no rich-text manipulation.

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

Consequence: the sync protocol may diff by full-index comparison each run (milliseconds). No incremental indexing will ever be required. Simplicity here is funded by trivial scale.

## Export format (D2/D3)

Export zips the journal folder byte-for-byte:

```
journal-2026-08-23.zip
└── journal/
    ├── 2026-08-01_090512_k3f9a1.md
    └── …
```

Import accepts exactly this shape and merges entries into the local store using standard LWW semantics. Internal format ≡ export format: there is no conversion layer, therefore no conversion layer can break.

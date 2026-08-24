# AGENTS.md — Operating Instructions for AI Assistants

**Audience:** any AI assistant (opencode or otherwise) working inside this repository.
**Status:** binding. These rules override assistant defaults. If a rule blocks legitimate work, update this file first with justification, then proceed.

---

## 1. Read before acting

On first contact with this repo in a session:

1. Read this file completely.
2. Read `README.md` → then [docs/01](docs/01-overview.md) → [docs/02](docs/02-architecture.md).
3. Before touching storage or sync code, additionally read [docs/05](docs/05-sync-protocol.md) and [docs/06](docs/06-storage-format.md).

Never modify code based on memory of previous sessions. The docs are the single source of truth; re-verify contracts against them.

## 2. Documentation reflection rule (the core law)

**Every change to behavior, structure, contracts, or tooling MUST be mirrored in the docs within the same work session.** Code and documentation are one deliverable; a merge state where they disagree is considered broken.

### Reflection matrix — "I changed X" → "I update Y"

| What changed | Docs that MUST be updated |
|---|---|
| Any user-facing feature added/removed/changed | `01-overview.md` (F/S/D/P requirement IDs), `README.md` features table |
| Component responsibility, request flow, security | `02-architecture.md` |
| Build tooling, SDK versions, environment steps | `03-environment-setup.md`, verified-status tables |
| New/moved/renamed/deleted files, dependency rules | `04-project-structure.md` |
| Sync behavior, API routes, merge/conflict rules | `05-sync-protocol.md` |
| Storage layout, front matter fields, Markdown subset | `06-storage-format.md` |
| Build/sign/install/pairing procedure, test checklist | `07-build-deploy.md` |
| Backup/recovery implications, lifespan assumptions | `08-maintenance-recovery.md` |
| Dev workflow, engineering rules, release process | `09-development-guide.md` |
| Version number | `README.md` quick facts + `app/build.gradle.kts` together |

If a change touches multiple rows, update all referenced docs. When in doubt, update more rather than less.

## 3. Standing engineering laws

These are restated here because assistants violate them most often:

1. **Zero new third-party runtime dependencies.** No libraries in `app/build.gradle.kts`, no npm, nothing. Hand-roll instead. Exceptions require rewriting doc 01 principles *first* and stating the case to the user.
2. **No breaking changes to doc 06 storage format.** Unknown front-matter keys are preserved. Malformed files are displayed, never dropped.
3. **All file writes are atomic** (temp file + rename). No exceptions.
4. **No background processes, no network calls except to the paired peer**, no telemetry of any kind.
5. **Kotlin files stay under ~300 lines**; split when exceeded.
6. **Comments only where the *why* is non-obvious** — the docs carry rationale, not the code.

## 4. Working conventions

- Work in the existing directory structure; never create parallel or duplicate trees.
- Prefer editing existing docs over creating new ones; a new numbered doc requires user approval.
- Keep docs declarative and present tense ("Sync uses LWW", never "will use").
- Commands written in docs must be PowerShell-compatible (this dev PC is Windows).
- **PowerShell text operations on repo files MUST be encoding-explicit:** use `Get-Content -Encoding UTF8` / `[System.IO.File]::ReadAllText` + `WriteAllText` with `UTF8Encoding`. Bare `Get-Content`/`Set-Content` round-trips corrupt non-ASCII characters (mojibake) — this bug shipped once already; never again.
- Use pinned versions everywhere; never write "latest".
- After finishing any task, run through the reflection matrix (§2) before declaring done.

## 5. Definition of done (every task)

1. Code compiles (`gradlew assembleDebug`) when Kotlin/assets changed.
2. UI changes verified in browser mock mode (`?mock=1`).
3. Relevant rows of the doc 07 checklist pass for functional changes.
4. All docs updated per §2 — no drift between code and documentation.
5. Summary to the user states: what changed, which docs were touched, what remains open.

## 6. Prohibited without explicit user approval

- Adding/removing features (consult doc 01 spec; features need a requirement ID first)
- Creating CI pipelines, test frameworks, or new tooling
- Committing, pushing, or creating branches unless asked
- Deleting or renaming documented files/contracts
- Changing the sync protocol, port, storage format, or API shape
- Touching `keystore.jks` beyond reading its existence

## 7. Session hygiene

- Start of session: check `git status` equivalent state (or file listing) — orient before editing.
- End of task: report files created/modified with paths.
- If user requests something contradicting the docs: flag the conflict explicitly, propose the doc amendment, get agreement, then implement both halves (code + docs) together.

---

*This file exists so that any future session starts with full context and leaves the repository coherent. Treat it as the project's constitution.*

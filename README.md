# LifeNote

A private, offline-first journal for **any number of Android phones** that sync directly over a shared local network — no cloud, no accounts, no servers, no app store. Built once, maintained never.

## What it is

One sideloaded APK installed on every phone. Each instance contains a journal UI (rendered in a WebView), stores entries as plain `.md` files, and runs a tiny embedded HTTP server. Whenever paired phones share a WiFi network with the app open, they exchange entries directly — and entries propagate transitively through the mesh until every device converges. The system is equal peers confined to the LAN — nothing else participates.

## Features

| Area | Capabilities |
|---|---|
| Journaling | Write / edit / delete with notes-app style editor (title + formatted body) · timeline · **calendar view** with entry-day markers · instant search · Markdown storage rendered beautifully |
| Sync | Direct phone-to-phone over shared WiFi · unlimited peers (pair once, converge always) · auto-sync on open + manual button · offline-first with catch-up · last-write-wins merge with conflict copies |
| Data safety | Human-readable `.md` storage · one-tap export zip · import/restore · dual redundancy across both phones |
| Privacy | Startup PIN gate · sync token blocks foreign devices · zero internet communication beyond the paired phone |

## Documentation index (normative)

> **AI assistants:** read [`AGENTS.md`](AGENTS.md) before touching anything in this repo. It is binding.

| Doc | Covers |
|---|---|
| [01 — Overview](docs/01-overview.md) | Product definition, principles, full functional spec (F/S/D/P requirement IDs), non-goals |
| [02 — Architecture](docs/02-architecture.md) | Component contracts, request flows, rejected alternatives, security posture |
| [03 — Environment Setup](docs/03-environment-setup.md) | Dev PC toolchain: JDK, Android SDK, exact commands |
| [04 — Project Structure](docs/04-project-structure.md) | Repository map, dependency rules, line budgets |
| [05 — Sync Protocol](docs/05-sync-protocol.md) | Pairing, sync sequence, LWW conflicts, tombstones, API reference |
| [06 — Storage Format](docs/06-storage-format.md) | Entry file contract, parser rules, Markdown rendering subset |
| [07 — Build & Deploy](docs/07-build-deploy.md) | Keystore, build, sideload install, pairing, test checklist |
| [08 — Maintenance & Recovery](docs/08-maintenance-recovery.md) | Failure playbook, backups, phone migration, lifespan analysis |
| [09 — Development Guide](docs/09-development-guide.md) | Two-tier dev loop (browser mock → emulator), engineering rules, release process, onboarding path |

## Quick facts

| | |
|---|---|
| Version | v1.0.0 (specification frozen; implementation in progress) |
| Stack | Kotlin (~650 lines) + single-file HTML/CSS/JS UI (~1200 lines) |
| Dependencies | 0 third-party runtime dependencies |
| Min Android | 8.0 (API 26) · target API 35 |
| Sync transport | HTTP/1.1 on LAN port `8420`, token-authenticated, last-write-wins, transitive mesh convergence |
| Data format | One UTF-8 Markdown file per entry (internal ≡ export format) |
| Install method | Sideloaded APK, no Play Store |
| Recurring cost | Zero, by design |

## Device inventory

Any Android 8.0+ phone on the household LAN can join. Joining = install APK, enter one existing device's IP + PIN. Peer registries live locally on each device.

| Device | Status |
|---|---|
| Phone A | paired |
| Phone B | paired |
| +N more | add anytime via Settings |

## Status

**Implementation ~70% complete.** Environment (03) ✅ · scaffold + full UI ✅ · storage layer (06) ✅ · embedded HTTP server ✅ · sync engine (05) ⬜ · PIN gate (P1) ⬜ · ship per (07) ⬜.

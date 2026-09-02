# LifeNote

A private, offline-first Android journal with readable Markdown backups — no cloud, no accounts, no remote servers, no app store.

## What it is

One sideloaded APK contains the journal UI and stores every entry as a plain `.md` file. The app never connects to another device or internet service. Moving or combining journals happens explicitly through export-format zip backups.

## Features

| Area | Capabilities |
|---|---|
| Journaling | Debounced autosave · per-entry revision history and restore · notes-app style editor · layered journal UI · timeline · calendar · instant search · Markdown storage |
| Backup & restore | Export readable Markdown zip · merge backup into the current journal · replace the current journal from a backup |
| Data safety | Human-readable `.md` storage · bounded revision history · staged import validation · atomic replacement · explicit monthly backup workflow |
| Privacy | Optional in-app password lock · zero outbound network communication · local files remain inside Android storage until explicitly exported |

## Documentation index (normative)

> **AI assistants:** read [`AGENTS.md`](AGENTS.md) before touching anything in this repo. It is binding.

| Doc | Covers |
|---|---|
| [01 — Overview](docs/01-overview.md) | Product definition, principles, full functional spec (F/S/D/P requirement IDs), non-goals |
| [02 — Architecture](docs/02-architecture.md) | Component contracts, request flows, rejected alternatives, security posture |
| [03 — Environment Setup](docs/03-environment-setup.md) | Dev PC toolchain: JDK, Android SDK, exact commands |
| [04 — Project Structure](docs/04-project-structure.md) | Repository map, dependency rules, line budgets |
| [05 — Local API & Backup Restore](docs/05-sync-protocol.md) | Loopback API, export, merge import, replace import, failure behavior |
| [06 — Storage Format](docs/06-storage-format.md) | Entry file contract, parser rules, Markdown rendering subset |
| [07 — Build & Deploy](docs/07-build-deploy.md) | Keystore, build, sideload install, backup/restore test checklist |
| [08 — Maintenance & Recovery](docs/08-maintenance-recovery.md) | Failure playbook, backups, phone migration, lifespan analysis |
| [09 — Development Guide](docs/09-development-guide.md) | Two-tier dev loop (browser mock → emulator), engineering rules, release process, onboarding path |

## Quick facts

| | |
|---|---|
| Version | v1.0.2 |
| Stack | Kotlin (~950 lines) + single-file HTML/CSS/JS UI (~1360 lines) |
| Dependencies | 0 third-party runtime dependencies |
| Min Android | 8.0 (API 26) · target API 35 |
| Local transport | HTTP/1.1 loopback on `127.0.0.1:8420`; never exposed to the LAN |
| Data format | One UTF-8 Markdown file per entry (internal ≡ export format) |
| Install method | Sideloaded APK, no Play Store |
| Recurring cost | Zero, by design |

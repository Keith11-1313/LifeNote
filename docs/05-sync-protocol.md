# 05 — Sync Protocol

How many phones become one journal. Design goal: **correct enough for one person with several devices**, simple enough to debug with your eyes.

## Topology

N peers, fully symmetric, flat mesh. Every device may initiate a pairwise exchange with any other; neither side of a connection is privileged — each exchange has a temporary client (initiator) and server (receiver).

**Convergence is transitive:** entries reach every paired device even if two phones never meet directly. If A↔B and B↔C have synced since the entry appeared, C has it. LWW decisions are based on absolute `updated` timestamps, so merge results are order-independent: any sync order converges to the same state.

## Pairing (per device, once ever)

Each phone's Settings screen shows:

```
┌──────────────────────────────┐
│  This device: 192.168.1.34   │  ← shown big, for typing into any other phone
│  Port:        8420           │
│  PIN:         483-291        │  ← auto-generated at first launch
└──────────────────────────────┘
```

On a new phone you enter: peer IP + that peer's PIN. Each device stores a **peer registry** (name + address + port), unbounded in size. Adding device C means: type A's or B's address into C once; C's first successful sync pulls the full journal.

The PIN is per-device and shared only with devices you pair. It is the entire security model — appropriate for a home LAN where the threat is a nosy neighbor device, not a targeted attacker.

## The sync dance (initiator's perspective)

```
Phone A (initiator)                    Phone B (receiver)
      │                                        │
      │ 1. POST /api/ping                      │
      │──────── X-LifeNote-Token: 483-291 ────►│ verify PIN → 200 OK
      │                                        │
      │ 2. GET /api/index                      │
      │───────────────────────────────────────►│
      │◄──── [{id, updated, deleted}, ...] ────│  (metadata only, cheap)
      │                                        │
      │ 3. compare with local index            │
      │    ├─ B newer → add to PULL list       │
      │    ├─ A newer → add to PUSH list       │
      │    └─ only on one side → transfer it   │
      │                                        │
      │ 4. GET /api/entries/{id}   (for pulls) │
      │◄──── full entry file ──────────────────│
      │                                        │
      │ 5. PUT /api/entries/{id}   (for pushes)│
      │──── full entry file ──────────────────►│ B applies if not newer (LWW)
      │                                        │
      │ 6. report "synced ✓ 3 pulled, 1 pushed"│
```

**Trigger:** app open (auto, silent) + manual sync button. On trigger, the SyncEngine iterates the entire peer registry and attempts each in turn; per-peer results are reported independently (`B ✓ 3 pulled, 1 pushed · C ✗ unreachable`). **Never** in background — no service, no battery drain, no OS fights.

## Conflict resolution: last-write-wins (LWW)

Every entry carries an `updated` timestamp. On collision, the newer timestamp wins, unconditionally.

Why this is fine for a personal journal: conflicts require **the same entry edited on both phones between two syncs**. With one human, you're editing one entry on one phone at a time. Expected frequency: ~never.

Safety net: when a real conflict happens, the loser isn't destroyed — it's saved as a separate `_conflict-<id>-<device>.md` entry so you can merge manually if you ever notice.

### Worked example

| Time | Event | A has | B has | Winner |
|---|---|---|---|---|
| 10:00 | entry `e1` exists, synced | v1 | v1 | — |
| 10:05 | edit on A | v2 | v1 | |
| 10:07 | edit on B (same entry!) | v2 | v3 | |
| 10:10 | sync, A initiates | | | |
| 10:10 | compare: B's `updated` > A's | | | **v3** |
| 10:10 | A pulls v3, saves own v2 as `_conflict-e1-a.md` | v3 | v3 | |

## Deletions: tombstones

Deleting an entry must propagate — otherwise the next sync resurrects it (the classic "deleted on A, still on B, comes back" bug).

Solution: **delete = edit**. The entry file gets `deleted: true` in its front matter, disappears from the UI, and syncs like any other change. Tombstones are auto-purged 30 days after `updated`, independently on each device.

**Multi-device caveat (documented behavior):** if a device stays offline longer than 30 days while others synced its deletion and purged, rejoining can resurrect the deleted entry (its live copy flows back to peers). Remedy when it happens: delete again after all devices are online, or reconcile via export/import. Accepted tradeoff — no delivery receipts, no complexity; frequency ≈ never in real use.

## API reference (the whole thing)

All requests require header `X-LifeNote-Token: <PIN>`. All bodies are UTF-8. Server rejects anything else with `403`.

| Method | Path | Body | Response |
|---|---|---|---|
| `POST` | `/api/ping` | — | `200 {"name":"pixel-a","entries":42}` |
| `GET` | `/api/index` | — | `200 [{"id":"...","updated":"...","deleted":false},...]` |
| `GET` | `/api/entries/{id}` | — | `200` full entry file (front matter + body) |
| `PUT` | `/api/entries/{id}` | full entry file | `200` if accepted, `409` if receiver's copy is newer (LWW reject — initiator then pulls instead) |
| `GET` | `/` | — | the UI (used only for debugging in a PC browser) |

Anything else → `404`. Malformed HTTP → connection closed. There is no auth role system, no sessions, no cookies — one shared PIN is the whole security model, appropriate for a home LAN.

## Failure modes & behavior

| Situation | Behavior |
|---|---|
| Peer unreachable | Skipped silently; other peers still sync; entries stay dirty until a peer succeeds |
| Sync interrupted mid-transfer | Each entry is one atomic file write — worst case one entry is stale, next sync fixes it |
| Two devices edited different entries offline | No conflict — both transfer, both kept |
| Two devices edited the *same* entry offline | LWW + conflict copy (see above); result is order-independent across all peers |
| Three+ devices, staggered syncs | Converge to identical state regardless of sync order (absolute timestamps) |
| Wrong PIN | `403`, that peer's sync aborts, UI flags the pairing problem; other peers unaffected |
| Peer IP changed | Ping timeout for that peer only; update its registry entry in Settings |
| Device retired permanently | Remove it from every remaining device's peer registry (Settings) |

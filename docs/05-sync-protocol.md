# 05 — Sync Protocol

How two phones become one journal. Design goal: **correct enough for one person with two devices**, simple enough to debug with your eyes.

## Topology

Exactly 2 peers, fully symmetric. Either can initiate. Neither is "the server" — both run identical code, and each connection has a temporary client (initiator) and server (receiver).

## Pairing (done once, ever)

Each phone's Settings screen shows:

```
┌──────────────────────────────┐
│  This device: 192.168.1.34   │  ← shown big, for typing into the other phone
│  Port:        8420           │
│  PIN:         483-291        │  ← auto-generated at first launch
└──────────────────────────────┘
```

On the other phone you enter: peer IP + PIN. Stored forever. If the router re-assigns IPs, you fix it in Settings (30 seconds, once a blue moon) — or reserve the IP in the router to never think about it again.

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

**Trigger:** app open (auto, silent) + manual sync button. **Never** in background — no service, no battery drain, no OS fights.

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

Solution: **delete = edit**. The entry file gets `deleted: true` in its front matter, disappears from the UI, and syncs like any other change. Tombstones are auto-purged 30 days after `updated`, by which time both phones have certainly seen the deletion.

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
| Peer unreachable | Silent skip; entries stay dirty; retry on next open |
| Sync interrupted mid-transfer | Each entry is one atomic file write — worst case one entry is stale, next sync fixes it |
| Both phones edited different entries offline | No conflict — both transfer, both kept |
| Both edited the *same* entry offline | LWW + conflict copy (see above) |
| Wrong PIN | `403`, sync aborts, UI shows "pairing problem" |
| Peer IP changed | Ping timeout; Settings shows hint to update address |

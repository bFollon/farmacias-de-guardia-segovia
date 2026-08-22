# Farmacias Data Service

Single source of truth for parsed pharmacy schedules — see [`../Features/backend-data-service.md`](../Features/backend-data-service.md) and [`../Features/migration-plan.md`](../Features/migration-plan.md) for the full design and phased rollout.

**Status: scaffold only.** No parsers are ported yet (Phase 1, step 2) — `POST /api/refresh/:locationId` returns `501` for every location. Schedule JSON must currently be placed by hand into `data/schedules/{locationId}.json` and picked up via `POST /api/admin/reload`.

## Develop

```bash
npm install
cp .env.example .env   # set API_TOKEN
npm run dev
```

## Build & run

```bash
npm run build
npm start
```

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness check |
| GET | `/api/locations` | Bearer | manifest: `{ locationId, version }[]` |
| GET | `/api/schedules/:locationId` | Bearer | full schedule JSON; supports `If-None-Match` → `304` |
| POST | `/api/refresh/:locationId` | Bearer | stubbed — `501` until a parser is ported |
| POST | `/api/admin/reload` | Bearer | re-read `data/schedules/*.json` from disk |

All Bearer-authed routes expect `Authorization: Bearer $API_TOKEN`.

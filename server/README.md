# Farmacias Data Service

Single source of truth for parsed pharmacy schedules — see [`../Features/backend-data-service.md`](../Features/backend-data-service.md) and [`../Features/migration-plan.md`](../Features/migration-plan.md) for the full design and phased rollout.

**Status:** all 4 region parsers are ported and passing fixture tests (`npm test`). `POST /api/refresh/:locationId` does a real fetch+parse+validate+publish for all 11 locations. Not deployed anywhere yet. No client (iOS/Android) talks to this — Phase 3 hasn't started, so deploying this changes nothing about what the apps show.

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

## Deploy (pm2)

1. On the Pi: clone/pull the repo, then in `server/`:
   ```bash
   npm ci
   npm run build
   ```
2. Create `.env` (not committed — gitignored) with a real token:
   ```bash
   echo "API_TOKEN=$(openssl rand -hex 32)" > .env
   echo "PORT=3000" >> .env
   echo "HOST=0.0.0.0" >> .env
   ```
   pm2 doesn't load `.env` files itself — either `export $(cat .env | xargs)` before starting pm2, or add an `env` block to `ecosystem.config.cjs` (don't commit real secrets into that file).
3. Start under pm2:
   ```bash
   pm2 start ecosystem.config.cjs
   pm2 save
   ```
4. `data/schedules/*.json` is where published schedules live — it's gitignored, so it survives `git pull` but won't exist on a fresh clone. That's fine: there's no real state to protect, everything there is reproducible by re-running step 5.
5. **Nothing is populated on first boot.** There's no [[pdf-change-monitor]] yet to auto-trigger parses, so call refresh once per location by hand:
   ```bash
   for loc in segovia-capital cuellar el-espinar riaza-sepulveda la-granja la-sierra fuentidueña carbonero navas-asuncion villacastin cantalejo; do
     curl -s -X POST -H "Authorization: Bearer $API_TOKEN" "http://localhost:3000/api/refresh/$loc"
     echo
   done
   ```
   The 8 rural ids share one PDF — refreshing any one of them parses and validates all 8 together and only publishes if every one passes, so you only strictly need to call it once per rural id but calling all 8 is harmless (each after the first will just see the same PDF hash and no-op).
6. Exposing this publicly (Cloudflare Tunnel, matching the Bugsink/Aptabase pattern) can wait — nothing consumes this API yet, so there's no rush. When you do, only `/health` should be reachable without a token in mind; every other route already requires `Authorization: Bearer $API_TOKEN`.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness check |
| GET | `/api/locations` | Bearer | manifest: `{ locationId, version }[]` |
| GET | `/api/schedules/:locationId` | Bearer | full schedule JSON; supports `If-None-Match` → `304` |
| POST | `/api/refresh/:locationId` | Bearer | fetch + parse + validate + publish (fails closed) |
| POST | `/api/admin/reload` | Bearer | re-read `data/schedules/*.json` from disk |

All Bearer-authed routes expect `Authorization: Bearer $API_TOKEN`.

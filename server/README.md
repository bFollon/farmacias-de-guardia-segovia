# Farmacias Data Service

Single source of truth for parsed pharmacy schedules — see [`../Features/backend-data-service.md`](../Features/backend-data-service.md) and [`../Features/migration-plan.md`](../Features/migration-plan.md) for the full design and phased rollout.

**Status:** all 4 region parsers are ported and passing fixture tests (`npm test`). `POST /api/refresh/:locationId` does a real fetch+parse+validate+publish for all 11 locations, deployed and live on the Pi. No client (iOS/Android) talks to this yet — Phase 3 hasn't started, so this doesn't change anything about what the apps show.

## Develop

```bash
npm install
cp .env.example .env   # set API_KEY and RELOAD_KEY
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
2. Create `.env` (not committed — gitignored) with two separate real tokens, matching InterSegoService's `API_KEY`/`RELOAD_KEY` split:
   ```bash
   echo "API_KEY=$(openssl rand -hex 32)" > .env
   echo "RELOAD_KEY=$(openssl rand -hex 32)" >> .env
   echo "PORT=3000" >> .env
   echo "HOST=0.0.0.0" >> .env
   ```
   `API_KEY` gates the read-only routes (`GET /api/locations`, `GET /api/schedules/:id`) and is the one that's safe to embed in the apps once Phase 3 exists. `RELOAD_KEY` gates the two admin routes (`POST /api/refresh`, `POST /api/admin/reload`) and should **never** end up in a shipped app — extracting `API_KEY` from a binary shouldn't hand out the ability to trigger fetches against cofsegovia.com or force a republish.

   `.env` is loaded automatically via `dotenv` (see `src/index.ts`'s first import), same pattern as InterSegoService/InterSegoMonitor — no need to export it into the shell or add it to `ecosystem.config.cjs` yourself.
3. Start under pm2:
   ```bash
   pm2 start ecosystem.config.cjs
   pm2 save
   ```
4. `data/schedules/*.json` is where published schedules live — it's gitignored, so it survives `git pull` but won't exist on a fresh clone. That's fine: there's no real state to protect, everything there is reproducible by re-running step 5.
5. **Nothing is populated on first boot.** There's no [[pdf-change-monitor]] yet to auto-trigger parses, so call refresh once per location by hand, using `$RELOAD_KEY`:
   ```bash
   for loc in segovia-capital cuellar el-espinar riaza-sepulveda la-granja la-sierra fuentidueña carbonero navas-asuncion villacastin cantalejo; do
     curl -s -X POST -H "Authorization: Bearer $RELOAD_KEY" "http://localhost:3000/api/refresh/$loc"
     echo
   done
   ```
   The 8 rural ids share one PDF — refreshing any one of them parses and validates all 8 together and only publishes if every one passes, so you only strictly need to call it once per rural id but calling all 8 is harmless (each after the first will just see the same PDF hash and no-op — and after the first successful publish, refresh does a cheap `HEAD` request first and skips the download entirely unless the PDF actually changed).
6. Exposing this publicly (Cloudflare Tunnel, matching the Bugsink/Aptabase pattern) can wait — nothing consumes this API yet, so there's no rush. When you do, expose only `API_KEY`-gated routes to anything resembling app traffic; `RELOAD_KEY` stays server-side, used only by you (or later, `pdf-change-monitor`) directly.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness check |
| GET | `/api/locations` | `API_KEY` | manifest: `{ locationId, version }[]` |
| GET | `/api/schedules/:locationId` | `API_KEY` | full schedule JSON; supports `If-None-Match` → `304` |
| POST | `/api/refresh/:locationId` | `RELOAD_KEY` | fetch (HEAD-checked first) + parse + validate + publish (fails closed) |
| POST | `/api/admin/reload` | `RELOAD_KEY` | re-read `data/schedules/*.json` from disk |

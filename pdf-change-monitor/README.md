# PDF Change Monitor

Watches [cofsegovia.com](https://cofsegovia.com/farmacias-de-guardia/) for pharmacy duty PDF changes. Runs twice a day (08:00 and 20:00), downloads all 4 region PDFs (Segovia Capital, Cuéllar, El Espinar, Segovia Rural — one PDF backs all 8 ZBS), compares their SHA-256 checksums against the last known state, and:

1. Triggers `POST /api/refresh/:locationId` on `farmacias-data-service` for any region whose PDF changed, so the new schedule is parsed, validated, and published automatically.
2. Sends a summary email either way — "PDF changed" is always worth knowing, and the refresh outcome (published vs. held for review by the validation gate) rides along in the same email.

Ported from InterSego's `services/InterSegoMonitor`, adapted per `Features/pdf-change-monitor.md`: the biggest deviation is that this monitor *triggers* a re-parse automatically (InterSego's monitor only alerts, since InterSego has no automated parser to trigger).

## Requirements

- Node.js >= 20
- pm2 (`npm install -g pm2`)
- `farmacias-data-service` (`../server`) already deployed and reachable

## Environment variables

Copy `.env.example` to `.env` and fill in the values.

| Variable | Required | Default | Description |
|---|---|---|---|
| `MONITOR_API_KEY` | Yes | — | Bearer token required for this monitor's own `POST /check`. Generate with `openssl rand -hex 32`. |
| `DATA_SERVICE_RELOAD_KEY` | Yes | — | Must match `server/.env`'s `RELOAD_KEY` exactly — used to call the data service's refresh endpoint. |
| `DATA_SERVICE_URL` | No | `http://localhost:3765` | Base URL of `farmacias-data-service`. |
| `SMTP_USER` | Yes | — | iCloud sender address. Used as the SMTP username. |
| `SMTP_PASS` | Yes | — | App-specific password for iCloud SMTP. **Not your Apple ID password.** Generate at [appleid.apple.com](https://appleid.apple.com) → Sign-In & Security → App-Specific Passwords. |
| `NOTIFY_EMAIL` | Yes | — | Address to receive change notifications. |
| `PORT` | No | `3702` | Port to listen on. |
| `HOST` | No | `0.0.0.0` | Interface to bind to. Use `127.0.0.1` to restrict to localhost. |

## Deploy

```bash
# 1. Clone / pull the repo on the Pi, then:
cd /path/to/FarmaciasDeGuardia/pdf-change-monitor

# 2. Install dependencies and build
npm install
npm run build

# 3. Create and fill in .env
cp .env.example .env
nano .env

# 4. Start with pm2
pm2 start dist/index.js --name pdf-change-monitor

# 5. Persist across reboots (run once, if not already done for other services)
pm2 save
pm2 startup   # follow the printed instructions if not already set up
```

## Rebuild after code changes

```bash
cd /path/to/FarmaciasDeGuardia/pdf-change-monitor
git pull
npm install
npm run build
pm2 restart pdf-change-monitor
```

## pm2 commands

```bash
pm2 status                          # check process is running
pm2 logs pdf-change-monitor         # tail logs
pm2 logs pdf-change-monitor --lines 100
pm2 restart pdf-change-monitor
pm2 stop pdf-change-monitor
```

## Endpoints

All endpoints except `/health` require `Authorization: Bearer <MONITOR_API_KEY>`.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | No | Liveness check. Returns `{ status: "ok" }`. |
| `GET` | `/status` | Yes | Last check timestamp and per-region state (URL, SHA-256 prefix, lastChecked, lastChanged). |
| `POST` | `/check` | Yes | Triggers an immediate check. Returns 202 immediately; check runs in the background. |

## How it works

1. On startup, verifies SMTP credentials, then runs an initial check.
2. Cron fires at `0 8,20 * * *` (08:00 and 20:00 server time).
3. Each check:
   - Scrapes the cofsegovia.com listing page for current PDF links (falls back to hardcoded URLs if scraping fails).
   - Downloads each region's PDF and computes a SHA-256 hash.
   - Compares against the stored state in `data/checksums.json`.
   - For any region whose URL or hash changed, calls `POST /api/refresh/:locationId` on the data service (Segovia Rural uses any one of its 8 ZBS ids — refreshing one refreshes all 8), then sends one summary email listing the affected regions and each refresh's outcome.
4. State survives restarts via `data/checksums.json`. The first run after deployment establishes the baseline — no refresh or email on first run.

## Notes

- iCloud SMTP requires an **app-specific password**, not your regular Apple ID password.
- Unlike InterSego's monitor (Linecar has an incomplete SSL cert chain), cofsegovia.com's certificate is valid — no special TLS handling needed here.
- The service runs on port `3702` by default, distinct from InterSego's `intersego-server` (3700) and `intersego-monitor` (3701), and from `farmacias-data-service` (3765).

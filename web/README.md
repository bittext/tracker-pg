# Tracker (PostgreSQL) — Web UI

Angular front end for the **tracker-pg** API. Same features as the Oracle `tracker/web` app; dev proxy targets **port 9091** (`proxy.conf.json`).

## Prerequisites

- Node 18+ (LTS recommended)
- tracker-pg Spring Boot API on `http://127.0.0.1:9091` (e.g. `docker compose up -d` then `cd ../server && mvn spring-boot:run`)

## Install & run

```bash
npm install
npm start
```

Open `http://localhost:4200/`. API calls use `/api/...` and are forwarded to the backend by the dev server proxy.

## Build

```bash
npm run build
```

Output: `dist/web/`.

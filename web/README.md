# Tracker (PostgreSQL) — Web UI

Angular front end for the **tracker-pg** API. Same features as the Oracle `tracker/web` app; dev proxy targets **port 9091** (`proxy.conf.json`).

## Prerequisites

- **Node.js (even major, LTS only):** use **20.x**, **22.x**, or **24.x** (see `package.json` → `engines` and `.nvmrc`). Odd majors (e.g. 21, 23, 25) are not supported for this app. With [nvm](https://github.com/nvm-sh/nvm): `nvm install` then `nvm use` from this directory (reads `.nvmrc`, currently **22** — same line as `web/Dockerfile`’s `node:22-alpine`).
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

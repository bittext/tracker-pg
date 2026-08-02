# tracker-pg Bruno collection

API collection for the Phoenix Nexus / tracker-pg Angular + Spring app.

## Open in Bruno

1. Install [Bruno](https://www.usebruno.com/).
2. **Open Collection** → select this folder: `bruno/tracker-pg`
3. Choose environment **Local** or **Production**.
4. Set `username` and secret `password`.
5. For **Local**, `autoLogin` is `true` — any protected request will login and save `token`.
6. For **Production**, run **01 Auth → Login (sets token)** (or set `autoLogin` = `true`).

If you still see `{"error":"unauthorized"}`, the active environment has no token and
auto-login could not run (missing credentials, or `autoLogin=false`).

## Auth quick reference

| Mode | How |
|------|-----|
| Auto (Local default) | Set username/password → send any request |
| Explicit login | **01 Auth → Login (sets token)** |
| Gate check | **01 Auth → Ensure Authenticated** |
| MFA | Login → set `otpCode` → **MFA Verify** |
| Step-up | **Step-Up** → header `X-Step-Up-Token: {{stepUpToken}}` |

Collection auth = Bearer `{{token}}`. Public login/MFA requests use `auth: none`.

## Layout

| Folder | App area |
|--------|----------|
| 01 Auth | Login, MFA, step-up, logout |
| 02 Meta | `/api/version` |
| 03 Me Member | Profile / onboarding |
| 04 Markets Overview Trading | Screeners, Finviz, research, predicts |
| 05 Robinhood Daily Tracker | Daily Tracker, crypto, agentic, performance |
| 06 Trading Journal | Journal tab |
| 07 Tracker Notes | Tracker notes |
| 08 Life Notes | Life notes |
| 09 Journal | Personal journal |
| 10 Management | Tasks, notes, writeups, docs… |
| 11 Report Calendar | Report calendar |
| 12 Fitness | Exercise |
| 13 Finance Banking Credit | Banking / credit / loans… |
| 14 Admin | Admin console |

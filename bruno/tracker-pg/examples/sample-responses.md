# Sample responses (illustrative)

Shapes match the Angular DTOs / Spring controllers. Values are examples.

## POST `/api/auth/login` — success

```json
{
  "mfaRequired": false,
  "challengeId": null,
  "message": "Signed in",
  "token": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresAt": "2026-08-03T17:00:00Z",
    "username": "spulickal",
    "role": "ADMIN",
    "marketsEnabled": true
  }
}
```

## POST `/api/auth/login` — MFA required

```json
{
  "mfaRequired": true,
  "challengeId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Enter the code from your authenticator app.",
  "token": null
}
```

## GET `/api/auth/me`

```json
{
  "userId": 2,
  "username": "spulickal",
  "role": "ADMIN",
  "marketsEnabled": true,
  "admin": true
}
```

## POST `/api/auth/step-up`

```json
{
  "stepUpToken": "eyJhbGciOiJIUzI1NiJ9.stepup...",
  "expiresAt": "2026-08-02T18:15:00Z"
}
```

## GET `/api/finance/robinhood/daily-tracker?year=2026&months=8`

Abbreviated:

```json
{
  "year": 2026,
  "months": [8],
  "accounts": [
    { "accountSuffix": "3370", "label": "Individual investing" }
  ],
  "days": [
    {
      "snapshotDate": "2026-08-01",
      "hasScheduledSnapshot": true,
      "accounts": [
        {
          "accountSuffix": "3370",
          "totalAccountValue": 134116.79,
          "cashBalance": -19910.71,
          "equityMarketValue": 154027.5
        }
      ]
    }
  ]
}
```

## GET `/api/markets/tracker/notes?year=2026&month=8`

```json
[
  {
    "id": 42,
    "year": 2026,
    "month": 8,
    "subject": "Untitled",
    "body": "# Notes\n",
    "attachments": []
  }
]
```

## Error — 401

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

## Error — step-up required (403)

```json
{
  "error": "step_up_required",
  "message": "Re-enter your password to continue."
}
```

# Plaid Security Evidence Index

This document maps common Plaid security questionnaire themes to **categories of technical evidence** available for this product.

**This index does not list source files, class names, or configuration filenames.** Public and partner-facing materials should stay at that level; operators correlate themes to implementation using **private internal runbooks**, secure code review, or redacted exports—not paths published in the open repository.

> Note: This index is technical evidence support and does not replace your internal policy documents, risk register, access review logs, or incident records.

## 1) Access control and authentication

- JWT-based API authentication, route protection, and Spring Security–style request authorization.
- Role-based restrictions for administrative endpoints.

## 2) Password handling and credential security

- Password hashing with BCrypt, per-user salt, and server-side pepper material.
- Auth-related settings supplied through environment and deployment configuration (not embedded in client code).

## 3) Security headers and secure transport posture

- HTTP security headers (including HSTS where enabled, frame options, referrer policy, permissions policy).
- Forwarded/proxy header behavior compatible with TLS termination in front of the API.

## 4) Error handling and information disclosure controls

- API error handling that avoids returning stack traces or internal exception messages to clients.

## 5) Logging and monitoring support

- API request/response logging with redaction appropriate for credentials and sensitive payloads.

## 6) Data integrity and controlled schema change

- Versioned database migrations applied on startup, with schema validation aligned to the deployed model.

## 7) Upload handling and storage constraints

- Multipart and upload size limits, controlled import paths for financial data ingestion.

## 8) Deployment/change management evidence

- Automated deployment pipeline definitions, host deployment scripts, and container/orchestration stack configuration (as maintained privately for your environment).

## 9) Plaid integration-specific controls

- Banking/Plaid integration logic with account and item scoping appropriate to the product.
- Plaid-related settings and secrets provided only through environment and secure configuration.
- When configured, Plaid Item **access tokens** stored for sync are **application-level encrypted at rest** (AES-256-GCM with a deployment secret); legacy plaintext rows are re-sealed on next use.
- **Privacy policy** in the web app describes financial data and Plaid; members record acknowledgment before Link; they may **disconnect** per institution (stored credentials removed; imported ledger data managed separately).

## Operational documents to provide outside the repo

For Plaid review, pair this technical index with your internal operational evidence:

- Information Security Policy
- Incident Response Procedure
- Access review records
- Risk register / risk treatment log
- Vulnerability and patch management records
- Change approval/release records

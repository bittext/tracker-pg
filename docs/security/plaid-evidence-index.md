# Plaid Security Evidence Index

This file maps common Plaid security questionnaire themes to concrete evidence in this codebase.

> Note: This index is technical evidence support and does not replace your internal policy documents, risk register, access review logs, or incident records.

## 1) Access control and authentication

- JWT-based API auth and route protection:
  - `server/src/main/java/com/svp/tracker/auth/config/SecurityConfig.java`
  - `server/src/main/java/com/svp/tracker/auth/security/JwtAuthenticationFilter.java`
- Admin endpoint role restrictions:
  - `server/src/main/java/com/svp/tracker/auth/config/SecurityConfig.java`

## 2) Password handling and credential security

- Password hashing with BCrypt + per-user random salt + application pepper:
  - `server/src/main/java/com/svp/tracker/auth/service/PasswordHashService.java`
- Password pepper and auth controls configured via environment-backed properties:
  - `server/src/main/resources/application.yml`

## 3) Security headers and secure transport posture

- Header controls (HSTS toggle, frame deny, referrer policy, permissions policy):
  - `server/src/main/java/com/svp/tracker/auth/config/SecurityConfig.java`
- Forwarded header handling (proxy/TLS termination compatibility):
  - `server/src/main/resources/application.yml`

## 4) Error handling and information disclosure controls

- API error responses avoid leaking stack traces/messages to clients:
  - `server/src/main/resources/application.yml`
  - `server/src/main/java/com/svp/tracker/common/web/ApiExceptionHandler.java`

## 5) Logging and monitoring support

- API request/response logging with auth/body redaction handling:
  - `server/src/main/java/com/svp/tracker/config/ApiHttpLoggingFilter.java`

## 6) Data integrity and controlled schema change

- Flyway-enabled migrations and Hibernate validate mode:
  - `server/src/main/resources/application.yml`
  - `server/src/main/resources/db/migration/`

## 7) Upload handling and storage constraints

- Multipart size limits and controlled import behavior:
  - `server/src/main/resources/application.yml`
  - `server/src/main/java/com/svp/tracker/finance/service/BankingService.java`

## 8) Deployment/change management evidence

- Deployment automation and environment orchestration artifacts:
  - `.github/workflows/deploy-develop-lightsail.yml`
  - `scripts/lightsail-deploy.sh`
  - `docker-compose.stack.yml`

## 9) Plaid integration-specific controls

- Plaid integration service and account scoping logic:
  - `server/src/main/java/com/svp/tracker/finance/service/BankingPlaidService.java`
- Plaid config flags/secrets via environment:
  - `server/src/main/resources/application.yml`

## Operational documents to provide outside the repo

For Plaid review, pair this technical index with your internal operational evidence:

- Information Security Policy
- Incident Response Procedure
- Access review records
- Risk register / risk treatment log
- Vulnerability and patch management records
- Change approval/release records


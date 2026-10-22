# TrustVault

TrustVault is a document-management application built to demonstrate authenticated access, document ownership, controlled sharing, file upload/download, and audit logging.

> **Project status:** The backend and frontend build locally. Automated tests currently use an isolated H2 database. No production backend, database, or persistent file store is configured or deployed by this repository.

## Features

- HTTP Basic authentication for protected API endpoints.
- User roles: `OWNER`, `VIEWER`, and `ADMIN`.
- Authenticated owners can upload documents.
- Document metadata includes a name, category, owner, and upload time.
- Owners can grant and revoke document permissions.
- Shared users can access documents according to their `READ` or `EDIT` permission.
- Authorized users can download stored files.
- Server-side file storage uses generated stored filenames rather than trusting a client-provided path; allowed file types are checked by extension, not by inspecting file contents.
- Audit events are recorded for implemented actions such as uploads and successful downloads.
- Administrator-only audit endpoint protected by role authorization.
- React frontend built with Vite, configurable backend base URL, and document download actions.

## Technology stack

- **Backend:** Java 17, Spring Boot, Spring Security, Spring Data JPA / Hibernate, Maven
- **Frontend:** React, Vite, JavaScript, CSS
- **Database:** MySQL
- **File storage:** Local server filesystem

Check the actual versions in `pom.xml` and `frontend/package.json` before submission.

## Architecture

```text
React + Vite frontend
        |
        | HTTP API requests (/api)
        v
Spring Boot REST API
  |        |          |
  |        |          +-- Audit logging
  |        +------------- File storage service
  +---------------------- MySQL / JPA repositories
```

The frontend development server may proxy `/api` requests to the backend. Confirm the proxy target in `frontend/vite.config.*`.

## Prerequisites

Install:

- Java 17
- Maven
- Node.js and npm
- MySQL Server
- Git

## Local setup

### 1. Clone the repository

```bash
git clone https://github.com/puri-13/TrustVault.git
cd TrustVault
```

### 2. Create the database

In MySQL, run:

```sql
CREATE DATABASE trustvault;
```

The local profile defaults to `jdbc:mysql://localhost:3306/trustvault`. Override it with `DB_URL` if needed. Schema update is enabled for local development only.

### 3. Configure database credentials

The application configuration uses environment variables for the database username and password. In **PowerShell**, set them in the same terminal used to start Maven:

```powershell
$env:DB_USERNAME = "root"
$secure = Read-Host "Enter your MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
Remove-Variable secure
```

Use a least-privilege MySQL account where possible. Never commit passwords or put real credentials in this README.

### 4. Start the backend

From the repository root (the folder containing `pom.xml`), in PowerShell:

```powershell
mvn test
mvn spring-boot:run
```

The local backend is configured for port `8081`. Keep this terminal open while using the application.

### 5. Start the frontend

Open a second PowerShell terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open the local URL printed by Vite in the terminal. The port can vary if the default port is already in use.

With no `VITE_API_BASE_URL` set, local frontend requests use relative `/api` paths and Vite forwards them to `http://localhost:8081`.

## API overview

The following routes are present in the current implementation. Confirm the exact request fields and response schemas in the controller classes before relying on this list.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/health` or `/api/health/live` | Public process liveness; does not check database or file storage |
| `GET` | `/api/health/ready` | Public readiness probe; checks `SELECT 1` and storage-directory writability; returns only `UP`/`DOWN` statuses |
| `POST` | `/api/auth/register` | Public registration; always creates an `OWNER` |
| `GET` | `/api/auth/me` | Return the authenticated username and server-assigned role |
| `GET` | `/api/documents` | List documents owned by the authenticated user |
| `POST` | `/api/documents` | Upload multipart fields `name`, `category`, and `file`; requires `OWNER` |
| `GET` | `/api/documents/{id}` | Read document metadata if owner or shared with `READ`/`EDIT` |
| `GET` | `/api/documents/{id}/download` | Download if owner or shared with `READ`/`EDIT` |
| `GET` | `/api/documents/shared-with-me` | List documents shared with the authenticated user |
| `POST` | `/api/documents/{id}/permissions` | Grant `READ` or `EDIT`; owner only; JSON `username`, `permission` |
| `GET` | `/api/documents/{id}/permissions` | List permissions; owner only |
| `DELETE` | `/api/documents/{id}/permissions?username={username}` | Revoke permission; owner only |
| `GET` | `/api/audit` | Read audit events; requires the `ADMIN` role |

All protected endpoints use HTTP Basic authentication on each request. The health and registration endpoints are public. Inaccessible document resources return `404` to avoid confirming their existence.

## Security notes

- The backend checks ownership or permission before returning document metadata and file content.
- The backend returns `404 Not Found` for inaccessible documents in the tested cases, avoiding disclosure of whether another user's document exists.
- Passwords are encoded using BCrypt.
- File uploads are limited to 10 MB and extensions PDF, TXT, PNG, JPG, JPEG, and DOCX. PDF, PNG, and JPEG signatures are checked; TXT must be valid UTF-8 without null bytes; DOCX is checked for required package entries with expanded-size and entry-count limits. The client-provided MIME type is not trusted. These checks do not scan for malware or fully parse every format.
- Public registration is limited in memory to 10 requests per client address per 15 minutes by default. Configure `REGISTRATION_RATE_LIMIT_MAX_ATTEMPTS` and `REGISTRATION_RATE_LIMIT_WINDOW_SECONDS` to change these values. Limits are per process and reset on restart; multiple instances do not share a counter. Behind a reverse proxy, `getRemoteAddr()` may identify the proxy rather than the user. Configure trusted proxy address forwarding at the hosting/container layer before relying on per-client limits; the app does not trust arbitrary `X-Forwarded-For` headers.
- Keep database credentials outside source control.
- Local filesystem storage is suitable for a demonstration, but production deployment needs carefully configured persistent storage, backups, access controls, and operational monitoring.
- HTTP Basic authentication is only appropriate over HTTPS outside local development. CORS is restricted to configured origins and does not replace authentication.
- The UI temporarily keeps the Basic-auth username/password in React memory for API calls; it does not persist credentials to browser storage.
- Review `THREATS.md` for known risks and mitigations.

## Automated tests

Run the complete backend test suite from the repository root:

```powershell
.\mvnw.cmd test
```

The suite currently has 20 tests: 7 Spring MVC/JPA integration tests using an in-memory H2 database and isolated `target/trustvault-test-storage`, plus 13 unit tests for registration, rate limiting, readiness, and file validation. The last run during this review passed with 0 failures and 0 errors. It covers registration validation/duplicates/password hashing and role assignment, authentication and current-user role reporting, username uniqueness, ownership, permission grant/revoke, READ download access and post-revocation denial, file signatures and spoofed MIME types, invalid/oversized uploads, rate limiting, CORS preflight, health/readiness, and admin-only audit access. It does not prove production database compatibility or persistent storage behavior.

## Project checklist before submission

- [ ] Run `.\mvnw.cmd test` and confirm `BUILD SUCCESS`.
- [ ] Run `npm install` and `npm run build` inside `frontend`.
- [ ] Start backend and frontend from clean terminals and test the demo.
- [ ] Test upload, download, sharing, revocation, and unauthorized access.
- [ ] Check that no passwords, private keys, test credentials, or temporary JSON files are committed.
- [ ] Verify every feature mentioned in this README exists and works.
- [ ] Add the actual deployed demo URL and screenshots here only after deployment is confirmed.
- [ ] Review `THREATS.md` and `PROMPTS.md` for accuracy.

## Deployment

No production backend URL is configured. Do not set a placeholder as the production API URL.

### Required environment variables

For the backend, configure these in the hosting provider without committing their values:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`: hosted MySQL JDBC URL, for example the provider's `jdbc:mysql://...` endpoint
- `DB_USERNAME` and `DB_PASSWORD`: a least-privilege database account
- `TRUSTVAULT_CORS_ALLOWED_ORIGINS`: exact deployed frontend origin(s), comma-separated, such as `https://your-app.vercel.app`; do not include a path
- `TRUSTVAULT_STORAGE_DIR`: path on a persistent mounted volume
- `PORT`: optional; defaults to `8081`
- `REGISTRATION_RATE_LIMIT_MAX_ATTEMPTS` and `REGISTRATION_RATE_LIMIT_WINDOW_SECONDS`: optional in-memory registration limit settings; defaults are 10 attempts and 900 seconds

For Vercel, set `VITE_API_BASE_URL` to the deployed backend origin (scheme and host only, with no `/api` suffix), then redeploy. This URL is public configuration, not a secret. The project does not contain a backend provider URL or credentials.

### Deployment sequence

1. Provision hosted MySQL and a persistent volume for the backend. This application stores files on local disk; an ephemeral container filesystem is not sufficient. Configure the host's readiness probe to call `/api/health/ready` and its liveness probe to call `/api/health/live` (or retain `/api/health` for compatibility).
2. Review the current entity/schema against the hosted database and apply a reviewed migration manually. Ensure the `app_users.username` column has the unique constraint `uk_app_users_username`; first check for existing duplicate usernames. No migration tool is configured. Production uses `ddl-auto=validate` and will not create or update tables.
3. Configure the backend variables above, restrict database network access, enable HTTPS, deploy, and check `GET /api/health`. This liveness endpoint does not verify DB connectivity or file-volume writability.
4. Set Vercel `VITE_API_BASE_URL` and configure the backend CORS origin to the exact Vercel/custom frontend origin. Redeploy the frontend and verify registration, login, upload, share/revoke, and download against the deployed services.
5. Confirm backups and restore procedures for both MySQL and stored files. No deployment or live end-to-end check has been performed in this repository review.

Local `application.properties` retains `ddl-auto=update` for development. Avoid running the production profile until a production database and storage directory are intentionally provisioned.

**Demo URL:** Add the verified URL only after deployment and end-to-end testing.

**Repository:** https://github.com/puri-13/TrustVault

## License

Add a license only if one has been chosen for this project.

# TrustVault

TrustVault is a document-management application built to demonstrate authenticated access, document ownership, controlled sharing, file upload/download, and audit logging.

> **Project status:** Features described below are based on the current implementation and manual tests performed during development. Review the checklist and replace any placeholder values before submitting. Do not describe the application as production-secure or deployed unless you have verified that.

## Features

- HTTP Basic authentication for protected API endpoints.
- User roles: `OWNER`, `VIEWER`, and `ADMIN`.
- Authenticated owners can upload documents.
- Document metadata includes a name, category, owner, and upload time.
- Owners can grant and revoke document permissions.
- Shared users can access documents according to their `READ` or `EDIT` permission.
- Authorized users can download stored files.
- Server-side file storage uses generated stored filenames rather than trusting a client-provided path.
- Audit events are recorded for implemented actions such as uploads and successful downloads.
- Administrator-only audit endpoint protected by role authorization.
- React frontend built with Vite.

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

The application is configured to use the `trustvault` database. Check `src/main/resources/application.properties` for the exact JDBC URL and other settings.

### 3. Configure database credentials

The application configuration uses environment variables for the database username and password. In **PowerShell**, set them in the same terminal used to start Maven:

```powershell
$env:DB_USERNAME = "root"
$secure = Read-Host "Enter your MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
Remove-Variable secure
```

Use the MySQL account appropriate for your local setup. Never commit passwords or put real credentials in this README.

### 4. Start the backend

From the repository root (the folder containing `pom.xml`):

```powershell
mvn test
mvn spring-boot:run
```

The local backend is configured for port `8081`. Keep this terminal open while using the application.

### 5. Start the frontend

Open a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite in the terminal. The port can vary if the default port is already in use.

## API overview

The following routes are present in the current implementation. Confirm the exact request fields and response schemas in the controller classes before relying on this list.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Register an allowed non-admin user |
| `GET` | `/api/documents` | List the authenticated user's documents |
| `POST` | `/api/documents` | Upload a document |
| `GET` | `/api/documents/{id}` | Read document metadata if authorized |
| `GET` | `/api/documents/{id}/download` | Download a document if authorized |
| `GET` | `/api/documents/shared-with-me` | List documents shared with the authenticated user |
| `POST` | `/api/documents/{id}/permissions` | Grant a document permission |
| `GET` | `/api/documents/{id}/permissions` | List permissions for an owned document |
| `DELETE` | `/api/documents/{id}/permissions?username={username}` | Revoke a permission |
| `GET` | `/api/audit` | Read audit events; requires the `ADMIN` role |

Most endpoints require HTTP Basic authentication. The registration endpoint is public, but registration must not permit callers to assign themselves the `ADMIN` role.

## Security notes

- The backend checks ownership or permission before returning document metadata and file content.
- The backend returns `404 Not Found` for inaccessible documents in the tested cases, avoiding disclosure of whether another user's document exists.
- Passwords are encoded using BCrypt.
- File uploads have configured size limits; check the storage service for its full validation rules.
- Keep database credentials outside source control.
- Local filesystem storage is suitable for a demonstration, but production deployment needs carefully configured persistent storage, backups, access controls, and operational monitoring.
- HTTP Basic authentication should only be used over HTTPS outside a local development environment.
- Review `THREATS.md` for known risks and mitigations.

## Tests and manual verification

During development, the following manual checks were observed to succeed:

- An owner downloaded a document and received HTTP `200`.
- A user with `READ` permission accessed and downloaded the shared document.
- A user without permission received `404` for that document.
- An owner revoked the user's permission; subsequent access returned `404`.
- A non-admin user received `403` from `/api/audit`.
- `mvn test` reported `BUILD SUCCESS` after database credentials were set in the test terminal.

These are development checks, not proof of complete security. Run the tests again on the final code and add automated tests for authorization, upload validation, permission revocation, and audit access.

## Project checklist before submission

- [ ] Run `mvn test` and confirm `BUILD SUCCESS`.
- [ ] Run `npm install` and `npm run build` inside `frontend`.
- [ ] Start backend and frontend from clean terminals and test the demo.
- [ ] Test upload, download, sharing, revocation, and unauthorized access.
- [ ] Check that no passwords, private keys, test credentials, or temporary JSON files are committed.
- [ ] Verify every feature mentioned in this README exists and works.
- [ ] Add the actual deployed demo URL and screenshots here only after deployment is confirmed.
- [ ] Review `THREATS.md` and `PROMPTS.md` for accuracy.

## Deployment

**Demo URL:** _Add the real deployed URL after deployment and testing._

**Repository:** https://github.com/puri-13/TrustVault

## License

Add a license only if one has been chosen for this project.

# AI Assistance Log — TrustVault

## Purpose

This file documents how AI assistance was used during TrustVault development. It should be kept accurate and updated with the actual prompts and decisions used by the developer. Do not claim that AI-generated code was fully correct without testing it.

## Areas where AI assistance was used

During development, AI assistance was used to help with:

- Reviewing and improving Spring Boot document upload, download, and access-control code.
- Debugging PowerShell `curl.exe` requests and JSON quoting.
- Interpreting Maven test failures and locating Surefire reports.
- Reviewing authorization behavior for owners, shared users, and the admin-only audit endpoint.
- Preparing project documentation and a security threat model.

Replace or supplement these summaries with the actual prompts used in the project, especially any prompts required by the hackathon rules.

## Example issue: malformed permission request

**Task:** Grant `READ` permission to a user for a document.

**Problem encountered:** A PowerShell request returned HTTP `400`. The backend log showed a JSON parse error because the request body was malformed.

**AI-assisted diagnosis:** The issue was in JSON/PowerShell quoting, not necessarily the permission controller.

**Fix applied:** Put the JSON request body in a temporary file and pass it to `curl.exe` using `--data-binary "@permission-request.json"`.

**Verification:** The permission request succeeded, and the shared user could access the document. The temporary JSON files were removed before committing.

## Example issue: Maven test failure

**Task:** Run the backend test suite.

**Problem encountered:** `applicationContextLoads` failed because Hibernate could not determine the database dialect from JDBC metadata.

**AI-assisted diagnosis:** The test process did not have the database environment variables available.

**Fix applied:** Set `DB_USERNAME` and `DB_PASSWORD` in the PowerShell session before rerunning `mvn test`.

**Verification:** Maven reported `BUILD SUCCESS`.

## Example issue: download filename

**Task:** Preserve a file extension in the download response.

**Problem encountered:** The response filename was `Upload Test`, without `.txt`.

**AI-assisted change:** Use the extension from the server-generated stored filename when constructing the download filename.

**Verification:** The download response returned HTTP `200` and `Content-Disposition: attachment; filename="Upload Test.txt"`.

## Testing and developer review

AI suggestions were treated as assistance, not as proof of correctness. Changes were reviewed and checked through manual API requests and Maven tests. The developer should continue verifying all generated or edited code, especially authorization logic and filesystem handling.

## Actual prompts and corrections

Add the actual prompts used for important development tasks here. For each substantial AI-assisted task, record:

1. **Prompt/task:** What was asked.
2. **AI suggestion:** What was proposed.
3. **Issue or mistake:** What was incorrect, incomplete, or did not work, if applicable.
4. **Correction:** What was changed.
5. **Verification:** How the result was tested.

Do not invent AI mistakes or claim a correction was tested if it was not. Follow the hackathon's exact requirements for this file.

## Repository review session (2026-10-22)

- **Prompt/task:** Inspect the existing TrustVault repository, report the audit before edits, then proceed in small verified stages toward secure backend behavior, automated tests, frontend API integration, deployment configuration, and accurate documentation. No external deployment or credential sharing was authorized.
- **AI assistance:** Reviewed the checked-in controllers, services, security configuration, properties, frontend requests, and tests. Added a configurable frontend API base URL and document download actions; added explicit CORS configuration, production profile settings, a liveness endpoint, sanitized validation/upload errors, and H2/Mockito tests. Updated README deployment and test notes.
- **Issue and correction:** The first test implementation parsed an upload response with Jackson, which is not available on this project test classpath; two tests errored. Replaced parsing with a repository lookup. The focused test then passed.
- **Command correction:** The first `npm run build` was invoked from the repository root and failed because `package.json` is in `frontend/`. Reran from `frontend/`; production build passed.
- **Command correction:** PowerShell did not resolve `mvnw.cmd` from the current directory without an explicit relative path. Reran it as `.\mvnw.cmd`; the Maven compile and verification commands passed.
- **Verification:** `npm run build` and `npm run lint` passed. `mvn verify` passed with 20 tests, 0 failures, and 0 errors using the test-only H2 profile and `target/trustvault-test-storage`.
- **Additional fixes:** Corrected an accidental `=` at the end of the local storage path after confirming the normal storage directory existed and the suffixed directory did not. Added server-provided current-user role reporting instead of a username-based viewer guess, UTF-8 Basic-auth encoding, and a database username uniqueness constraint with duplicate responses mapped to HTTP 409.
- **Deployment note:** Adding the username uniqueness constraint to an existing database requires a reviewed migration and duplicate-data check before production starts with schema validation.
- **Security review follow-up:** Added content signature/format checks for uploads, including bounded DOCX package inspection; an in-memory per-client registration throttle; and separate liveness/readiness routes that return no internal database or filesystem details. Readiness checks database connectivity and storage-directory writability.
- **Rate-limit limitation:** The limiter is process-local, resets at restart, and is not shared across instances. Behind a reverse proxy, the host must provide trusted client-address forwarding; this application intentionally does not trust arbitrary forwarded headers.
- **Verification update:** `.\mvnw.cmd -Dtest=FileStorageServiceTests,BackendApplicationTests,RegistrationRateLimiterTests,UserControllerTests,HealthControllerTests test` passed with 14 tests before the filter-level rate-limit test was added. The final `.\mvnw.cmd verify` passed with 20 tests, 0 failures, and 0 errors. `npm run lint` and `npm run build` both passed.
- **Command correction:** The first filter test compile failed because the Servlet API has no `SC_TOO_MANY_REQUESTS` constant. Replaced it with status `429`; the focused filter test passed afterward.
- **Limitations:** No hosted database, backend, persistent production volume, deployment URL, or end-to-end production run was available. The production profile and deployment instructions are prepared but deployment remains unverified.

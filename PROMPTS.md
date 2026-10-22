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

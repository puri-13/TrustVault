# TrustVault Threat Model

## Purpose and scope

This document identifies important security risks for TrustVault, a document-management application with authenticated users, ownership, document sharing, file upload/download, and audit logging.

This is a practical project threat assessment, not a formal security certification. Mitigations listed as planned or requiring review must not be represented as already implemented.

## Assets to protect

- Uploaded documents and their contents.
- User credentials and password hashes.
- Document ownership and sharing permissions.
- User and document metadata.
- Audit events.
- Database credentials and filesystem storage.
- Integrity and availability of the application.

## Trust boundaries

1. Browser/frontend to backend API.
2. Backend API to MySQL database.
3. Backend API to local file storage.
4. Authenticated owner to shared viewer/editor.
5. Ordinary user to administrator-only functionality.
6. Developer workstation and environment variables to source control.

## Threats and mitigations

| Threat | Potential impact | Current mitigation / status | Remaining work |
|---|---|---|---|
| Unauthorized document access | Confidential files exposed | Backend checks ownership or `READ`/`EDIT` permission for document access and downloads; manual unauthorized-access test returned `404` | Add automated tests for every document endpoint and each role |
| Permission remains after revocation | Former recipient retains access through the API | Revocation was tested; subsequent metadata access returned `404` | Test downloads and all related endpoints after revocation |
| Privilege escalation to `ADMIN` | Ordinary user reads audit data or performs admin actions | Registration service rejects `ADMIN`; audit endpoint uses `@PreAuthorize("hasRole('ADMIN')")`; non-admin request returned `403` | Define a controlled production admin-provisioning procedure; test positive admin access |
| Password exposure | Account compromise | Passwords are encoded using BCrypt; database password is configured through environment variables | Enforce strong passwords, protect secrets, and use HTTPS outside local development |
| Malicious or oversized upload | Storage exhaustion or unsafe file handling | Multipart size limits are configured; storage service validates uploads | Review extension/content validation, MIME spoofing, quotas, malware scanning needs, and cleanup |
| Path traversal | Read/delete files outside storage directory | Storage service checks stored names and validates the resolved file path | Add automated tests for `..`, slash/backslash, missing files, and symbolic-link edge cases |
| Stored-file loss or tampering | Documents become unavailable or modified | Files are stored on the server filesystem | Restrict filesystem permissions, back up files, and consider integrity checks |
| Unauthorized API requests | Data access or modification | Protected endpoints require authentication; role checks are applied to admin audit access | Audit every controller for missing ownership/permission checks |
| Audit records missing or misleading | Incident investigation becomes unreliable | Implemented actions call the audit service; successful upload/download events are recorded | Verify coverage for grants, revocations, deletes, denied attempts, and failures; restrict audit database access |
| Sensitive information in logs | Credentials or private document data exposed | Audit details currently describe actions and IDs rather than file contents | Ensure passwords, tokens, and document content are never logged |
| Cross-site request or transport risks | Unauthorized requests or credentials intercepted | CSRF is disabled and the app uses stateless HTTP Basic authentication | HTTP Basic must be used over HTTPS outside local development; review CORS and CSRF assumptions if authentication changes |
| Database compromise | User and document metadata exposed or altered | Credentials are configured outside source code | Use a least-privilege database account, backups, access restrictions, and secret rotation |
| Dependency vulnerabilities | Exploitation through outdated libraries | Maven and npm dependency manifests are present | Review dependency versions and run appropriate vulnerability checks before release |

## Security design choices

- **Deny by default:** protected API endpoints require authentication unless explicitly permitted.
- **Ownership and sharing:** access to a document should be granted only to its owner or a user with the appropriate permission.
- **Least privilege:** `READ` and `EDIT` permissions should be enforced consistently. Confirm whether `EDIT` is intentionally allowed to download and read metadata.
- **No public admin self-registration:** the public registration flow must not accept the `ADMIN` role.
- **Path safety:** client input must never be treated as an unrestricted filesystem path.
- **Minimal disclosure:** inaccessible document IDs return `404` in the tested access-control paths.

## Verification checklist

- [ ] Test anonymous access to all protected endpoints.
- [ ] Test owner, viewer with `READ`, viewer with `EDIT`, unrelated user, and admin roles.
- [ ] Test access immediately after permission revocation.
- [ ] Test upload size limits, empty files, invalid content, and unusual filenames.
- [ ] Test path traversal and missing stored files.
- [ ] Verify audit events for successful and failed sensitive actions.
- [ ] Verify only admins can read audit records.
- [ ] Verify secrets and temporary test files are excluded from Git.
- [ ] Use HTTPS and appropriate production configuration before public deployment.

## Limitations

This threat model is based on a development review and the tests described in the project documentation. It does not prove that all risks have been eliminated. A production deployment would require additional testing, operational controls, monitoring, backup and recovery procedures, and a review of the full codebase and configuration.

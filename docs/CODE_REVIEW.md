# Source review and fixes — 23 October 2026

Reviewed all Java application classes, database schema and initialization, JSP pages/fragments, assets, helper scripts, and test setup.

## Bugs resolved

| Finding | Correction |
|---|---|
| Announcements allow 1,000 characters but notifications store only 600, causing publication rollback. | Preserve the full announcement and store a Unicode-safe notification preview of at most 600 characters. |
| Invitation responses read a request before locking its project; MySQL repeatable-read snapshots could preserve an outdated member count while waiting. | Use READ COMMITTED transactions with the existing project/request locks, so competing acceptances recheck current capacity. |
| Login allowed passwords beyond BCrypt's 72-byte boundary even though registration rejected them. | Apply the same UTF-8 byte-length validation before login verification. |
| Embedded servers without catalina.base or UPLOAD_DIR could fail to locate upload storage. | Fall back to the absolute runtime/uploads directory outside the web root. |
| Valid MIME headers with charset parameters could reject text uploads. | Normalize the MIME media type before comparing it against allowed types. |
| Upload cleanup errors could replace the intended validation response with an uncaught server error. | Log cleanup failures without replacing the user's response. |
| Archived projects still showed task-status forms. | Include project status in task results, hide write controls, and retain the server-side archive restriction. |
| Saving an unchanged task status created duplicate notifications and activity entries. | Treat unchanged status as an idempotent no-op. |
| Assignments could target inactive members. | Require an active account in addition to project membership. |
| Re-reading a notification could fail with MySQL useAffectedRows=true. | Accept an already-read notification only after verifying its session recipient. |
| Invalid admin account-state input silently selected deactivation. | Validate that the submitted state is exactly true or false. |
| First-administrator setup needed serialization independent of transaction isolation. | Hold a schema-specific advisory lock through the creation transaction and commit. |
| JDBC configuration files were decoded with Properties' ISO-8859-1 stream default. | Read configuration as UTF-8 for non-ASCII credentials. |
| Integration setup hardcoded MySQL root, host and port and discarded existing environment overrides. | Reuse configured initialization, permit an explicit test URL, validate the schema, and restore prior environment values. |
| A commit/connection error could be reported as a guarantee that nothing was saved. | Ask the user to reload and verify state; preserve original exceptions if rollback also fails. |

## Verification

- Maven package: successful; 8 database-independent tests passed, 8 opt-in cases skipped in this invocation.
- Jasper compilation: all 17 JSP pages and their included fragments compiled with 0 errors.
- Disposable MySQL review: 7 tests passed with 0 failures, including existing workflow and HTTP tests plus 4 new database regressions.
- Repeated the database review with useAffectedRows=true: all 7 tests passed again.
- HTTP checks include registration, session handling, invitation acceptance, persistent team membership, tasks, discussion, MIME-parameter uploads, private downloads, long announcements and read-only archived tasks.
- The last-place regression forces both recipients to wait for the same project lock and verifies only one can join a two-person team.
- PowerShell parsing: all helper scripts have 0 syntax errors.
- ReviewRunner removes only its generated collabhub_review_<UUID> schema in a finally block. The application database was not used for testing, and student_hub_test was not recreated.

These checks cover the reviewed scenarios; deployment-specific configuration and the previously documented limitations remain in README.md. Database/HTTP tests require explicit opt-in and MySQL access.

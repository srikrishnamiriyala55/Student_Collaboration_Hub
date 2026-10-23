# Verification and practical acceptance test plan

## Automated verification

Build: `mvn package`. Source compiles for Java 8; runtime verification uses installed JDK 21, MySQL 8.0.46 and embedded Tomcat 9.0.118.

Normal `mvn package` also runs `JspCompilationTest`, which compiles all 17 JSP pages and their fragments with Jasper without a database. The database/HTTP integration suites remain opt-in.

`DatabaseInitializerTest` checks database URL parsing, safe schema names, and packaging of the non-destructive schema without connecting to MySQL. Its optional real first-run check is enabled with `COLLABHUB_BOOTSTRAP_CHECK=true`; it creates a UUID-named `collabhub_setup_check_...` schema, initializes it twice, verifies data preservation, and drops only that temporary schema in a finally block. It does not recreate student_hub_test.

Explicit integration command from an interactive PowerShell terminal:

```powershell
$env:DB_PASSWORD='root123'
.\scripts\test-integration.ps1
```

Tests require a separate `student_hub_test` database. The script initializes the same schema under that name and forces the integration JDBC URL. Tests refuse to run against a different database. Generated accounts use `@example.test` and randomized suffixes; test data remains isolated for investigation. No application data is deleted. The rollback test temporarily creates/removes a deliberately failing notification trigger in the test database only. Do not run multiple copies of this test suite simultaneously because they share that trigger name and local HTTP port 18089.

Tests also accept a disposable name matching `collabhub_review_<32 lowercase UUID hex characters>`. `com.collabhub.util.ReviewRunner` requires this stricter name, initializes the schema, runs WorkflowTest, WebTest and RegressionTest, and drops only that schema afterward. This was the method used for the latest review without recreating student_hub_test. See CODE_REVIEW.md and test-results/review-summary.txt. The integration script accepts `-TestDatabaseUrl` for a custom MySQL host/port and restores prior process environment settings when finished.

`WorkflowTest.invitationsTasksAndPrivacy` verifies valid/invalid login, duplicate email, self/duplicate/invalid/unauthorized invitations, recipient privacy, acceptance and repeat handling, persisted membership after fresh authentication, rejection, task/member validation, completion progress, discussions, private notifications, badges, leaderboard, recommendation scoring, archive authorization, admin authorization, and file metadata access.

`WorkflowTest.concurrentAcceptanceAndNotificationRollback` runs competing acceptance calls and asserts exactly one succeeds, one membership exists, and a forced notification failure rolls back acceptance and membership together.

`WebTest.httpWorkflowAndEveryPage` starts a real Tomcat/Jasper HTTP server, renders public and authenticated JSPs, verifies protected-page redirects/CSRF denial, logs in separate clients, creates a project through HTTP, verifies HTML escaping, sends/accepts an invite, checks members after logout/login, assigns tasks, posts discussion, uploads/downloads a file, denies outsider download/admin access, renders all main routes and the admin dashboard, posts an announcement and verifies deactivation immediately invalidates authenticated access.

## Manual test cases and expected results

| Action | Expected result |
|---|---|
| Register required fields + matching password | Account created with BCrypt hash; sign-in page opens. |
| Duplicate email, malformed email, short/mismatched password | Escaped validation message; no duplicate student. |
| Login valid / invalid | Dashboard + rotated session / generic error. |
| Visit dashboard logged out; logout then reload | Redirect to login; old session unusable. |
| Create project and inspect details | Owner appears as OWNER; database persists after refresh. |
| Search title/category/technology/status | Matching database projects only. |
| Edit project as owner / outsider | Saved / denied with no change. |
| A invites B from directory | Pending request + B notification committed together. |
| Repeat, self, nonexistent project/student, already member | Rejected with no new request/membership. |
| Outsider C submits B's request ID | Denied; request remains pending. |
| B accepts, refreshes details and logs in again | ACCEPTED; one B membership visible on correct project. |
| Two acceptance calls at once | One success; second rejected; one membership. |
| B declines another invitation | REJECTED; no membership; A notified. |
| Force notification insert failure in test DB | Entire acceptance rolls back; still pending; no membership. |
| Create/assign/edit task to member | Task persists; assignee notified. |
| Assign to outsider | Denied; no task write. |
| Mark task completed | Progress updates; assignee receives Finisher badge once. |
| View/read own notifications; attempt another ID | Own history/read persists; another recipient's notification denied. |
| Team discussion / outsider discussion | Message persists and teammates notified / denied. |
| Upload/download allowed file as member | Stored outside web root; metadata persisted; attachment downloaded. |
| Invalid extension, fake image signature, >10 MB | Rejected; no file metadata committed. |
| Download file as outsider or tamper with ID | Denied. |
| Repeat badge-triggering activity | No duplicate achievement; leaderboard uses stored records. |
| Skills Java, MySQL; interest Education | Matching project score 8; fallback described when missing. |
| Student calls admin route directly | HTTP 403. |
| Admin searches/deactivates student | Only matching results; inactive user's next request redirects to login. |
| Admin announces / archives | Persistent notifications / archived project preserved and read-only. |
| Stop MySQL; load protected page | Generic unavailable response; no credentials/stack trace rendered. |

Final automated results are recorded in `docs/VERIFICATION.md` and the generated `target/surefire-reports` files. Visual checks are documented there separately from automated assertions.

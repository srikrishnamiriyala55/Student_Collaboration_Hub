# Verification results — 23 October 2026 (Asia/Kolkata)

Final command: Maven `package` with `COLLABHUB_INTEGRATION=true`, `DB_URL` targeting `student_hub_test`, and the local MySQL password supplied through the environment. **Exit code 0.** The resulting application WAR is `target/Student_Collaboration_Hub.war`.

| Test suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| WorkflowTest | 2 | 0 | 0 | 0 |
| WebTest | 1 | 0 | 0 | 0 |

These are three broad integration tests with many assertions, not three individual feature checks. Coverage includes transaction rollback, concurrent acceptance, fresh-login membership persistence, DAO authorization, real HTTP/Servlet/JSP rendering, multipart transfer and access controls. See TESTING.md and the actual test source for exact assertions. Test reports are in `target/surefire-reports`; copies accompany the source ZIP under `docs/test-results`.

Environment actually used: Windows, installed JDK 21.0.11, Maven 3.9.16, MySQL 8.0.46 and embedded Tomcat 9.0.118. Application compiled using Java 8 release compatibility and `javax.servlet` 4.0.1. A separate Java 8 JVM and the Eclipse graphical deployment workflow were not executed; instructions are provided for them.

Browser inspection: homepage and sign-in page visually inspected at the browser's normal desktop viewport. Branding, navigation, hero layout and sign-in form render correctly with the local stylesheet. Authenticated JSPs were compiled and rendered by the HTTP integration test; a manual visual audit of every authenticated screen or every mobile breakpoint is not claimed.

The application was launched against `student_hub` on local port **8081**, because 8080 was already occupied. Final database inspection confirmed 12 tables, 4 badge definitions, **0 student accounts and 0 projects** in `student_hub`. All generated test students/projects remain in the separate `student_hub_test` database. There is no default administrator; create one through `scripts/create-admin.ps1`.

The first HTTP test runs revealed an embedded-runner classpath issue and a relative JSP fragment include issue. Both were corrected before the final passing run. Tomcat logs emit informational TLD scanning messages and Java 21 shutdown leak-detection warnings; no application test failures remained.

Follow-up UI correction: removed the public “Get started” header link; removed description clipping; improved wrapping for long text and compact card layouts; enlarged small text; and stacked the registration/login layout on narrower screens. Registration was visually checked at narrow and desktop widths, including all fields and the footer. The complete integration suite was rerun after these changes.

Known functional limits are documented in README.md. Notifications use HTTP refresh, recommendations use rule-based scoring, and file validation is not malware scanning. No external service integrations are claimed.

Eclipse follow-up: inspected the live Problems view and found 10 CollabHub validation errors caused by five EL list literals. Replaced these with JSTL comma-separated iteration lists; after project refresh, all 10 error markers disappeared. The remaining 52 errors in that workspace belong to the unrelated tripnest project. Added servlet serialVersionUID fields and removed an unused test import. Added a database-independent Jasper compilation test covering all 17 JSPs. `mvn package` passes, with JSP compilation reporting 0 errors. The deleted student_hub_test database was not recreated; database/HTTP suites are skipped in this follow-up run, and earlier integration results above are historical.

Automatic database setup follow-up: added a Servlet startup listener and packaged the existing canonical SQL script as a classpath resource. `DatabaseInitializerTest` passed all 4 checks, including a real MySQL first-run setup in a newly generated temporary schema, verification of 12 tables and 4 badges, a second initialization preserving an inserted record without duplicate badges, and automatic removal of that temporary schema. Neither student_hub nor student_hub_test was used for that test. URL validation and schema safety checks also passed. Maven packaging and compilation of all 17 JSPs pass.

# CollabHub — Student Project Collaboration Hub

Complete Java Servlet/JSP application for Tomcat 9, JDBC, and MySQL. The existing Eclipse Maven WAR project was retained. The workspace contained only the starter POM/web.xml, so a new HTML/JSP frontend with simple CSS was created with your approval.

## Start locally

Run PowerShell from `C:\Users\srikr\eclipse-workspace\Student_Collaboration_Hub`:

```powershell
$env:DB_PASSWORD='root123'
.\scripts\check-db.ps1
.\scripts\start-dev.ps1
```

Open http://localhost:8081/Student_Collaboration_Hub/ . Stop the development server with Ctrl+C. The script defaults to port 8081 because port 8080 is already used on this workstation. To choose another port, run `.\scripts\start-dev.ps1 -Port 8082`. The optional development server uses embedded **Tomcat 9.0.118**, with the same Servlet/JSP APIs as Eclipse Tomcat 9. Application source compiles with Java 8 `--release 8`; builds and tests were executed using installed JDK 21.

The local `student_hub` database was initialized during implementation. It contains badge definitions, but no sample students or projects. Register two accounts to exercise collaboration. There are no default administrator credentials.

## Database setup and connection

The schema is `database/schema.sql`. It uses `CREATE ... IF NOT EXISTS` and seeds only badge definitions. It never drops/recreates databases or clears rows. For a fresh installation:

```powershell
mysql -h 127.0.0.1 -u root -p
```

At the MySQL prompt, enter:

```sql
CREATE DATABASE IF NOT EXISTS student_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE student_hub;
SOURCE C:/Users/srikr/eclipse-workspace/Student_Collaboration_Hub/database/schema.sql;
SHOW TABLES;
```

The `-p` command prompts for the password. Your local credentials are `root` / `root123`. Connection settings live in `src/main/resources/db.properties`. This workstation also has an ignored `src/main/resources/db-local.properties` with the local password. Environment variables `DB_URL`, `DB_USER`, and `DB_PASSWORD` override both files. The source ZIP excludes `db-local.properties`; set `DB_PASSWORD` when using the ZIP. In Eclipse configure environment variables on the **Tomcat server launch configuration**, rather than just the Eclipse Java build path. The local WAR is built with this workstation's private local settings.

Default URL: `jdbc:mysql://127.0.0.1:3306/student_hub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8`.

`scripts/check-db.ps1` verifies connectivity and queries the student count before launching the web app. Connector/J 8.4.0 is declared in `pom.xml` and packaged automatically; do not add a second Connector JAR manually. Runtime dependencies also include JSTL 1.2 and BCrypt 0.4. `javax.servlet-api` is provided by Tomcat. Test-only dependencies include JUnit and embedded Tomcat/Jasper; they are excluded from the deployed WAR.

Existing schemas with different columns require an explicit migration; `IF NOT EXISTS` is not a migration tool. In this workspace `student_hub` did not exist before initialization.

## Eclipse and Tomcat 9

1. If moving to another computer or replacing an older copy, back up the original folder first. The generated ZIP contains the complete source and Eclipse project metadata; retain your own server/workspace settings.
2. In Eclipse Enterprise edition choose **File → Import → Maven → Existing Maven Projects**, select the extracted project root containing `pom.xml`, and finish. For this existing workspace, refresh the project instead of importing a duplicate.
3. Right-click project → **Maven → Update Project**. Maven downloads JDBC, JSTL and BCrypt dependencies into the Maven Dependencies container.
4. In **Window → Preferences → Server → Runtime Environments**, add Apache Tomcat v9.0 at `C:\Program Files\Apache Software Foundation\Tomcat 9.0`. Select the installed JDK 21 to run Tomcat. Keep application compiler compliance at Java 8 (project facets: Java 1.8, Dynamic Web Module 4.0).
5. In **Project Properties → Targeted Runtimes** select Tomcat 9. Do not select Tomcat 10/11; this app uses `javax.servlet`.
6. In **Deployment Assembly**, source `/src/main/webapp` maps to `/`, Java/resources to `/WEB-INF/classes`, and Maven Dependencies to `/WEB-INF/lib`. The existing Maven/WTP configuration retains these mappings. Maven Update Project refreshes them.
7. Initialize the database as above. In the Tomcat server's launch configuration set `DB_PASSWORD=root123` and optionally `UPLOAD_DIR=C:\Users\srikr\eclipse-workspace\Student_Collaboration_Hub\runtime\uploads`. Uploads must stay outside `src/main/webapp`.
8. Choose **Project → Clean**, then build. Or run `mvn package` from the root. The resulting WAR is `target/Student_Collaboration_Hub.war`.
9. In Servers view, **Add and Remove** → add Student_Collaboration_Hub. Set its context root to `Student_Collaboration_Hub` if necessary. Start the server, then open `http://localhost:8080/Student_Collaboration_Hub/`.
10. Register Student A and Student B. Use different browsers/private windows for separate sessions. A creates a project, then opens Find teammates and invites B to that project. B accepts in Notifications. Refresh Project Details: B appears in The team, with membership date. Log out and back in; membership remains.
11. Add tasks assigned to members, post discussion updates, upload a permitted file, and mark notifications read. Refresh the pages to verify MySQL persistence.

The WAR can also be deployed directly to a separately managed Tomcat 9. Do not run the development runner and Eclipse Tomcat on the same port simultaneously.

## Create the first administrator

From a trusted interactive terminal:

```powershell
$env:DB_PASSWORD='root123'
.\scripts\create-admin.ps1
```

Enter a name, new email, and a password of at least 12 characters when prompted. Password entry is hidden. The tool stores a BCrypt hash and refuses to bootstrap if an administrator already exists. Sign in through the normal login page; Admin navigation appears. Registration and profile editing cannot change roles. Additional administrator management is a trusted database-administrator operation; student-facing routes cannot promote users.

## Structure

```text
Student_Collaboration_Hub/
  pom.xml, .project, .classpath, .settings/    Maven/Eclipse WTP
  database/schema.sql                       12 application tables
  scripts/                                  run, check DB, bootstrap admin, test
  docs/ARCHITECTURE.md                       routes, transactions, algorithms
  docs/TESTING.md                            practical test plan/results
  src/main/java/com/collabhub/
    model/                                  session User and database Record projections
    dao/                                    User, Project, TeamRequest, Task,
                                            Community, File, Admin, Activity, Sql
    servlet/                                AppServlet, FileServlet
    filter/                                 SecurityFilter
    util/                                   Db, Validation, AdminBootstrap, DatabaseCheck
  src/main/resources/                       central DB configuration
  src/main/webapp/
    WEB-INF/web.xml
    WEB-INF/views/                          17 JSP pages and 4 shared fragments
    assets/css/style.css                    simple responsive styling
    assets/js/app.js                         archive confirmation, current navigation
  src/test/java/com/collabhub/               database/HTTP tests and dev runner
  target/                                   generated WAR and test reports
  runtime/                                  generated server files/uploads, excluded from ZIP
```

SQL lives in DAOs, never JSP. All dynamic user text is escaped with JSTL `<c:out>`. `Record` models JOIN/query projections without duplicating one entity into numerous display DTOs; `User` is the serializable authenticated-session model. AppServlet routes page and form requests to focused DAOs. FileServlet handles multipart transfers separately.

## Frontend changes and limitations

- Created a new professional navy/blue frontend after confirming that no existing frontend ZIP/pages were available. Added Home, Login, Registration, Dashboard, Projects, Project form/details, Find teammates, Notifications, My tasks, Profile, Recommendations, Leaderboard, Admin and error pages. Reused shared layout, cards, forms and navigation throughout.
- Invitations are owner/admin initiated; students discover projects but cannot submit unsolicited join applications. This implements the specified invitation workflow.
- Archive preserves history instead of permanently deleting projects. Deactivate preserves account and membership history. Archived projects are read-only for collaboration.
- Notifications/discussions refresh through normal HTTP; no WebSocket or background polling is claimed. Recommendations are rule-based, not a trained model.
- No email delivery, password-recovery service, antivirus scanner, or content sanitization of documents is included. Files are downloaded as attachments; allowed types and sizes are enforced. Only PDF/PNG/JPEG/UTF-8 TXT/CSV are supported, up to 10 MB.
- Public student discovery exposes name, skills, interests and bio. Member emails appear only for teammates/admins. Profiles and notifications are session-scoped; no arbitrary user-ID lookup endpoint exists.
- This academic application uses direct JDBC connections and unpaginated lists. Large deployments would need a connection pool and pagination. HTTPS deployment should enable secure cookies in Tomcat; HttpOnly cookies, session rotation, CSRF and server-side authorization are implemented now. Local HTTP remains usable.
- Ordinary `mvn test` skips database integration tests unless explicitly enabled. See `docs/TESTING.md` for the actual verification command and results.

## Common setup problems

| Symptom | Action |
|---|---|
| Connection refused / communications failure | Start local MySQL, verify port 3306 and run `scripts/check-db.ps1`. |
| Access denied for root | Set the password in the Tomcat launch environment; restart Tomcat after changing it. |
| Unknown database/table | Run `database/schema.sql` against the same MySQL instance as DB_URL. |
| `javax.servlet` missing | Maven Update Project and select Tomcat 9 runtime. Tomcat 10+ is incompatible. |
| JSTL class/tag not found | Confirm Maven Dependencies deploy to WEB-INF/lib; avoid duplicate manual JARs. |
| 404 for application | Verify the deployed module and context path `/Student_Collaboration_Hub`. JSPs under WEB-INF are intentionally not directly accessible. |
| Form expired/403 | Reload the page after login/session expiration, then resubmit. Cross-session CSRF tokens are invalid. |
| Port 8080 in use | Stop the other server or select another connector port. |
| Upload fails | Use a permitted type ≤10 MB and ensure Tomcat can write UPLOAD_DIR. |
| Error page without details | Check Eclipse Console / Tomcat logs for the server-side exception; users never receive stack traces. |

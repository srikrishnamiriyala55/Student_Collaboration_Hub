# CollabHub — Student Project Collaboration Hub

A student collaboration platform for discovering projects, inviting teammates, managing tasks, sharing files, and tracking achievements using Java Servlets, JSP, JDBC, and MySQL.

## Start locally

Install JDK 21, Maven, and MySQL Server 8, then start MySQL. Download or clone this repository and open PowerShell in the project folder containing `pom.xml`. Configure your own MySQL credentials:

```powershell
$env:DB_USER='root'
$env:DB_PASSWORD='your_mysql_password'
.\scripts\check-db.ps1
.\scripts\start-dev.ps1
```

Open http://localhost:8081/Student_Collaboration_Hub/ . Stop the development server with Ctrl+C. The script defaults to port 8081 because port 8080 is already used on this workstation. To choose another port, run `.\scripts\start-dev.ps1 -Port 8082`. The optional development server uses embedded **Tomcat 9.0.118**, with the same Servlet/JSP APIs as Eclipse Tomcat 9. Application source compiles with Java 8 `--release 8`; builds and tests were executed using installed JDK 21.

The first startup automatically creates `student_hub`, its 12 tables, constraints, and four badge definitions. No manual SQL import is needed with automatic setup enabled. Existing data is preserved on later starts. Register two accounts to exercise collaboration; there are no default administrator credentials. MySQL itself must be installed and running, and the configured account must have permission to create the database and tables.

## Database setup and connection

The schema is `database/schema.sql`. It uses `CREATE ... IF NOT EXISTS` and seeds only badge definitions. It never drops/recreates databases or clears rows. For a fresh installation:

**Automatic first run:** MySQL must already be installed and running. Configure `db.user` and `db.password` in `src/main/resources/db.properties` (or set `DB_USER` and `DB_PASSWORD`), then start the application normally. `DatabaseStartupListener` runs `DatabaseInitializer`, creates the database named in `db.url`, all 12 tables, constraints and badge definitions. Existing rows are preserved on subsequent starts. The same SQL script is packaged into the WAR automatically by Maven, so no absolute local file path is required. The MySQL account needs CREATE database/table permissions; the local root account normally has these. Incorrect credentials, unavailable MySQL or insufficient permissions cause startup to fail with details in server logs. Initialization is retryable after correcting the configuration; MySQL DDL is not transactional.

Automatic setup defaults to `db.initialize=true`. Set it to `false` (or `DB_INITIALIZE=false`) only when managing the schema manually. `scripts/check-db.ps1` and the administrator bootstrap also initialize missing schema before using it. No sample student or administrator credentials are created. The manual alternative is:

```powershell
mysql -h 127.0.0.1 -u root -p
```

At the MySQL prompt, enter:

```sql
CREATE DATABASE IF NOT EXISTS student_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE student_hub;
SOURCE C:/path/to/Student_Collaboration_Hub/database/schema.sql;
SHOW TABLES;
```

Replace `C:/path/to/Student_Collaboration_Hub` with your actual extracted project path when using the optional manual import. The `-p` command prompts for your laptop's MySQL password. Connection settings live in `src/main/resources/db.properties`; change `db.user` and `db.password` there if not using environment variables. An optional, ignored `src/main/resources/db-local.properties` overrides those file settings. Environment variables `DB_URL`, `DB_USER`, and `DB_PASSWORD` take precedence over both files. The source ZIP excludes `db-local.properties`. In Eclipse configure environment variables on the **Tomcat server launch configuration**, rather than just the Eclipse Java build path.

Default URL: `jdbc:mysql://127.0.0.1:3306/student_hub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8`.

`scripts/check-db.ps1` verifies connectivity and queries the student count before launching the web app. Connector/J 8.4.0 is declared in `pom.xml` and packaged automatically; do not add a second Connector JAR manually. Runtime dependencies also include JSTL 1.2 and BCrypt 0.4. `javax.servlet-api` is provided by Tomcat. Test-only dependencies include JUnit and embedded Tomcat/Jasper; they are excluded from the deployed WAR.

Existing schemas with different columns require an explicit migration; `IF NOT EXISTS` is not a migration tool. In this workspace `student_hub` did not exist before initialization.

## Eclipse and Tomcat 9

1. If moving to another computer or replacing an older copy, back up the original folder first. The generated ZIP contains the complete source and Eclipse project metadata; retain your own server/workspace settings.
2. In Eclipse Enterprise edition choose **File → Import → Maven → Existing Maven Projects**, select the extracted project root containing `pom.xml`, and finish. For this existing workspace, refresh the project instead of importing a duplicate.
3. Right-click project → **Maven → Update Project**. Maven downloads JDBC, JSTL and BCrypt dependencies into the Maven Dependencies container.
4. In **Window → Preferences → Server → Runtime Environments**, add Apache Tomcat v9.0 and select your laptop's Tomcat installation folder. Select the installed JDK 21 to run Tomcat. Keep application compiler compliance at Java 8 (project facets: Java 1.8, Dynamic Web Module 4.0).
5. In **Project Properties → Targeted Runtimes** select Tomcat 9. Do not select Tomcat 10/11; this app uses `javax.servlet`.
6. In **Deployment Assembly**, source `/src/main/webapp` maps to `/`, Java/resources to `/WEB-INF/classes`, and Maven Dependencies to `/WEB-INF/lib`. The existing Maven/WTP configuration retains these mappings. Maven Update Project refreshes them.
7. In the Tomcat server's launch configuration set `DB_USER` and `DB_PASSWORD` to your MySQL credentials. Optionally set `UPLOAD_DIR` to an absolute writable folder on your laptop outside `src/main/webapp`. Database initialization runs automatically when the application starts.
8. Choose **Project → Clean**, then build. Or run `mvn package` from the root. The resulting WAR is `target/Student_Collaboration_Hub.war`.
9. In Servers view, **Add and Remove** → add Student_Collaboration_Hub. Set its context root to `Student_Collaboration_Hub` if necessary. Start the server, then open `http://localhost:8080/Student_Collaboration_Hub/`.
10. Register Student A and Student B. Use different browsers/private windows for separate sessions. A creates a project, then opens Find teammates and invites B to that project. B accepts in Notifications. Refresh Project Details: B appears in The team, with membership date. Log out and back in; membership remains.
11. Add tasks assigned to members, post discussion updates, upload a permitted file, and mark notifications read. Refresh the pages to verify MySQL persistence.

The WAR can also be deployed directly to a separately managed Tomcat 9. Do not run the development runner and Eclipse Tomcat on the same port simultaneously.

## Create the first administrator

From a trusted interactive terminal:

```powershell
$env:DB_PASSWORD='your_mysql_password'
.\scripts\create-admin.ps1
```

Enter a name, new email, and a password of at least 12 characters when prompted. Password entry is hidden. The tool stores a BCrypt hash and refuses to bootstrap if an administrator already exists. Sign in through the normal login page; Admin navigation appears. Registration and profile editing cannot change roles. Additional administrator management is a trusted database-administrator operation; student-facing routes cannot promote users.

## Project structure

Each entry below includes a one-line description of its purpose. Generated folders are shown for reference and do not need to be copied from another laptop.

```text
Student_Collaboration_Hub/
├── README.md                                  # Overview, installation, configuration, and usage guide.
├── pom.xml                                    # Maven dependencies, Java compatibility, resources, and WAR packaging.
├── .gitignore                                 # Excludes generated files, local credentials, and runtime data from Git.
├── .project                                   # Identifies the Eclipse project and its builders.
├── .classpath                                 # Defines Eclipse source folders, libraries, and build output.
├── .settings/                                 # Stores Eclipse compiler, encoding, validation, and web-project settings.
├── database/                                  # Contains the canonical MySQL initialization script.
│   └── schema.sql                             # Creates the database, 12 tables, constraints, indexes, and badge definitions.
├── scripts/                                   # Provides PowerShell commands for local setup and operation.
│   ├── start-dev.ps1                          # Builds and starts the application with embedded Tomcat on port 8081.
│   ├── check-db.ps1                           # Initializes missing schema and verifies the MySQL connection.
│   ├── create-admin.ps1                       # Creates the first administrator using a hidden password prompt.
│   └── test-integration.ps1                   # Explicitly initializes a separate test database and runs integration tests.
├── docs/                                      # Contains implementation notes, testing instructions, and verification records.
│   ├── ARCHITECTURE.md                        # Maps features to routes, DAOs, tables, transactions, and scoring rules.
│   ├── CODE_REVIEW.md                         # Records reviewed bugs, corrections, and regression verification.
│   ├── TESTING.md                             # Describes automated tests and practical acceptance checks.
│   ├── VERIFICATION.md                        # Records completed verification and its environment and limitations.
│   └── test-results/                          # Contains saved test reports from documented verification runs.
├── src/                                       # Contains application source code, resources, views, and tests.
│   ├── main/                                  # Contains files needed to build and deploy the application.
│   │   ├── java/com/collabhub/                 # Root package for the Java backend.
│   │   │   ├── model/                         # Defines the session user and database result models.
│   │   │   │   ├── User.java                   # Stores authenticated user identity and role in the session.
│   │   │   │   └── Record.java                 # Represents database query results with typed convenience accessors.
│   │   │   ├── dao/                           # Handles SQL queries, persistence, and transactional business operations.
│   │   │   │   ├── Sql.java                    # Executes parameterized SQL and maps query results.
│   │   │   │   ├── UserDao.java                # Registers, authenticates, and updates student profiles.
│   │   │   │   ├── ProjectDao.java             # Manages projects, memberships, permissions, and recommendations.
│   │   │   │   ├── TeamRequestDao.java         # Sends, accepts, and rejects invitations with transactional consistency.
│   │   │   │   ├── TaskDao.java                # Creates, assigns, edits, and updates project tasks.
│   │   │   │   ├── CommunityDao.java           # Retrieves notifications, discussions, achievements, and leaderboard data.
│   │   │   │   ├── FileDao.java                # Stores file metadata and checks access to shared project files.
│   │   │   │   ├── AdminDao.java               # Provides administrator statistics, account management, and announcements.
│   │   │   │   └── ActivityDao.java            # Records activity, creates notifications, and awards unique badges.
│   │   │   ├── servlet/                       # Receives HTTP requests and coordinates backend operations.
│   │   │   │   ├── AppServlet.java             # Routes page requests and form submissions to the appropriate DAOs.
│   │   │   │   └── FileServlet.java            # Validates uploads and serves authorized file downloads.
│   │   │   ├── filter/                        # Applies request-level authentication and security checks.
│   │   │   │   └── SecurityFilter.java         # Enforces sessions, roles, CSRF protection, and response security headers.
│   │   │   └── util/                          # Provides shared configuration, initialization, and validation utilities.
│   │   │       ├── Db.java                     # Loads database settings and supplies JDBC connections and transactions.
│   │   │       ├── DatabaseInitializer.java    # Creates missing schema safely using the packaged SQL script.
│   │   │       ├── DatabaseStartupListener.java # Runs database initialization when the web application starts.
│   │   │       ├── DatabaseCheck.java          # Initializes schema and prints a database connectivity check.
│   │   │       ├── AdminBootstrap.java         # Initializes the first administrator from an interactive terminal.
│   │   │       └── Validation.java             # Validates identifiers, text, selections, capacity, and dates.
│   │   ├── resources/                         # Contains configuration copied to the application's classpath.
│   │   │   └── db.properties                  # Configures the MySQL URL, credentials, and automatic initialization.
│   │   └── webapp/                            # Contains web assets, JSP views, and deployment metadata.
│   │       ├── META-INF/MANIFEST.MF            # Supplies the web archive's manifest metadata.
│   │       ├── assets/                        # Contains browser-accessible styling and JavaScript.
│   │       │   ├── css/style.css              # Provides shared colors, typography, forms, and responsive layouts.
│   │       │   └── js/app.js                  # Handles archive confirmation and active navigation highlighting.
│   │       └── WEB-INF/                       # Contains protected JSP templates and Servlet deployment configuration.
│   │           ├── web.xml                    # Configures sessions, JSP encoding, welcome routing, and error pages.
│   │           └── views/                     # Contains the application's 17 server-rendered JSP pages.
│   │               ├── layout.jsp             # Provides shared navigation, flash messages, page layout, and footer.
│   │               ├── home.jsp               # Introduces the platform and links to registration or the workspace.
│   │               ├── register.jsp           # Displays the student account registration form.
│   │               ├── login.jsp              # Displays the student and administrator sign-in form.
│   │               ├── dashboard.jsp          # Summarizes personal projects, tasks, announcements, and achievements.
│   │               ├── projects.jsp           # Lists searchable and filterable projects.
│   │               ├── project-form.jsp       # Displays the create-project and edit-project form.
│   │               ├── project-details.jsp    # Shows project information, teammates, tasks, discussions, and files.
│   │               ├── students.jsp           # Lists students and provides project invitation forms.
│   │               ├── notifications.jsp      # Displays private notifications and incoming invitation actions.
│   │               ├── tasks.jsp              # Shows tasks assigned to the logged-in student.
│   │               ├── profile.jsp            # Displays editable profile information and earned badges.
│   │               ├── recommendations.jsp    # Shows projects ranked by explainable skill and interest matching.
│   │               ├── leaderboard.jsp        # Ranks students using persisted achievements and completed work.
│   │               ├── admin.jsp              # Displays protected administration, moderation, and announcement tools.
│   │               ├── error.jsp              # Presents application validation and page-loading errors safely.
│   │               ├── http-error.jsp         # Displays a safe fallback for configured HTTP error responses.
│   │               └── parts/                 # Contains reusable JSP fragments shared by multiple pages.
│   │                   ├── csrf.jspf          # Adds the session CSRF token to state-changing forms.
│   │                   ├── project-cards.jspf # Renders project summaries and progress indicators.
│   │                   ├── task-list.jspf     # Renders task information, editing forms, and status controls.
│   │                   └── badges.jspf        # Renders earned badges and achievement details.
│   └── test/java/com/collabhub/                # Contains test suites and the optional local development runner.
│       ├── DevServer.java                     # Starts embedded Tomcat with the application resources and classes.
│       ├── InitializeTestDb.java              # Initializes the isolated schema used by explicit integration tests.
│       ├── WorkflowTest.java                  # Tests database workflows, permissions, concurrency, and rollback.
│       ├── RegressionTest.java                # Tests announcement limits, password boundaries, idempotency, and team capacity.
│       ├── WebTest.java                       # Tests real HTTP sessions, rendered pages, forms, and file access.
│       ├── JspCompilationTest.java            # Compiles all JSP pages and fragments without a database.
│       ├── servlet/FileServletTest.java       # Tests default upload storage without a Tomcat base directory.
│       └── util/                              # Contains configuration, validation, and isolated-database test helpers.
│           ├── DatabaseInitializerTest.java   # Tests setup safety and optional real first-run data preservation.
│           ├── ValidationTest.java            # Tests Unicode previews, password byte limits, and invalid dates.
│           ├── TestDatabase.java              # Restricts integration execution to explicitly isolated database names.
│           └── ReviewRunner.java              # Runs MySQL/HTTP regressions in a disposable schema and removes it afterward.
├── target/                                    # Generated Maven output containing compiled classes, WAR, and test reports.
├── runtime/                                   # Generated local Tomcat files and uploads excluded from Git.
├── .maven-repo/                               # Local dependency cache created by the helper scripts and excluded from Git.
└── deliverables/CollabHub-source.zip           # Generated source archive when available; ZIP files are excluded from Git.
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
| Unknown database/table | Verify automatic initialization is enabled and CREATE permissions are granted; restart and check startup logs. |
| `javax.servlet` missing | Maven Update Project and select Tomcat 9 runtime. Tomcat 10+ is incompatible. |
| JSTL class/tag not found | Confirm Maven Dependencies deploy to WEB-INF/lib; avoid duplicate manual JARs. |
| 404 for application | Verify the deployed module and context path `/Student_Collaboration_Hub`. JSPs under WEB-INF are intentionally not directly accessible. |
| Form expired/403 | Reload the page after login/session expiration, then resubmit. Cross-session CSRF tokens are invalid. |
| Port 8080 in use | Stop the other server or select another connector port. |
| Upload fails | Use a permitted type ≤10 MB and ensure Tomcat can write UPLOAD_DIR. |
| Error page without details | Check Eclipse Console / Tomcat logs for the server-side exception; users never receive stack traces. |

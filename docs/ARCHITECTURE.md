# Architecture and feature mapping

Every mutation uses POST and a session CSRF token. Successful forms use Post/Redirect/Get with escaped flash messages. Routes below are relative to `/Student_Collaboration_Hub`. GET is used for pages and authorized downloads only.

| Feature / JSP | Servlet routes | DAO | Tables |
|---|---|---|---|
| home, register, login | AppServlet `/home`, `/register`, `/login`, `/logout` | UserDao | students |
| dashboard | `/dashboard` | CommunityDao, TaskDao | projects, project_members, tasks, announcements, student_achievements |
| project discovery/form/details | `/projects`, `/project-details`, `/project-create`, `/project-update`, `/project-delete` | ProjectDao | projects, project_members, tasks, activity_logs |
| student directory/invitations | `/students`, `/team-request`, `/team-request-accept`, `/team-request-reject` | CommunityDao, TeamRequestDao | students, projects, team_requests, project_members, notifications, student_achievements |
| notifications | `/notifications`, `/notification-read` | CommunityDao, TeamRequestDao | notifications, team_requests |
| project tasks / my tasks | `/tasks`, `/task-create`, `/task-update`, `/task-status` | TaskDao | tasks, project_members, notifications, student_achievements |
| discussion inside project details | `/discussions` | CommunityDao | discussions, notifications, project_members, student_achievements |
| file section in project details | FileServlet `/files` POST upload / GET download | FileDao | project_files, project_members |
| own profile / badges | `/profile` | UserDao, CommunityDao | students, badges, student_achievements |
| recommendations | `/recommendations` | ProjectDao | students, projects, project_members |
| leaderboard | `/leaderboard` | CommunityDao | students, tasks, projects, badges, student_achievements |
| administration | `/admin/dashboard`, `/admin/student`, `/admin/announcement` | AdminDao, ProjectDao | students, projects, tasks, project_members, announcements, notifications, activity_logs |

## Invitations and consistency

Sending an invitation locks the project row. It checks the sender's owner/admin permission, project status/capacity, recipient existence/activity, self-invitation, membership, and pending/accepted requests. Request creation, recipient notification and activity logging commit together. A generated nullable `pending_guard` column provides a unique constraint for pending sender/recipient/project requests, while allowing rejected history.

Responding first verifies the request belongs to the session user, then locks the project and request in that order. It checks PENDING status. Acceptance validates status/capacity, inserts membership if absent, updates status, awards the Team Player badge, sends the owner's notification and records activity in one transaction. Any failure rolls back everything. The membership primary key independently prevents duplicates. Repeated/concurrent responses process exactly once. Reject uses the same locking/authorization and never adds a member. Accepted members are queried from a `project_members JOIN students` on every details request; refresh/login do not affect the stored relationship.

Project creation inserts the owner membership and builder badge atomically. Task operations lock the project and require an actual project-member assignee (also enforced by a composite foreign key). Team members/admins may edit project tasks; only owner/admin may change project metadata or archive. Notification read updates include the logged-in recipient in the WHERE clause. Private discussions/files require team membership or admin read access; posting/uploading additionally requires actual membership. Archived projects reject task/discussion/upload/invitation writes. Administration is checked by both filter and DAO, and active role status is reloaded for every authenticated request.

## Security

BCrypt cost 12; password length constrained to BCrypt's 72 UTF-8 byte limit. Prepared SQL statements and try-with-resources throughout. Session ID rotates after login; CSRF token rotates too. Logout invalidates session. Cookies are HttpOnly and URL session rewriting is disabled. CSP disallows external scripts/styles/frames and inline script execution. No SQL, password hashes or database credentials reach JSPs. Generic database failures are logged server-side. UTF-8 request, response, JSP and MySQL configuration supports international text.

File uploads use UUID storage names outside the executable web root; metadata is committed after writing, and the file is removed if metadata transaction fails. Extension, MIME, size and basic magic/text validation are enforced. Downloads load metadata, authorize project access, verify storage path confinement and deny symlinks, then force attachment delivery. As with any local filesystem + database pairing, a process crash between file write and DB commit could leave an orphan file; no distributed filesystem transaction is claimed.

## Scores and recommendations

Badge awards use unique `(student_id,badge_id)` and `INSERT IGNORE`: Builder 20 (first project); Team Player 15 (first accepted invite); Finisher 25 (first completed assigned task); Contributor 10 (first discussion). Badges remain earned even if a task is reopened. Leaderboard adds 10 for each currently completed assigned task and 5 for each currently completed owned project. Scores come directly from persisted records, with equal scores ordered by name and ID.

Recommendations tokenize comma-separated skills/technologies case-insensitively. Each exact matching technology adds 3 points; an exact matching interest/category adds 2. Candidates must be OPEN, have team capacity and exclude the student's memberships. Scores sort descending; ties retain newest-first database order. Missing skills/interests produce zero-point available-project suggestions with an explicit fallback reason. No trained model or external AI service is used.

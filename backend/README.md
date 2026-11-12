# CollegeConnect Backend

This folder contains the Spring Boot foundation, Phase 4 persistence model, and the Phase 5 authentication foundation. Phase 6 and Phase 7 workflows are implemented against the SRS. Phase 8 is in progress and is not signed off: marks, partial reporting, notification preferences, and an initial outbox/WhatsApp integration exist, but required workflows, reports, integration tests, and full Maven verification remain outstanding.

## Approved implementation sequence

The repository does not define Phases 7 and 8. For this implementation, the user-approved working scope is:

- **Phase 6:** Authenticated workflow foundations and attendance capture/correction.
- **Phase 7:** Absence explanations, advance leave requests, and supporting documents.
- **Phase 8:** CIE/SEE marks, reports, and notification preferences, outbox delivery, and recovery.

These are delivery groupings of the approved SRS requirements, not additional college policy decisions. Open Decisions in the SRS remain configurable or deferred; they must not be replaced with invented production rules.

## Requirements

- JDK 21
- Maven 3.6.3 or later
- MySQL 8.0.16 or later for local application runs (enforced `CHECK` constraints are used)

## Database setup

The application expects a MySQL database/schema to exist. Flyway applies versioned table migrations when the application starts; it does not create the MySQL database/schema itself. Hibernate is configured to validate the migrated schema and will not create or alter tables.

**Important:** Do not point the application at a non-empty or shared database until you have inspected it and made a backup. Flyway will attempt to create the CollegeConnect tables in the configured schema. It does not drop existing data, but existing tables with the same names can cause migration failure.

The first migration is `src/main/resources/db/migration/V1__initial_schema.sql`. Do not edit a migration after it has been applied to a shared database; add a new versioned migration for subsequent schema changes.

Create the empty database using your MySQL administration tool or SQL client. For example, only if `collegeconnect` does not already exist:

```sql
CREATE DATABASE collegeconnect
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

Do not run this command if you already created the database. If that database already contains tables or data, inspect it and make a backup before using it. The migration creates tables and constraints inside the database configured by `DB_URL`; it does not alter credentials or select another database.

## Run the test suite

From this directory, run:

```powershell
mvn test
```

Tests activate the `test` profile, apply the Flyway migration to an in-memory H2 database in MySQL compatibility mode, and ask Hibernate to validate that the mapped entities match the resulting schema. H2 is test-only; the application runtime uses MySQL.

## Run the application locally

Set the connection settings in your PowerShell session. Substitute values from your local MySQL setup; do not put real passwords in this file or commit them.

```powershell
$jwtSecretBytes = New-Object byte[] 32
$jwtRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtSecretBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtSecretBytes)
$jwtRandom.Dispose()
$env:DB_URL = "jdbc:mysql://localhost:3306/collegeconnect"
$env:DB_USERNAME = "<your-local-mysql-username>"
$env:DB_PASSWORD = "<your-local-mysql-password>"
mvn spring-boot:run
```

The JWT secret is generated locally and held only in the environment variable; keep it private and use a managed secret store outside local development. It must be Base64 that decodes to at least 32 random bytes for HS256. The configured database must exist and be reachable. Flyway applies pending migrations at startup.

The login endpoint is `POST /api/auth/login` with JSON fields `loginIdentifier` and `password`. Successful Admin, Professor, and Student login returns a 15-minute Bearer access token. Parent dashboard login is not enabled yet. Send an access token in the `Authorization: Bearer <token>` header. Admin, Professor, Student, and Parent routes are guarded under `/api/admin/**`, `/api/professor/**`, `/api/student/**`, and `/api/parent/**` respectively. Refresh tokens, logout/revocation, password reset, login throttling, parent dashboard login, and record-ownership checks are deferred to later steps.

Passwords must be stored using the configured BCrypt `PasswordEncoder`; account onboarding must call that encoder rather than persisting a plain-text password. Login is denied unless the account status is `ACTIVE`.

Authenticated Professors can read the roster for their assigned session at `GET /api/professor/attendance/sessions/{sessionId}/roster`. Admins can read a session roster at `GET /api/admin/attendance/sessions/{sessionId}/roster`. A Professor receives `403 Forbidden` when requesting a session assigned to another Professor; a missing session returns `404 Not Found`.

Assigned Professors and session substitutes submit attendance with `POST /api/professor/attendance/sessions/{sessionId}/submit`, providing exactly one `PRESENT`, `ABSENT`, or `AUTHORIZED_LEAVE` status for every roster student. `AUTHORIZED_LEAVE` is accepted only when approved leave covers the session date; a student with approved leave cannot be submitted as an ordinary absence. The submitter must explicitly choose the authorized-leave status rather than the system silently inferring it. This conservative behavior avoids creating an incorrect absence case while the college's OD-LVE-EFFECT decision remains open. Submission is transactional and locks the session against duplicate submits. It creates absence cases and idempotent outbox events for absences; absent-notification eligibility respects active verified parent links, recorded WhatsApp opt-in/mobile verification, and the `ATTENDANCE_ABSENT` preference. The system does not send WhatsApp messages in this submission request.

Admins can submit initial attendance as an administrative override using `POST /api/admin/attendance/sessions/{sessionId}/submit`. Already-submitted records must use the correction endpoint. The operation is recorded as an `ADMIN_OVERRIDE` attendance source action and an audit-log entry.

Admins can correct submitted records using `POST /api/admin/attendance/records/{attendanceId}/corrections`; each request supplies the expected current version and a mandatory reason. Corrections can target `PRESENT` or `ABSENT`; an `ABSENT` record may also be corrected to `AUTHORIZED_LEAVE` only when approved leave covers that session date. That transition closes the absence case and writes the correction history and notification intent atomically. Correction responses and history expose previous/new status, actor account/login/role, reason, optional comment, sequence, and correction timestamp; the original submitter and submission time remain unchanged. An absence correction uses the existing `ATTENDANCE_ABSENT` preference for its corrective notification; present notifications remain off by default until the college resolves OD-NOTIFY-PRESENT. An `ABSENT` to non-absent correction cancels pending or retryable original absence intents without deleting their history; already processing or accepted deliveries cannot be recalled.

The correction path pessimistically locks pending/retryable outbox rows while cancelling them. The outbox worker claims events under a database write lock, so it cannot claim a row already cancelled by a correction. If the worker claims an event before a correction, delivery may already be in flight and an uncertain acceptance requires reconciliation. This claim/cancel race still requires database-backed integration-test verification. Correction history is available to Admins and assigned Professors. Professor corrections are disabled unless both `collegeconnect.attendance.correction.professor-enabled=true` and a policy-approved positive `collegeconnect.attendance.correction.professor-window` duration are configured.

Parents with an active verified link can view absence cases at `GET /api/parent/students/{studentId}/absence-cases`, submit explanations at `POST /api/parent/absence-cases/{caseId}/explanation`, and create/list/cancel advance leave requests under `/api/parent/leave-requests`. Students can view their own absence cases. Professors can view absence and leave cases only for assigned offerings/sessions; Admins can list and review institution-wide through `/api/admin/absence-cases` and `/api/admin/leave-requests`. Until the college configures a more specific OD-LVE-CUTOFF, leave requests are rejected for past dates or when an affected scheduled session has started; same-day requests before session start are allowed. The built-in reviewer route is Admin-only, consistent with the SRS fallback.

Supporting documents can be uploaded by linked parents to an absence case or their leave request. Parents can list, replace, and delete their own uploads; Professor downloads are limited to assigned sessions/offerings, and Admins can list and download institution-wide. Downloads require an active verified parent-child link or professor assignment as applicable and a `CLEAN` scan state. Upload, scan-result, authorized download, replacement, and deletion operations are recorded in the existing audit log; Admin audit history is available at `/api/admin/supporting-documents/{documentId}/audit`. Configure `COLLEGECONNECT_DOCUMENTS_DIRECTORY` to a private, non-web-served directory before uploads are enabled. Current validation accepts PDF, JPEG, and PNG up to the configured 5 MiB limit and checks the content signature against the declared type and extension. Uploads remain `PENDING_SCAN`; there is no automated malware scanner in this backend. An Admin records an external scan result before download is allowed. Files are never served from a static web directory.

Professors/Admins can create CIE and SEE assessments and enter marks for actively enrolled students. CIE marks are bounded by the assessment's configured maximum; SEE result values are intentionally not tied to an invented grading scheme. Admin publishes assessments, with each mark's published revision preserved in history. Marks publication creates an outbox event for each active verified parent link, or an Admin-visible `SKIPPED_NO_OPTIN` event when no such link exists. Parents with active verified links and students can read only published marks. `GET /api/student/attendance/summary` and parent/Admin student summary routes compute Present / (Present + Absent) × 100; no eligibility threshold is inferred.

Parents can read/update supported notification preferences at `/api/parent/notification-preferences`. Admin outbox monitoring is available under `/api/admin/notifications/outbox`. Manual retry requires a confirmed never-accepted event; explicit resend creates a linked event with a new key and requires a reason.

Admins can read and update global notification defaults at `/api/admin/notification-preferences/defaults`; each change requires a reason and is audited. Parent preferences include attendance absence/present/summary, leave, absence decisions/reminders, CIE, SEE, and an all-messages opt-out. The worker checks active verified links, mobile verification, WhatsApp opt-in, parent preferences, and the global defaults before sending.

Date-range attendance rows are available at `/api/admin/reports/attendance` and `/api/professor/reports/attendance`; the latter is restricted in the database query to assigned offerings/sessions. Both support CSV at `/export.csv` and section/subject summaries at `/summary?groupType=SECTION|SUBJECT`. Student and linked-parent date-range reports are available under their role-scoped routes. Reports validate the date range and reject results over 5,000 rows rather than silently truncating them.

The asynchronous outbox worker is enabled only when `WHATSAPP_ENABLED=true`. It claims rows under a database write lock, writes attempt/lease state before calling the provider, applies bounded exponential backoff, marks expired leases ambiguous rather than blindly resending, and keeps provider failures separate from the business transaction. Configure `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_API_VERSION`, and the approved-template mappings `WHATSAPP_TEMPLATE_*`. The Cloud API adapter does not report success unless Meta returns a provider message id.

Meta can verify and deliver webhook callbacks at `GET/POST /api/webhooks/whatsapp`. Configure `WHATSAPP_WEBHOOK_VERIFY_TOKEN` and `WHATSAPP_APP_SECRET`. POST requests are HMAC-SHA256 verified and stored before asynchronous processing; duplicate inbound message ids and status callbacks are deduplicated. Delivery states are reconciled monotonically, and unmatched callback events remain flagged for reconciliation. Admins can inspect unresolved webhook events at `GET /api/admin/notifications/webhooks`. Inbound STOP/START commands are accepted only from active parent accounts with verified numbers; START also requires an active verified child link.

### Remaining integration and policy blockers

- The worker, Cloud API adapter, signature-verified webhook persistence, delivery callback reconciliation, and inbound STOP/START consent handling are implemented but not Maven-verified or externally tested. No delivery is attempted unless WhatsApp is enabled and credentials/templates are configured.
- The adapter supports approved template calls and explicit `templateParameters`, but existing business-event producers do not yet provide all required child/class/subject/date context parameters. Configure only templates whose parameter shape matches the outbox payload, or complete the message-context builders before production sending.
- Inbound attendance/marks/status/leave/explanation/preference commands, multi-child selection, inbound media/document attachment, and provider replies are not implemented. Only STOP/START consent changes are processed.
- Date-range attendance reports and CSV are implemented; absence/leave reports, published CIE/SEE performance reports, and date-filtered notification delivery reports remain outstanding.
- Concurrent database-backed worker claims, webhook deduplication under concurrent delivery, callback ordering, retry/resend transaction behavior, and transactional rollback behavior still require integration-test verification.
- End-of-day and daily-summary jobs are not enabled because their local times, absence window, and other relevant OD values are unresolved.
- Professor correction access stays off until both the correction-role decision and a positive correction window are configured. Other leave/absence policy defaults remain conservative and may need college approval.
- External antivirus scanning is not wired. Until an Admin records a scan outcome, supporting documents cannot be downloaded.
- Leave and absence decisions currently create outbox intent, but do not notify staff in-app or deliver parent messages.
- Parent dashboard password login remains disabled; parent APIs require a valid Parent JWT issued by an approved identity path.
- OD-ABS-WINDOW and any cutoff more specific than the SRS default are not configured, so explanation-window enforcement and configurable per-session leave cutoffs remain unresolved.
- Leave requests do not yet transition automatically to `EXPIRED`; the college must confirm when that state should apply. Document count limits are also unresolved; the current configured file-size limit is 5 MiB.
- This project has no web/mobile user interface; these are backend APIs only.

## Security defaults
Only login and the documented actuator endpoints are public.
Admin, Professor, Student, and Parent API routes require a matching role in a valid JWT.
Unknown routes are denied by default, even when the caller has a valid token.
Tokens use HS256 and expire after 15 minutes; keep the signing secret outside source control.

To package the application:

```powershell
mvn clean package
```

## File guide

| File | Purpose |
| --- | --- |
| `pom.xml` | Maven project definition: Spring Boot parent, Java version, runtime dependencies, and test dependencies. Spring Boot's dependency management selects compatible library versions. |
| `src/main/java/com/collegeconnect/CollegeConnectApplication.java` | Application entry point. `@SpringBootApplication` enables component scanning and Spring Boot auto-configuration; `SpringApplication.run` creates and starts the application context. |
| `src/main/java/com/collegeconnect/entity/BaseEntity.java` | Shared generated primary key mapping for persistent domain entities. |
| `src/main/java/com/collegeconnect/entity/` | JPA mappings for academic structure, accounts/profiles, sessions/attendance, leave, marks/history, documents, notifications, and audit. Each entity maps one logical record type; associations use lazy foreign-key relationships. |
| `src/main/resources/db/migration/V1__initial_schema.sql` | Initial, versioned MySQL schema: tables, foreign keys, uniqueness, checks, and query indexes. Flyway applies it once per database. |
| `src/main/resources/application.yml` | Runtime configuration. MySQL connection values come only from environment variables. Flyway runs migrations; Hibernate validates but does not generate schema. |
| `src/test/resources/application-test.yml` | Test-only H2 datasource configuration, isolating tests from a real MySQL database. |
| `src/test/java/com/collegeconnect/CollegeConnectApplicationTests.java` | Smoke test that checks Spring context startup, migration execution, and JPA schema validation using the test profile. |
| `.gitignore` | Excludes Maven output, IDE metadata, and local secret files from version control. |

## Dependency overview

- `spring-boot-starter-web`: Spring MVC and JSON-based REST foundations.
- `spring-boot-starter-data-jpa`: Spring Data repository and Hibernate ORM foundation for the Phase 4 entity mappings.
- `spring-boot-starter-security`: Spring Security foundation. Custom login, JWT handling, and role permissions are Phase 5 work.
- `spring-boot-starter-validation`: request validation foundation for later API DTOs.
- `spring-boot-starter-actuator`: operational health and information endpoints.
- `mysql-connector-j`: MySQL JDBC driver used at runtime.
- `flyway-core` and `flyway-mysql`: versioned SQL migrations for the MySQL schema.
- `spring-boot-starter-test`: JUnit, Spring test support, and Mockito for tests.
- `h2`: in-memory database for tests only.

## Configuration notes

- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are required for a normal application run. There are deliberately no credential defaults.
- `spring.jpa.hibernate.ddl-auto: validate` prevents the application from silently creating or changing database structure; Flyway migrations define the schema.
- Flyway migrations are the schema source of truth. Do not combine them with Hibernate `create`, `create-drop`, or `update` modes.
- `spring.jpa.open-in-view: false` avoids keeping a database session open through web response rendering.
- Hibernate JDBC timestamps use UTC. College-local date/time display and scheduling will be handled in the relevant domain/integration phases.
- Spring Security's starter applies its default security behavior until Phase 5 replaces it with the approved authentication and authorization configuration.

## Persistence model overview

The model is based on the approved [Phase 2 architecture and ER model](../docs/CollegeConnect_Architecture_ERD_v1.1.md). `USER_ACCOUNT` is separate from role profiles. Enrollment connects students to subject offerings. A session snapshots its roster, and attendance is unique for each session/student pair. Attendance corrections and mark revisions are append-only histories. Supporting documents must belong to exactly one leave request or absence case. The notification outbox records WhatsApp send intent separately from send attempts and provider delivery status.

Database foreign keys enforce record existence and several important uniqueness rules. Rules that depend on multiple records or college policy (for example parent links must be verified/active, a leave request must target the student's own sessions, or an outbox source must match its student) must also be checked by the service layer in their respective later phases. Attendance status, role, workflow status, notification categories, and CIE/SEE policy values remain strings rather than hard-coded closed enums while the SRS Open Decisions are unresolved.

## Planned package boundaries

As implementation progresses, source packages will follow the approved Phase 2 architecture: `controller`, `dto`, `service`, `repository`, `entity`, `config`, `security`, `exception`, `integration`, `validation`, and `notification`. Packages are introduced as needed rather than created empty.

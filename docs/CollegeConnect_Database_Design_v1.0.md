# CollegeConnect — Phase 4 Database Design

**Version:** 1.0  
**Status:** Implemented persistence foundation; validation pending  
**Requirements baseline:** [SRS v1.3](./CollegeConnect_SRS_v1.3.md)  
**Logical model:** [Approved Phase 2 architecture and ERD](./CollegeConnect_Architecture_ERD_v1.1.md)  
**Implementation:** [Backend project](../backend/)

## 1. Scope

Phase 4 turns the approved logical ER model into MySQL tables, constraints, a versioned schema migration, and Spring Data JPA entity mappings. It does not implement business services, REST endpoints, authentication, attendance submission, WhatsApp sending, or file storage behavior. Those remain later phases.

The schema deliberately does not create the MySQL database itself. The database must exist before the application starts; Flyway creates and versions tables within it. Do not point the application at a non-empty or shared schema until it has been inspected and backed up. The migration does not drop data, but naming conflicts with existing tables will cause startup to fail.

The migration targets MySQL 8.0.16 or later so the database enforces `CHECK` constraints. Physical table primary keys use a consistent `id` column inherited through `BaseEntity`; foreign-key columns keep descriptive names such as `student_id` and `session_id`. Join tables use a generated `id` plus a unique constraint on the logical pair, preserving duplicate protection while keeping one consistent JPA base mapping.

## 2. Database relationship summary

### Identity and access relationships

- `user_account` stores one login identity, password hash, primary role, and account status.
- `admin_profile`, `professor_profile`, `student_profile`, and `parent_profile` hold role-specific attributes. Each profile has a unique foreign key to one account.
- `parent_student_link` is the many-to-many bridge between parents and students. Verification, activation, and revocation are represented separately so access is based on the active verified relationship, not on matching names or phone numbers.
- `whatsapp_consent_event` keeps opt-in/opt-out history, including method, actor when applicable, timestamp, and optional child link. `parent_profile` also carries the current verified phone and current opt-in state for quick checks.

### Academic structure and enrollment

- A department has programmes; programmes have sections; an academic year has semesters; each section belongs to a programme and semester.
- A subject is a catalog item. A `subject_offering` is that subject for a particular section and semester.
- `professor_assignment` connects professors to offerings. A timetable slot uses an assignment; actual `class_session` rows preserve the responsible assignment and any explicit substitute.
- `enrollment` connects students to offerings across a validity period.

### Sessions and attendance

- `session_roster` snapshots the students eligible for a particular session.
- `attendance_record` stores one row per student/session, with a unique constraint preventing duplicates. A composite foreign key requires an attendance row to exist in that session's roster.
- `attendance_record.version` is the JPA optimistic-lock version. `attendance_correction` stores append-only before/after changes, actor, sequence, reason, and time. The later correction service must update the current row and append its history in one transaction.
- `absence_case` is associated with its originating attendance row. The schema allows one case per attendance record so workflows can update/reuse that case while retaining its closed status/history.

### Leave, marks, and documents

- `leave_request` references exactly one student and requester; `leave_request_session` optionally specifies the class sessions included in the date range.
- `cie_assessment` and `see_assessment` define marks contexts for an offering. Mark rows reference an assessment and student; history rows preserve mark revisions.
- Current mark rows hold current and published revision numbers separately. Student/parent-facing reads must use the published revision. The application must keep pointers consistent with history records in the same transaction.
- `supporting_document` stores private storage metadata, not the document bytes. A database check requires exactly one owner: an absence case or a leave request. The service must additionally ensure that the document, owner, and authenticated requester all refer to the same student.

### Notifications and audit

- `notification_outbox` records WhatsApp send intent and recipient scope; `notification_attempt` records individual provider attempts and delivery status. An explicit resend is a new outbox row linked to the original with a new idempotency key.
- Each outbox event stores its approved template name and a snapshot of template parameters as JSON text. This prevents a later attendance correction or marks amendment from changing the content of an already-queued notification. The payload must contain only the fields needed to render that message and must follow the approved privacy and retention policy.
- `whatsapp_webhook_event` records deduplication/correlation details for inbound events and provider callbacks.
- `in_app_notification` is for staff-facing work queues; it is not the WhatsApp outbox.
- `audit_log` attributes sensitive actions. Detailed attendance corrections and mark revisions also have dedicated append-only history tables.

## 3. Important constraints

The migration enforces:

- Unique login identifiers, official student IDs, faculty employee IDs, subject codes, and verified parent phone numbers.
- Foreign keys between academic structure, profiles, assignments, sessions, enrollment, attendance, marks, cases, documents, outbox records, and audit records.
- Unique `(session_id, student_id)` attendance and roster pairs.
- A foreign key from attendance `(session_id, student_id)` to the session roster pair.
- Unique mark per assessment/student and unique revision number per mark history.
- Unique notification idempotency keys, attempt number per outbox event, and provider message IDs when present.
- Valid date/time ranges, positive file size, and exactly-one document owner.
- Restricted deletion by default: no history-bearing relationship uses cascading delete.

Application services must still enforce multi-row business rules that cannot be represented with these simple constraints, such as active parent-child authorization, role-specific scope, matching the student's offering to a selected session, approval policy, and notification source/student consistency.

## 4. How the JPA mappings work

### `BaseEntity`

`BaseEntity` is annotated with `@MappedSuperclass`. This tells JPA to map its `id` field into each concrete entity table without creating a separate database table for the superclass. `@Id` marks the primary key and `@GeneratedValue(strategy = IDENTITY)` uses the database's auto-increment identity.

### Entity classes

Each class annotated with `@Entity` maps a Java type to a table. `@Table` selects the physical table name and declares uniqueness constraints where needed. Fields are mapped directly because JPA uses field access (the identifier annotation is on a field).

### Relationships

- `@ManyToOne` represents a foreign-key reference from the current row to another entity.
- `@OneToOne` plus a unique foreign key maps one account to at most one role profile.
- `FetchType.LAZY` avoids loading referenced entities unless application code actually uses them.
- `@JoinColumn` fixes the SQL foreign-key column name so the mapping is clear and stable.

All status, role, attendance, leave, and consent category values are stored as strings instead of closed Java enums for now. This avoids encoding unresolved college policy in code; validation of allowed transitions belongs in the relevant future service.

`@Version` on attendance and mark rows enables optimistic locking. When two requests try to update the same record, JPA detects that the stored version changed and rejects the stale write rather than silently overwriting the newer value.

The entity files are grouped as follows:

| Entity classes | What they represent |
| --- | --- |
| `BaseEntity` | Shared generated primary key |
| `Department`, `Programme`, `AcademicYear`, `Semester`, `Section`, `Subject`, `SubjectOffering` | College academic hierarchy and a subject taught for a particular section/term |
| `UserAccount`, `AdminProfile`, `ProfessorProfile`, `StudentProfile`, `ParentProfile`, `ParentStudentLink`, `WhatsAppConsentEvent` | Login identity, role profiles, guardian link, and consent history |
| `ProfessorAssignment`, `Enrollment`, `TimetableSlot`, `ClassSession`, `SessionRoster` | Teaching assignments, enrollment, schedule, class occurrence, and its roster snapshot |
| `AttendanceRecord`, `AttendanceCorrection`, `AbsenceCase` | Current class-wise attendance, concurrency version, append-only corrections, and absence explanation lifecycle |
| `LeaveRequest`, `LeaveRequestSession` | Request ownership, date range, and optional explicitly selected class sessions |
| `CieAssessment`, `CieMark`, `CieMarkHistory`, `SeeAssessment`, `SeeMark`, `SeeMarkHistory` | Assessment definitions, current/published marks, and append-only mark revisions |
| `SupportingDocument` | Private file metadata attached to exactly one leave request or absence case |
| `NotificationPreference`, `NotificationOutbox`, `NotificationAttempt`, `WhatsAppWebhookEvent`, `InAppNotification` | Parent choices, durable WhatsApp send intent, provider attempts/statuses, inbound webhook deduplication, and staff alerts |
| `AuditLog` | Actor-attributed history for sensitive operations |

### Flyway and Hibernate responsibilities

- Flyway applies `V1__initial_schema.sql` once and records its application in `flyway_schema_history`.
- Hibernate's `ddl-auto: validate` compares the entity mappings to the migrated schema at startup. It does not create or alter tables.
- This separation makes the SQL migration the reviewed schema source of truth and reduces the chance of accidental production schema changes.

## 5. File map

| File or directory | Purpose |
| --- | --- |
| `backend/pom.xml` | Adds Flyway core and the Flyway MySQL database module to the existing Spring Boot dependencies. |
| `backend/src/main/resources/application.yml` | Enables Flyway and retains MySQL environment-variable configuration plus Hibernate schema validation. |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` | Creates the initial normalized schema, keys, constraints, and indexes. |
| `backend/src/main/java/com/collegeconnect/entity/BaseEntity.java` | Shared generated primary-key mapping. |
| `backend/src/main/java/com/collegeconnect/entity/` | JPA mappings. Classes are grouped by their responsibility: academic structure, accounts, enrollment, attendance, leave, marks, documents, notifications, and audit. |
| `backend/src/test/resources/application-test.yml` | Selects an isolated in-memory H2 database and keeps Hibernate schema validation enabled. |
| `backend/src/test/java/com/collegeconnect/CollegeConnectApplicationTests.java` | Starts the Spring context; the test profile applies the migration and validates JPA table mappings. |
| `backend/README.md` | Local database setup, run/test commands, configuration notes, and dependency overview. |

## 6. Run and validate locally

Prerequisites: JDK 21, Maven 3.6.3 or later, and MySQL 8.0.16 or later. First create the empty database if it does not already exist. Then, from `CollegeConnect\backend` in PowerShell, configure your own local credentials and run:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/collegeconnect"
$env:DB_USERNAME = "<your-local-mysql-username>"
$env:DB_PASSWORD = "<your-local-mysql-password>"
mvn test
```

The tests use H2, not your configured MySQL server. To validate the MySQL connection and apply the migration to your own database, run:

```powershell
mvn spring-boot:run
```

Never commit real credentials. Do not edit `V1__initial_schema.sql` after it has been applied to a shared database; create a new numbered migration for future changes.

## 7. Known validation status and next work

The initial migration is written but has not been applied to the user's MySQL database. The current environment does not provide a working PowerShell command runner, so Maven execution has not been confirmed here. The next local validation is `mvn test`; if Hibernate reports a mapping/schema mismatch, fix the migration/entity pair before using the migration on a real MySQL database.

After Phase 4 approval, Phase 5 can implement authentication and authorization on top of `user_account` and the role profiles. Do not treat the current `role` or status strings as access control until Phase 5 services and security rules enforce them.

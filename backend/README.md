# CollegeConnect Backend

This folder contains the Spring Boot foundation and the Phase 4 persistence model. Authentication rules, API endpoints, business workflows, and user-facing features are still deferred to their approved phases.

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
$env:DB_URL = "jdbc:mysql://localhost:3306/collegeconnect"
$env:DB_USERNAME = "<your-local-mysql-username>"
$env:DB_PASSWORD = "<your-local-mysql-password>"
mvn spring-boot:run
```

The configured database must exist and be reachable. Flyway applies pending migrations at startup. The actuator health endpoint is `/actuator/health`; Spring Security's starter defaults may require authentication until Phase 5 configures endpoint access.

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

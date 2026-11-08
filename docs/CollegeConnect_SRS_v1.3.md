# CollegeConnect

## Software Requirements Specification (SRS)

**Product name:** CollegeConnect — Smart College Attendance and Parent Communication System  
**Document version:** 1.3  
**Status:** Final for stakeholder review — pending explicit approval  
**Date:** 4 November 2026  
**Prepared for:** College academic administration, faculty, students and parents  
**Prepared by:** Chandreshekar MG  
**Intended next step after approval:** Architecture and design (Phase 2) — not started

---

## Document Control

| Field | Value |
| --- | --- |
| Document title | CollegeConnect Software Requirements Specification |
| Version | 1.3 |
| Classification | Internal academic / project documentation |
| Source baseline | SRS Version 1.2 (complete functional and non-functional baseline) |
| Change nature | Three technical corrections only: (1) WhatsApp delivery guarantees vs internal idempotency; (2) SHALL / SHOULD / MAY terminology; (3) consistent manual retry, resend and ambiguous-outcome rules. All other Version 1.2 requirements preserved |
| Implementation status | Requirements only. No architecture document, no database schema implementation, and no application code are authorized by this version until explicit approval of SRS 1.3 |

### Version history

| Version | Date | Description |
| --- | --- | --- |
| 1.0 | Prior | Initial SRS covering roles, attendance, parent communication and core academic functions |
| 1.1 | Prior | Complete refined SRS covering roles and permissions, attendance, absence cases, advance leave, attendance correction and audit, CIE/SEE, supporting documents, WhatsApp integration, parent–child access restrictions, notification preferences, security and privacy, reports, and future enhancements |
| 1.2 | 4 November 2026 | Final comprehensive review. All Version 1.1 requirements preserved. Adds Transactional Outbox Pattern, notification reliability, logical data-consistency rules, complete parent WhatsApp daily-task coverage, and a WhatsApp failure-and-recovery workflow |
| 1.3 | 4 November 2026 | Technical corrections only. Clarifies internal idempotent processing versus WhatsApp delivery (not absolute exactly-once). Corrects SHALL / SHOULD / MAY. Aligns FR-OUT-7 and WF-13 for retry, resend and ambiguous outcomes. All other Version 1.2 requirements preserved |

### Requirement identification convention

| Prefix | Meaning |
| --- | --- |
| FR-AUTH | Authentication and session |
| FR-ADM | Administrator functions |
| FR-STU | Student functions |
| FR-PRF | Professor functions |
| FR-PAR | Parent functions (web and shared business rules) |
| FR-ACA | Academic structure (departments, classes, subjects, timetable, sessions) |
| FR-ATT | Attendance marking and records |
| FR-ABS | Absence cases and explanations |
| FR-LVE | Advance leave requests |
| FR-COR | Attendance correction and audit history |
| FR-DOC | Supporting document uploads |
| FR-CIE | Continuous Internal Evaluation |
| FR-SEE | Semester End Examination |
| FR-RPT | Reports |
| FR-NPF | Notification preferences |
| FR-LNK | Parent–child linking and access restriction |
| FR-OUT | Transactional outbox and notification processing (Version 1.2 emphasis) |
| FR-DC | Data consistency and referential integrity (logical requirements) |
| WA | WhatsApp integration |
| NFR | Non-functional requirements |
| CON | System constraints |
| OD | Open decisions requiring college input |
| RISK | Risks |
| FE | Future enhancements |
| WF | Application workflows |

Requirement identifiers are stable. Version 1.2 **added** FR-OUT, FR-DC, WA items for reliability/opt-in completeness, WF-13, and related NFR items. Version 1.3 **clarifies** FR-OUT-7, FR-OUT-21 to FR-OUT-23, WA-REL, WF-13 and Section 1.4; it does not remove Version 1.2 capabilities. Existing Version 1.1 requirement areas remain restated in full so this document is self-contained. No Version 1.1 or 1.2 capability listed in the preservation list is removed, narrowed, or silently rewritten to a weaker rule, except where Version 1.3 replaces an over-strong exactly-once wording with the precise delivery guarantee.

Where a college policy value is not known, the SRS records an **Open Decision (OD)** instead of inventing a number, approver, or rule.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Problem Statement](#2-problem-statement)
3. [Project Objectives](#3-project-objectives)
4. [Project Scope](#4-project-scope)
5. [User Roles and Permissions](#5-user-roles-and-permissions)
6. [Functional Requirements](#6-functional-requirements)
7. [WhatsApp Integration Requirements](#7-whatsapp-integration-requirements)
8. [Application Workflows](#8-application-workflows)
9. [Non-Functional Requirements](#9-non-functional-requirements)
10. [System Constraints and Open Decisions](#10-system-constraints-and-open-decisions)
11. [Risks and Mitigation](#11-risks-and-mitigation)
12. [Future Enhancements](#12-future-enhancements)
13. [Requirement Traceability](#13-requirement-traceability)
14. [Approval](#14-approval)

---

## 1. Introduction

### 1.1 Purpose of this document

This Software Requirements Specification defines **what** CollegeConnect must do, **who** may do it, **how parents are informed and can respond**, and **what quality, security and reliability properties** the system must satisfy.

This document is written for:

- College decision makers who must approve scope and policy.
- Developers who will later design and implement the system.
- Testers who will verify behaviour against requirement identifiers.
- Non-technical readers (administrators, faculty coordinators, parent representatives) who need a clear description of daily use.

This document does **not** specify class diagrams, API contracts, table scripts, or source code. Those belong to later phases after this SRS is explicitly approved.

### 1.2 Product overview

CollegeConnect is a Smart College Attendance and Parent Communication System. It connects four roles:

1. **Admin**
2. **Professor**
3. **Student**
4. **Parent**

The primary objective is to track **class-wise (period-wise) student attendance** and to communicate **attendance, absence, leave requests and academic performance** to parents through **WhatsApp**.

**WhatsApp is the primary communication channel for parents** because many parents are not comfortable using web applications. The parent web dashboard is **optional**. A parent who never opens the website must still be able to complete daily parent tasks through WhatsApp.

### 1.3 Intended audience

| Audience | How they use this SRS |
| --- | --- |
| College management | Confirm problem, scope, policy open points, and approval |
| Academic office / Admin users | Confirm operational workflows, reports, corrections, and monitoring |
| Professors | Confirm attendance, leave, CIE/SEE and notification behaviour |
| Students | Confirm what they can view and what they cannot change |
| Parent representatives | Confirm WhatsApp-first usage, privacy, and opt-in/opt-out |
| Development team | Implement only approved requirements, using the named technology stack |
| Testers | Build test cases from FR, WA, NFR and WF identifiers |

### 1.4 Document conventions

Requirement verbs in this SRS have the following meanings. Lower-case **shall**, **should** and **may** have the same meaning as the upper-case forms.

- **SHALL** = mandatory requirement. The system must implement the behaviour. Non-implementation is a defect.
- **SHOULD** = recommended requirement. The behaviour is expected and strongly advised. It is not mandatory in the same sense as SHALL. A justified deviation is allowed (for example an Open Decision, a hosting limit, or an explicit design exception recorded later) but SHALL NOT be ignored silently.
- **MAY** = permitted optional behaviour. The system is allowed to provide the behaviour; the system is also allowed to omit it unless another SHALL requires it.
- **Optional** for the parent web dashboard means the parent is not required to use it; it does not mean the feature is out of scope. The dashboard SHALL exist, but WhatsApp SHALL be sufficient for daily parent tasks.
- User-visible text in this SRS is specified in English. Additional languages are an Open Decision (OD-LANG).
- Logical data names (for example, Attendance Record, Notification Outbox Event) describe required information. They are **not** a physical database schema.

### 1.5 Definitions, acronyms and abbreviations

| Term | Meaning |
| --- | --- |
| CollegeConnect | The system specified by this document |
| Admin | Institutional administrator user who manages academic structure, users, reports, corrections oversight and notification operations |
| Professor | Faculty user assigned to teach one or more subjects/class sessions and mark attendance and internal marks |
| Student | Enrolled learner whose attendance and marks are tracked |
| Parent | Parent or legally recognized guardian linked to one or more students |
| Class session | One conducted teaching period of a subject for a specific class/section on a specific date and time slot |
| Class-wise attendance | Attendance captured per class session, not only a single daily present/absent flag |
| Attendance record | The stored result for one student in one class session (for example Present, Absent, Late, Authorized Leave — exact status set is constrained by OD-ATT-STATUS) |
| Absence case | A follow-up case created when a student is recorded absent in a class session and parent explanation / proof may be required |
| Advance leave request | A request submitted **before** the absence date/session asking permission for planned absence |
| CIE | Continuous Internal Evaluation (internal assessments during the semester) |
| SEE | Semester End Examination (end-semester university/college examination result as recorded by the college process) |
| Supporting document | File uploaded as proof (medical certificate, event letter, travel proof, and similar) |
| WhatsApp Cloud API | WhatsApp Business Platform Cloud API used to send and receive messages |
| Template message | Pre-approved WhatsApp business-initiated message template |
| Session message | Free-form WhatsApp message allowed inside an open customer-care window after the parent messages the business |
| Opt-in | Parent consent to receive WhatsApp notifications on a verified mobile number |
| Opt-out | Parent withdrawal of consent for non-mandatory notification categories, subject to legal and college policy |
| Transactional Outbox | Pattern that stores notification events in the same database transaction as the business data that caused them, then sends messages asynchronously |
| Idempotent processing (internal) | Application-level rule: processing the same outbox event more than once SHALL NOT cause an additional WhatsApp send when the previous send outcome is **known**. This is not the same as provider exactly-once delivery |
| Exactly-once delivery | A guarantee that the parent receives a given business notification once and only once. **CollegeConnect does not promise absolute exactly-once WhatsApp delivery** |
| At-least-once send intent | After a successful business transaction, a corresponding outbox event exists and will be processed until it reaches a terminal outcome (sent, skipped, failed permanently, or ambiguous pending review) |
| Ambiguous delivery outcome | A send attempt whose result is unknown: the WhatsApp provider MAY have accepted the message even though CollegeConnect has not stored a provider message ID (for example a crash or timeout after the provider accepted the request) |
| Provider message ID | Identifier returned by the WhatsApp Cloud API for an accepted message, used to match webhook delivery statuses |
| JWT | JSON Web Token used for API authentication after login |
| RBAC | Role-Based Access Control |

### 1.6 References

| ID | Reference | Use |
| --- | --- | --- |
| REF-IEEE | IEEE 830 / ISO/IEC/IEEE 29148 style for SRS structure | Document organization |
| REF-WA | Meta WhatsApp Business Platform Cloud API policy and technical rules | Templates, opt-in, 24-hour window, media |
| REF-TECH | Project technology choices listed in Section 4.5 | Implementation constraints after approval |
| REF-COLLEGE | College academic regulations, attendance eligibility rules, CIE/SEE scheme | Open Decisions in Section 10 |

### 1.7 Product perspective

CollegeConnect is a new system for a learning/production-capable college deployment. It is not specified as a module inside an existing university ERP. Integration with an existing college ERP, if required later, is a future enhancement unless the college later changes scope.

Logical external actors:

- Human users: Admin, Professor, Student, Parent.
- WhatsApp Business Platform (Cloud API).
- Email is not the primary parent channel. If used at all for staff alerts, that is secondary and does not replace WhatsApp for parents.

---

## 2. Problem Statement

Colleges commonly mark attendance on paper, spreadsheets, or disconnected portals. Parents often learn about absence only after shortage lists are published, or they must log into a website they find difficult to use.

This creates the following problems:

1. **Delayed parent awareness.** Parents do not receive timely, class-wise information when a student misses a period.
2. **Website-only communication fails many families.** Many parents are comfortable with WhatsApp and are not comfortable with web dashboards, passwords, or college portals.
3. **Absence is unexplained.** When a student is absent, there is no reliable, time-bound way for a parent to explain the absence or attach proof.
4. **Leave is informal.** Advance leave is often requested through verbal messages or unstructured WhatsApp chats with teachers, with no status tracking or audit trail.
5. **Attendance corrections are opaque.** Changes after submission may happen without a recorded reason, actor, previous value and timestamp.
6. **Academic performance is disconnected from attendance communication.** Parents may not see CIE/SEE updates in the same channel they already use.
7. **Notification sending is unreliable if tied directly to attendance save.** If WhatsApp is called inside the attendance submit action, either:
   - a WhatsApp outage blocks or rolls back attendance, or
   - attendance is saved but the parent is never notified and the event is lost.

CollegeConnect addresses these problems by making class-wise attendance the system of record, making WhatsApp the parent’s primary workspace, and guaranteeing that attendance data and notification intent are stored together reliably. WhatsApp delivery happens asynchronously with retry, history, internal idempotent processing, and reconciliation of ambiguous provider outcomes. CollegeConnect does not claim absolute exactly-once delivery by WhatsApp.

---

## 3. Project Objectives

**OBJ-1.** Provide a single system that connects Admin, Professor, Student and Parent around class-wise attendance and academic performance.

**OBJ-2.** Enable professors to mark and submit attendance per class session for the correct roster of students.

**OBJ-3.** Notify parents of attendance, absence, leave outcomes and academic performance primarily through WhatsApp.

**OBJ-4.** Allow parents to complete daily tasks through WhatsApp alone, without using the web dashboard.

**OBJ-5.** Support two distinct absence-related processes: (a) explanation of an already-recorded absence, and (b) advance leave requests before the absence occurs.

**OBJ-6.** Allow upload of supporting documents for leave and absence explanations.

**OBJ-7.** Allow authorized attendance correction with complete audit history.

**OBJ-8.** Manage and publish CIE and SEE marks to authorized parents and students.

**OBJ-9.** Restrict each parent to only the linked child or children, and restrict professors to assigned classes/subjects according to RBAC.

**OBJ-10.** Allow parents to manage notification preferences, including WhatsApp opt-in and opt-out rules defined in this SRS.

**OBJ-11.** Protect personal data, academic records and documents through authentication, authorization, audit and privacy controls.

**OBJ-12.** Produce operational and academic reports for administrators and authorized faculty.

**OBJ-13.** Deliver notifications reliably using the Transactional Outbox Pattern so that:
- attendance is never lost because WhatsApp failed;
- notification intent is never lost if attendance was saved;
- processing is internally idempotent (no automatic second send for a known successful event);
- absolute exactly-once WhatsApp delivery is **not** claimed; ambiguous outcomes are reconciled;
- failed messages can be retried automatically and manually according to FR-OUT-7.

**OBJ-14.** Keep the implementation maintainable and aligned with industry-standard practices on the approved technology stack, after this SRS is approved.

---

## 4. Project Scope

### 4.1 In scope

The following are in scope for CollegeConnect as specified by this SRS:

1. User accounts and RBAC for Admin, Professor, Student and Parent.
2. Student onboarding and academic enrolment against department, programme, semester, section and subjects.
3. Professor onboarding and subject/class assignment.
4. Parent registration, mobile number verification, WhatsApp opt-in, and parent–child linking.
5. Academic structure: departments, programmes, academic years, semesters, sections, subjects, professor assignments, timetables and class sessions.
6. Class-wise attendance marking, submission, locking rules, viewing and percentage calculation.
7. Absence case creation, parent explanation, review and status tracking.
8. Advance leave request, review, approval/rejection, status tracking and effect on attendance according to college policy (OD-LVE-EFFECT).
9. Supporting document upload, storage, access control and association to absence cases or leave requests.
10. Attendance correction by authorized users with mandatory reason and audit history.
11. CIE entry/publishing and SEE recording/publishing within the college’s defined components (OD-CIE-SCHEME, OD-SEE-SOURCE).
12. Optional parent web dashboard covering the same parent tasks as WhatsApp.
13. Student web/app views for own attendance and own marks (read-focused).
14. Professor views for assigned classes, attendance, leave/absence review as authorized, and CIE entry as authorized.
15. Admin configuration, user management, reports, notification monitoring and manual retry.
16. WhatsApp Business Platform Cloud API integration for outbound notifications and inbound parent actions.
17. Notification preferences.
18. Transactional Outbox, background processing, retries, idempotency, delivery history and admin monitoring.
19. Security, privacy, audit logs and parent–child access restrictions.
20. Reports listed in FR-RPT.

### 4.2 Out of scope (current version)

The following are **out of scope** unless later approved as a scope change:

1. Biometric, RFID, GPS, or facial-recognition attendance capture.
2. Native mobile applications for Android/iOS (responsive web is in scope; native apps are future).
3. Online fee payment and accounting.
4. Full Learning Management System (assignments submission grading workflow beyond CIE mark capture, video lectures, discussion forums).
5. University examination seating, hall tickets, and external university result portal replacement, except recording/publishing SEE marks the college is authorized to store.
6. Multi-college / multi-tenant SaaS for unrelated institutions (single college deployment is assumed unless OD-TENANT changes).
7. SMS or voice-call as a primary parent channel (WhatsApp is primary).
8. Social media publishing.
9. Payroll, HR, and staff appraisal.
10. Automatic generation of legal affidavits.
11. AI-based prediction of dropout or automatic shortage condonation.
12. Implementation work (architecture, database DDL, and code) prior to explicit SRS 1.3 approval.

### 4.3 Assumptions

1. The college will designate at least one Admin user to operate the system.
2. Each student has at least one parent/guardian mobile number that can use WhatsApp, or the college accepts that notifications cannot be delivered until a valid opted-in number exists.
3. The college will obtain and maintain a WhatsApp Business Platform account, a Cloud API phone number, and Meta template approvals.
4. Professors have access to a web browser to mark attendance.
5. The college will supply official student, faculty and academic structure data for onboarding.
6. Parents who opt in have consented to receive academic/attendance messages on WhatsApp.
7. Network connectivity is available to the hosted application and to WhatsApp Cloud API; temporary outages are handled by retry, not by blocking attendance.

### 4.4 Dependencies

1. WhatsApp Business Platform Cloud API availability and policy compliance.
2. College policy decisions listed as Open Decisions in Section 10.
3. Hosting, domain, TLS certificates and backup arrangements (operations), to be designed after SRS approval.
4. Approved CIE scheme and SEE recording process from the college/university regulations.

### 4.5 Approved technology constraints (learning project — implementation later)

These are **constraints on the future implementation**, not an authorization to start coding:

| Layer | Technology |
| --- | --- |
| Frontend | React.js |
| Backend | Java Spring Boot |
| Database | MySQL |
| ORM | Spring Data JPA / Hibernate |
| Authentication | Spring Security and JWT |
| Parent messaging | WhatsApp Business Platform Cloud API |

The implementation shall follow maintainable architecture and industry-standard practices (layered backend, clear module boundaries, RBAC on every API, audited corrections, outbox-based messaging). Architecture details are deferred until this SRS is approved.

---

## 5. User Roles and Permissions

### 5.1 Roles

CollegeConnect shall support exactly these primary roles in Version 1.3:

| Role | Description |
| --- | --- |
| Admin | Manages institutional setup, users, academic structure, oversight reports, attendance correction policy operations, notification monitoring and configuration |
| Professor | Manages assigned class sessions: attendance, authorized reviews, CIE entry for assigned subjects |
| Student | Views own attendance, own leave/absence statuses as allowed, and own CIE/SEE marks |
| Parent | Receives WhatsApp communication and performs parent tasks for linked children only; may optionally use the web dashboard |

A person shall not automatically inherit another role. If the same human is both a parent and a professor, the college may issue separate accounts or a later dual-role login decision (OD-DUAL-ROLE). Until that decision, permissions are evaluated per authenticated account role.

### 5.2 Permission matrix

Legend: **C** = create/submit, **R** = read, **U** = update, **D** = delete/deactivate, **A** = approve/reject, **M** = monitor/retry, **—** = not permitted.

| Capability | Admin | Professor | Student | Parent |
| --- | --- | --- | --- | --- |
| Manage departments, programmes, semesters, sections | C/R/U/D | — | — | — |
| Manage subjects and offerings | C/R/U | R (assigned) | R (own) | R (linked child) |
| Manage timetable and class sessions | C/R/U | R (assigned); limited U only if OD-TT-FACULTY allows | R (own) | R (linked child, limited) |
| Onboard / deactivate professors | C/R/U | R own profile | — | — |
| Onboard / deactivate students | C/R/U | R assigned roster | R own profile | R linked child profile (limited) |
| Register / verify parents and links | C/R/U | — | — | C/R own; U own profile |
| Mark and submit session attendance | R all; U per FR-COR | C/U assigned sessions before lock | R own | R linked child |
| View class attendance register | R | R assigned | — (only own row) | — (only child summary/details as allowed) |
| Create absence cases (system or authorized user) | R/U | R assigned; U if authorized | R own | R linked child |
| Explain recorded absence | R | R assigned | R own; C only if OD-STU-EXPLAIN allows | C/U linked child (until locked) |
| Submit advance leave | R | R assigned | C own if OD-STU-LEAVE allows | C linked child |
| Approve/reject leave | A as per OD-LVE-APPROVER | A if designated | — | — |
| Upload supporting documents | R; U/D per policy | R assigned | C own if allowed | C linked child |
| Correct attendance after submit | C/U with audit | C/U assigned within OD-COR-WINDOW if allowed | — | — |
| View correction audit history | R | R assigned (OD-COR-FACULTY-VIEW) | — | — |
| Enter CIE marks | R | C/U assigned subjects before publish lock | R own after publish | R linked child after publish |
| Record/publish SEE marks | C/R/U per OD-SEE-SOURCE | R assigned after publish unless OD says otherwise | R own after publish | R linked child after publish |
| Notification preferences | R/U global defaults; R user prefs | R own (staff alerts if any) | R own if student notifications exist | R/U own |
| WhatsApp opt-in / opt-out | R/M | — | — | C/U own |
| Notification outbox monitoring and manual retry | M | — | — | — |
| Reports | R all authorized | R assigned-scope | R own | R linked child |
| Security configuration | U | — | — | — |

### 5.3 Authorization rules (mandatory)

**SEC-RBAC-1.** Every authenticated request shall be authorized by role and resource scope. Possession of a valid JWT is not sufficient to access another person’s data.

**SEC-SCOPE-STU.** A student shall access only their own records.

**SEC-SCOPE-PAR.** A parent shall access only records of students with an **active verified link** to that parent. No browsing of other students, class-wide registers, or other parents’ data.

**SEC-SCOPE-PRF.** A professor shall access only assigned course offerings, class sessions, rosters, attendance, absence/leave items and CIE data. Access to unassigned departments or sections is forbidden.

**SEC-SCOPE-ADM.** Admin access is institution-wide for this single-college system, but every privileged change shall be audited.

**SEC-NO-ENUM.** The system shall not reveal whether a student, parent or mobile number exists to unauthorized callers beyond what is required for legitimate login or registration error messages. Registration error messages shall be generic where feasible.

**SEC-CHILD-PRIVACY.** Marks, documents, absence explanations and attendance details of a student shall not be sent to a WhatsApp number that is not opted-in and linked to that student.

### 5.4 Multi-child parents

**FR-LNK-1.** A parent account / opted-in WhatsApp number may be linked to more than one student if the college records those family relationships.

**FR-LNK-2.** When a parent action or query could apply to more than one linked child, the system shall require the parent to select the child (WhatsApp list/buttons or dashboard selector).

**FR-LNK-3.** Unlinking a parent shall immediately stop further child-specific notifications to that parent and revoke dashboard access to that child. Historical audit of the former link shall be retained.

**FR-LNK-4.** Linking shall require a college-approved verification process (see WF-2). A parent shall not self-link to an arbitrary USN/roll number without verification.

---

## 6. Functional Requirements

### 6.1 Authentication, account status and session (FR-AUTH)

**FR-AUTH-1.** Users shall authenticate with unique credentials. Default web authentication is username/email plus password, protected by Spring Security, issuing a JWT for API access after successful login.

**FR-AUTH-2.** Passwords shall be stored only as a strong one-way hash. Plain-text password storage is forbidden.

**FR-AUTH-3.** Login shall fail for deactivated users.

**FR-AUTH-4.** JWT expiry, refresh and logout/invalidation behaviour shall follow NFR-SEC. Exact token lifetimes are OD-JWT-TTL.

**FR-AUTH-5.** Role shall be included in the authenticated security context and enforced server-side on every operation.

**FR-AUTH-6.** Failed login attempts shall be rate-limited (NFR-SEC-LOCK).

**FR-AUTH-7.** Password reset shall use a verified channel. Parent WhatsApp shall not be used to send passwords in plain text.

**FR-AUTH-8.** Optional parent dashboard login may use the verified mobile number as identifier plus password or OTP (OD-PAR-LOGIN). WhatsApp use shall not require dashboard login.

### 6.2 Administrator functions (FR-ADM)

**FR-ADM-1.** Admin shall create, update and deactivate Admin (as permitted), Professor, Student and Parent records.

**FR-ADM-2.** Admin shall maintain departments, programmes, academic years, semesters, sections, subjects, subject offerings and professor-to-offering assignments.

**FR-ADM-3.** Admin shall maintain timetables and generate or open class sessions for attendance.

**FR-ADM-4.** Admin shall import or enter student enrolment into offerings/sections.

**FR-ADM-5.** Admin shall verify or override parent–student links according to college process, and shall revoke links.

**FR-ADM-6.** Admin shall configure notification defaults, retry policy parameters, and academic calendar working days as needed for scheduling.

**FR-ADM-7.** Admin shall access institution reports (FR-RPT).

**FR-ADM-8.** Admin shall monitor notification outbox, delivery history, failures and dead-letter events, and shall trigger authorized manual retries (FR-OUT-9).

**FR-ADM-9.** Admin shall perform or authorize attendance corrections according to FR-COR.

**FR-ADM-10.** Admin shall publish or unpublish CIE/SEE visibility according to FR-CIE and FR-SEE.

**FR-ADM-11.** Admin shall manage document retention access in accordance with privacy rules; mass public download of all documents is forbidden.

**FR-ADM-12.** Admin shall not be able to silently alter attendance without FR-COR audit fields.

### 6.3 Student functions (FR-STU)

**FR-STU-1.** A student shall view their own profile, timetable, class-wise attendance, running percentages, absence cases, leave requests and published CIE/SEE marks.

**FR-STU-2.** A student shall not mark their own attendance.

**FR-STU-3.** A student shall not view other students’ attendance, marks, documents or personal data.

**FR-STU-4.** Whether a student may submit an advance leave request or absence explanation is OD-STU-LEAVE and OD-STU-EXPLAIN. If disabled, only the parent (and Admin as exception) may submit those items.

**FR-STU-5.** A student shall see the status of their leave requests and absence cases (Submitted, Under Review, Approved, Rejected, More Info Required, Closed — exact labels may be localized but these states shall exist).

**FR-STU-6.** Student WhatsApp notifications are not the primary channel. If the college later enables student WhatsApp, that is FE-STU-WA.

### 6.4 Professor functions (FR-PRF)

**FR-PRF-1.** A professor shall view only assigned offerings, upcoming/past class sessions, and the session roster.

**FR-PRF-2.** A professor shall mark each student in an assigned open session with an allowed attendance status and submit the session attendance.

**FR-PRF-3.** A professor shall not submit attendance for unassigned sessions.

**FR-PRF-4.** After submission, further edits shall follow FR-COR only, not silent overwrite.

**FR-PRF-5.** A professor shall view absence cases and leave requests for students in assigned sessions/offerings, and shall approve/reject if designated by OD-LVE-APPROVER / OD-ABS-REVIEWER.

**FR-PRF-6.** A professor shall enter CIE marks for assigned subjects and submit them for publishing according to FR-CIE.

**FR-PRF-7.** A professor shall see whether session parent notifications are queued, sent, failed or retrying, at least at summary level for sessions they submitted (detailed provider payloads may be Admin-only).

**FR-PRF-8.** A professor shall upload or view supporting documents only when attached to cases they are authorized to review.

### 6.5 Parent functions — shared business rules (FR-PAR)

These rules apply to **both** WhatsApp and the optional web dashboard.

**FR-PAR-1.** Parents shall receive class-wise attendance notifications for linked children when notification preferences and opt-in allow it.

**FR-PAR-2.** Parents shall receive a daily attendance summary for each linked child according to schedule OD-DAILY-SUMMARY-TIME and preference flags.

**FR-PAR-3.** Parents shall check current attendance (class-wise recent sessions and percentage) without using the website.

**FR-PAR-4.** Parents shall check published CIE and SEE marks without using the website.

**FR-PAR-5.** Parents shall submit advance leave requests for a linked child.

**FR-PAR-6.** Parents shall explain already-recorded absences for a linked child.

**FR-PAR-7.** Parents shall upload supporting documents for leave or absence cases.

**FR-PAR-8.** Parents shall check leave request and absence-case status.

**FR-PAR-9.** Parents shall change notification preferences, including mute categories allowed by policy.

**FR-PAR-10.** Parents shall receive approval and rejection notifications for leave and, where configured, for absence-explanation review.

**FR-PAR-11.** The parent web dashboard is optional and shall provide the same parent tasks as WhatsApp. Lack of dashboard use shall not reduce WhatsApp capabilities.

**FR-PAR-12.** Parent actions shall identify the child, the academic context, and the actor (parent identity / WhatsApp number).

**FR-PAR-13.** Parents shall not edit attendance records.

**FR-PAR-14.** Parents shall not view class-wide registers or other children.

### 6.6 Academic structure, timetable and class sessions (FR-ACA)

**FR-ACA-1.** The system shall record Department, Programme, Academic Year, Semester, Section/Class, Subject, Subject Offering, Professor Assignment, Timetable Slot and Class Session as distinct logical entities.

**FR-ACA-2.** A Subject Offering shall belong to one academic term/section context and one subject.

**FR-ACA-3.** A Professor Assignment shall associate a professor with a subject offering. Multiple professors on one offering are allowed only if OD-MULTI-FACULTY allows it; otherwise one primary professor is required.

**FR-ACA-4.** A Timetable Slot shall define weekday, start time, end time, subject offering, and optional room (room is OD-ROOM).

**FR-ACA-5.** A Class Session shall be a specific occurrence derived from a timetable slot and a calendar date, or a uniquely created extra/remedial session linked to a subject offering.

**FR-ACA-6.** Each Class Session shall have a unique identity, date, start/end time, subject offering, responsible professor (the marker), and roster source.

**FR-ACA-7.** The roster of a Class Session shall be the students enrolled in that offering/section on that date, excluding students with status that makes them ineligible (for example discontinued), per OD-ROSTER-RULES.

**FR-ACA-8.** Cancelled sessions shall not accept attendance. If attendance was submitted before cancellation, OD-CANCEL-SESSION decides whether records are voided via FR-COR.

**FR-ACA-9.** Extra classes shall be creatable by Admin (and by Professor if OD-EXTRA-CLASS allows) and shall produce Class Sessions that follow the same attendance and notification rules.

### 6.7 Attendance management (FR-ATT)

**FR-ATT-1.** Attendance shall be captured **class-wise** (per Class Session), not as a single daily mark only. Daily summaries are aggregations of class-wise records.

**FR-ATT-2.** Allowed statuses shall include at least **Present** and **Absent**. Additional statuses such as Late, Authorized Leave, Unauthorized Leave, or Duty Leave are OD-ATT-STATUS. Until that decision, the system shall still distinguish Present, Absent, and Authorized Leave if leave approval exists.

**FR-ATT-3.** For every student on the session roster, a submitted session shall contain exactly one attendance record.

**FR-ATT-4.** Partial save as draft may be allowed before submit (OD-ATT-DRAFT). Drafts shall not trigger parent notifications. Only **submit** creates notification outbox events.

**FR-ATT-5.** Submit shall be performed by the assigned professor (or Admin override). Submit shall be an atomic business operation as defined in FR-OUT.

**FR-ATT-6.** After successful submit, the session shall enter a submitted/locked-for-ordinary-edit state. Ordinary re-marking is forbidden; corrections use FR-COR.

**FR-ATT-7.** Duplicate submit of the same session shall be rejected. If the client retries submit, the system shall not create a second set of attendance rows or a second set of parent-facing notifications.

**FR-ATT-8.** Each attendance record shall store: class session, student, subject (via offering), professor who submitted (or last corrected actor separately), status, submitted-at timestamp, and source action.

**FR-ATT-9.** Attendance percentages shall be computed from class-wise records using a documented formula. The eligibility threshold (for example 75%) is OD-ATT-ELIGIBILITY and shall not be hard-coded as a pretended college rule in this SRS. The system shall still compute and display percentages.

**FR-ATT-10.** If a student is not on the roster, the professor shall not add ad-hoc unmarked students during marking unless Admin has enrolled them. Guest attendance is out of scope.

**FR-ATT-11.** Present notifications may be enabled or disabled independently of Absent notifications via FR-NPF and OD-NOTIFY-PRESENT.

**FR-ATT-12.** System-generated daily summaries shall not replace class-wise notifications unless the parent preference explicitly chooses summary-only (FR-NPF).

**FR-ATT-13.** End-of-day processing shall confirm remaining unexplained absences for the day and notify parents according to WF-7, without creating duplicate absence cases for the same student+session.

### 6.8 Absence cases and explanations (FR-ABS)

**FR-ABS-1.** When a submitted attendance record has status Absent (or equivalent unauthorized absence), the system shall create or reuse an **Absence Case** for that student and class session.

**FR-ABS-2.** There shall be at most one open Absence Case per student per class session.

**FR-ABS-3.** An Absence Case shall store student, class session, subject, originating attendance record, timestamps, status, explanation text, reviewer, review comments, and linked documents.

**FR-ABS-4.** Parents shall be notified that an absence was recorded and that an explanation may be submitted, subject to preferences and opt-in.

**FR-ABS-5.** Parents shall submit an explanation through WhatsApp or dashboard within OD-ABS-WINDOW. After the window, submission may be locked except Admin override.

**FR-ABS-6.** Explanation shall not by itself change the attendance status. Status change happens only through review outcome and/or FR-COR according to OD-ABS-EFFECT.

**FR-ABS-7.** Reviewer (OD-ABS-REVIEWER) shall accept, reject, or request more information.

**FR-ABS-8.** Parent and authorized student shall see Absence Case status.

**FR-ABS-9.** Authorized-leave conversion, if policy allows, shall be an audited attendance correction or a controlled status transition, never an unaudited overwrite.

**FR-ABS-10.** End-of-day confirmation shall remind parents of unexplained absences still inside the window and shall not spam duplicates for already-explained cases.

### 6.9 Advance leave requests (FR-LVE)

**FR-LVE-1.** Advance leave is a request submitted **before** the start of the affected class session or day, according to OD-LVE-CUTOFF.

**FR-LVE-2.** A leave request shall include: child/student, requester (parent, or student if allowed), date range and/or specific sessions, reason category, reason text, optional documents, submitted-at, and status.

**FR-LVE-3.** Statuses shall include at least: Submitted, Under Review, Approved, Rejected, Cancelled, Expired.

**FR-LVE-4.** Approver is OD-LVE-APPROVER (examples the college may choose: assigned subject professor, class teacher/mentor, HoD, Admin). This SRS does not invent the official approver.

**FR-LVE-5.** On approve or reject, the requester shall be notified on WhatsApp if opted in.

**FR-LVE-6.** Approved leave shall affect future attendance marking as defined by OD-LVE-EFFECT (examples: auto-mark Authorized Leave; or show a banner so the professor marks accordingly). Until the college decides, the system shall at minimum **display approved leave to the professor at marking time** and shall not hide it.

**FR-LVE-7.** Leave shall not be used to rewrite historical absences except through FR-ABS + FR-COR policy. Advance leave is not a substitute for explaining a past absence.

**FR-LVE-8.** Overlapping duplicate active leave requests for the same student and same session shall be rejected or merged per OD-LVE-OVERLAP. Default until decided: reject exact duplicate active overlap.

**FR-LVE-9.** Parent shall query leave status through WhatsApp and dashboard.

**FR-LVE-10.** Cancellation by parent shall be allowed until review completes, unless OD-LVE-CANCEL says otherwise.

### 6.10 Attendance correction and audit history (FR-COR)

**FR-COR-1.** After session submit, any change to an attendance status shall be recorded as a **Correction**, not as a silent update.

**FR-COR-2.** A correction shall store: attendance record id, previous status, new status, actor, actor role, timestamp, mandatory reason, optional comment, and optional supporting reference (leave id, absence case id, document id).

**FR-COR-3.** Who may correct, and within what time window, is OD-COR-WHO and OD-COR-WINDOW. Admin shall always be able to correct with audit.

**FR-COR-4.** The full correction history of an attendance record shall be immutable (append-only). History rows shall not be deleted in normal operation.

**FR-COR-5.** If a correction changes a student to Absent, absence-case and notification rules shall run. If a correction changes a student from Absent to Present or Authorized Leave, related open absence cases shall be closed or updated, with notifications as configured.

**FR-COR-6.** Correction-triggered notifications shall use the outbox and shall be idempotent per correction event.

**FR-COR-7.** Reports and parent views shall show the **current** status and, to Admin/authorized professor, the audit history.

**FR-COR-8.** The original submitter identity shall remain visible even after correction.

### 6.11 Supporting document uploads (FR-DOC)

**FR-DOC-1.** Supporting documents may be attached to Absence Cases and Leave Requests.

**FR-DOC-2.** Allowed types, maximum size, virus scanning and count limits are OD-DOC-POLICY. Until decided, the logical requirement is: accept common document and image types used for medical/event proof, reject executable files.

**FR-DOC-3.** Documents uploaded via WhatsApp media shall be stored and linked to the correct case/student.

**FR-DOC-4.** Access to documents shall follow SEC-SCOPE rules. A parent sees only documents they or the college attached to their child’s cases. A professor sees documents for cases they may review. Students see own documents if policy allows.

**FR-DOC-5.** Documents shall not be sent onward to other parents.

**FR-DOC-6.** Deletion/replacement shall be audited. After reviewer decision, parent replacement may be locked.

**FR-DOC-7.** Storage shall be private. Documents shall not be served by unguessable-only URLs without authorization if that would leak academic medical information. Authorized download shall be required.

### 6.12 CIE management (FR-CIE)

**FR-CIE-1.** The system shall record CIE components and marks per student per subject offering.

**FR-CIE-2.** The set of CIE components (for example IA1, IA2, Assignment, Quiz) is OD-CIE-SCHEME. The system shall support multiple named components with max marks.

**FR-CIE-3.** Professors shall enter marks only for assigned offerings.

**FR-CIE-4.** Marks shall be validable against max marks. Negative marks are forbidden unless OD-CIE-NEGATIVE allows.

**FR-CIE-5.** Draft CIE shall not be visible to students/parents. Publish is an explicit action by Professor and/or Admin (OD-CIE-PUBLISHER).

**FR-CIE-6.** On publish, notification outbox events shall be created in the same transaction as the publish flag/marks finalization (FR-OUT).

**FR-CIE-7.** After publish, edits shall be audited analogous to FR-COR (CIE amendment history).

**FR-CIE-8.** Parents shall view published CIE through WhatsApp and dashboard; students through student view.

### 6.13 SEE management (FR-SEE)

**FR-SEE-1.** The system shall record SEE marks or grades per student per subject as the college is authorized to store.

**FR-SEE-2.** Source of SEE data (manual Admin entry, faculty entry, or import) is OD-SEE-SOURCE.

**FR-SEE-3.** SEE shall not be visible to students/parents until published.

**FR-SEE-4.** Publish shall create outbox notification events in the same transaction as visibility change.

**FR-SEE-5.** Parents and students shall view published SEE for linked/own records only.

**FR-SEE-6.** SEE amendments after publish shall be audited.

### 6.14 Reports (FR-RPT)

**FR-RPT-1.** Admin shall generate student-wise class-wise attendance reports for a date range.

**FR-RPT-2.** Admin shall generate subject-wise and section-wise attendance summary reports, including percentages.

**FR-RPT-3.** Admin shall generate shortage/below-threshold candidate reports **after** OD-ATT-ELIGIBILITY is configured. Until configured, percentage lists shall still be exportable without claiming an official eligibility cut-off.

**FR-RPT-4.** Admin shall generate absence-case and leave-request reports by status.

**FR-RPT-5.** Admin shall generate CIE and SEE reports after publish, scoped by offering/section.

**FR-RPT-6.** Admin shall generate notification delivery reports: queued, sent, delivered, failed, dead-letter, retries.

**FR-RPT-7.** Professors shall generate the same report types limited to assigned offerings.

**FR-RPT-8.** Exports should support a tabular format suitable for college office use (for example CSV/XLSX). Exact formats are a design choice after approval.

**FR-RPT-9.** Reports shall obey RBAC. A parent report is only the linked child.

### 6.15 Notification preferences (FR-NPF)

**FR-NPF-1.** Each parent shall have preference records covering at least:

- Class-wise absence notifications (on/off)
- Class-wise present notifications (on/off) if OD-NOTIFY-PRESENT allows present messages
- Daily summary (on/off)
- Leave approval/rejection (on/off — default on)
- Absence review outcome (on/off — default on)
- CIE published (on/off)
- SEE published (on/off)
- End-of-day unexplained absence reminder (on/off)

**FR-NPF-2.** Global defaults shall be configurable by Admin.

**FR-NPF-3.** Parents shall change preferences via WhatsApp and via dashboard.

**FR-NPF-4.** Opt-out of **all** WhatsApp messages is allowed via WA-OPT-OUT, except that the system shall still record the preference and shall not keep sending marketing-like academic spam. Whether legally mandatory safety messages exist is OD-MANDATORY-MSG. This product does not invent emergency-alert exceptions beyond college decision.

**FR-NPF-5.** Preference changes shall take effect for subsequently created outbox events. In-flight already-sent messages are not recalled from WhatsApp.

**FR-NPF-6.** Preferences are per parent. If a parent has multiple children, OD-NPF-PER-CHILD decides whether preferences are global to the parent or per child. Default until decided: per parent, applying to all linked children, with child still selected for queries.

### 6.16 Transactional Outbox Pattern and notification processing (FR-OUT)

#### 6.16.1 Purpose of the pattern (plain language)

When a professor submits attendance, two things must happen:

1. Save the attendance in CollegeConnect.
2. Inform parents on WhatsApp.

If the application tries to send WhatsApp **during** the save:

- WhatsApp may be down, slow, or rate-limited, and attendance submit would fail even though the college must keep the register; or
- attendance might save, then the application crashes before WhatsApp send, and the parent is never informed and the system forgets to try again.

The **Transactional Outbox Pattern** solves this as follows:

1. In **one database transaction**, save the business data (attendance records, absence cases as required, correction rows, published marks, and so on) **and** save **notification events** (outbox rows) that describe messages to send later.
2. If that transaction fails, **neither** attendance **nor** notification events are committed. The register does not move forward with missing events.
3. After **successful commit**, a **background worker** reads pending outbox events and sends WhatsApp messages through the WhatsApp Cloud API.
4. If WhatsApp fails, attendance **remains saved**. Only the outbox event is retried. WhatsApp failure shall **never** roll back attendance records.
5. Processing is **internally idempotent** for a known send outcome: the worker SHALL NOT automatically send the same outbox event again after the provider has accepted it and CollegeConnect has stored that fact. This is **not** a promise of absolute exactly-once WhatsApp delivery. If the application crashes after the provider accepted a message but before the provider message ID was stored, the outcome is **ambiguous** and SHALL follow the reconciliation procedure (FR-OUT-21), not a blind retry.

This pattern is a **requirement** of CollegeConnect, not an optional implementation hint.

#### 6.16.1.1 Delivery guarantees (plain language)

Three different ideas must not be mixed:

| Guarantee | What CollegeConnect promises |
| --- | --- |
| Reliable **intent** | If attendance (or another business action) commits, a matching outbox event is committed in the same transaction. |
| Internal **idempotent processing** | For one outbox event, automatic processing will not fire another Cloud API send once the send is known to have been accepted (provider message ID stored, or SENT/DELIVERED). |
| **Exactly-once WhatsApp delivery** | **Not promised.** WhatsApp is an external system. A timeout or process crash can occur after WhatsApp has accepted the message and before CollegeConnect stores the message ID. In that window a later send **could** duplicate a parent message unless reconciliation shows the first message already exists. |

CollegeConnect SHALL minimise that residual risk by storing provider IDs immediately, matching webhooks, classifying ambiguous attempts, and restricting retries as defined in FR-OUT-7 and FR-OUT-21.

#### 6.16.2 Outbox requirements

**FR-OUT-1.** When a professor submits attendance, attendance records (and required absence cases) **and** corresponding notification events shall be saved within the **same database transaction**.

**FR-OUT-2.** If the database transaction fails, neither attendance nor notification events shall be committed.

**FR-OUT-3.** After successful commit, a background worker shall process pending notification events.

**FR-OUT-4.** The worker shall send WhatsApp messages through the WhatsApp Cloud API.

**FR-OUT-5.** Failed messages shall be retried according to configurable retry policies (max attempts, backoff, quiet hours if OD-RETRY-QUIET).

**FR-OUT-6.** WhatsApp failures shall never roll back attendance records, CIE/SEE publishes, leave decisions, or other already-committed business data.

**FR-OUT-7.** Notification processing SHALL be **internally idempotent** per outbox event. Internal idempotency is an application-level rule. It is **not** an absolute exactly-once delivery guarantee from WhatsApp.

The following three Admin/worker actions SHALL be distinguished and SHALL stay consistent with WF-13:

1. **Retry (same event, new attempt).** If the event is **confirmed never to have been accepted** by the provider — no stored provider message ID, and a definite non-acceptance (API error that did not create a message, or reconciliation has proved no message exists) — the system SHALL retry using the **existing** outbox event and SHALL append a **new attempt** row (FR-OUT-15). The idempotency key SHALL NOT change.
2. **Explicit resend (new event).** If a message is already **SENT** or **DELIVERED** (or otherwise confirmed accepted) and an authorized Admin still needs the parent to receive the content again, the system SHALL create a **new** outbox event with a **new** idempotency key, SHALL record the actor, timestamp and auditable reason, and SHALL link the new event to the original event. Reusing the original event or original idempotency key for this purpose is forbidden.
3. **Ambiguous delivery outcome.** If the provider **MAY** have accepted the message but CollegeConnect crashed, timed out, or otherwise failed to store the provider message ID, the system SHALL **not** treat this as a confirmed never-accepted retry and SHALL **not** treat this as an explicit resend of a delivered message. It SHALL follow the reconciliation procedure in FR-OUT-21 **before** any further send.

Automatic workers SHALL NOT call the WhatsApp send API again for an event that is already SENT or DELIVERED, or that already has a stored provider message ID, except as the processing of a **new** resend event created under (2).

**FR-OUT-8.** The system shall store notification attempts, provider message IDs, delivery status and failure reasons.

**FR-OUT-9.** Admin shall have monitoring and manual retry capabilities for failed or stuck events. Manual retry SHALL apply FR-OUT-7: same-event retry only when never-accepted is confirmed; new event for explicit resend of sent/delivered; reconciliation first when the outcome is ambiguous. Every manual action SHALL be audited (actor, time, event, action type, reason).

**FR-OUT-10.** The same outbox mechanism shall be used for all parent WhatsApp notifications, including:

- class-wise attendance/absence
- daily summaries
- leave submitted (if configured)
- leave approved/rejected
- absence explanation review outcomes
- CIE published
- SEE published
- end-of-day unexplained absence reminders
- correction notifications that parents are allowed to receive

**FR-OUT-11.** Outbox event states shall include at least: PENDING, PROCESSING, SENT, DELIVERED, FAILED_RETRYABLE, FAILED_DEAD_LETTER, AMBIGUOUS_DELIVERY, CANCELLED, SKIPPED_PREFERENCE, SKIPPED_NO_OPTIN.

**FR-OUT-12.** Claiming an event for processing shall be concurrency-safe so two workers do not send the same event.

**FR-OUT-13.** Worker recovery after application restart shall resume PENDING and stale PROCESSING events (stuck timeout is configurable). No event shall be lost because the application process died. A stale PROCESSING event with no stored provider message ID SHALL be classified as an **ambiguous delivery outcome** until FR-OUT-21 reconciliation completes. It SHALL NOT be sent again automatically solely because the process restarted.

**FR-OUT-14.** Temporary WhatsApp API outages, HTTP 429 rate limits, and 5xx errors shall mark events FAILED_RETRYABLE and apply backoff. Permanent errors (invalid number, template rejected, 4xx that will not succeed on retry) shall go to FAILED_DEAD_LETTER with a reason code for Admin.

**FR-OUT-15.** Each attempt row shall store: event id, attempt number, started-at, finished-at, request correlation id, HTTP/provider error, provider message id if any, result.

**FR-OUT-16.** Webhook callbacks from WhatsApp (accepted, delivered, read, failed) shall update delivery status without creating new parent messages. Webhooks SHALL be matched using the stored provider message ID. If an inbound webhook refers to a message ID not yet stored, the system SHALL attach that ID to the matching in-flight or ambiguous attempt when correlation is possible, then apply FR-OUT-21.

**FR-OUT-17.** Daily summaries and end-of-day jobs shall write outbox events in a transaction with any business flags that record “summary generated for date D / student S / parent P”, using an idempotency key that includes the date so re-running the job cannot create a second sendable event for the same key. Subsequent Cloud API sends for that event still follow FR-OUT-7 and FR-OUT-21.

**FR-OUT-18.** If a parent has no verified opted-in WhatsApp number, the business transaction shall still commit attendance; the outbox event shall be SKIPPED_NO_OPTIN (or equivalent) and visible to Admin.

**FR-OUT-19.** Notification content shall include enough context to identify child name, class/section, subject, date, session time, and status, without including unrelated students.

**FR-OUT-20.** The outbox is the system of record for **send intent**. Application logs alone are not sufficient.

**FR-OUT-21.** Ambiguous delivery outcomes SHALL be handled through provider IDs, webhook reconciliation and controlled retries, as follows:

1. Record the attempt as incomplete/unknown (no confirmed accept, no confirmed reject), including request correlation identifier, timestamp and worker identity.
2. Persist the provider message ID **as soon as** the Cloud API returns it, in the same attempt update, before marking the event SENT (FR-OUT-22).
3. Wait for and apply WhatsApp webhook status callbacks (accepted, delivered, failed) matched by provider message ID and, where available, request correlation data. Webhooks SHALL update status only; they SHALL NOT create a new parent-facing send.
4. If reconciliation **confirms accepted or delivered**, set SENT or DELIVERED. No further send SHALL occur for this event except an explicit resend under FR-OUT-7 item 2.
5. If reconciliation **confirms never accepted** (provider failed to create a message, or provider reports failure with no message ID), the event MAY be retried as a **new attempt on the existing event** (FR-OUT-7 item 1).
6. If reconciliation is still unresolved after a configurable wait, the event SHALL enter **AMBIGUOUS_DELIVERY** (or dead-letter equivalent) for Admin review. Automatic send SHALL remain suppressed.
7. Admin MAY then complete reconciliation (inspect webhooks, provider logs, parent delivery). Only after never-accepted is confirmed MAY Admin use same-event retry. A send performed while the outcome is still ambiguous is a **controlled residual-risk action**: it MAY duplicate a parent message; the Admin confirmation SHALL record that risk, actor, time and reason. This is not the normal retry path.

**FR-OUT-22.** The system SHALL persist the provider message ID as soon as the WhatsApp Cloud API returns it, before the event is marked SENT. If the process can store the ID, it SHALL NOT rely on a later log scrape as the primary record.

**FR-OUT-23.** CollegeConnect SHALL NOT claim absolute exactly-once delivery to WhatsApp. The required guarantees are:

- **At-least-once send intent** after a successful business transaction (outbox committed with the business data).
- **At-most-one automatic Cloud API send** per outbox event once the outcome of the previous send is **known**.
- **Best-effort duplicate suppression** using idempotency keys, exclusive event claim, provider message IDs and webhook reconciliation.
- Residual duplicate risk exists only in the ambiguous-acceptance window and is handled by FR-OUT-21, not by pretending the window cannot occur.

### 6.17 Data consistency and logical relationships (FR-DC)

The following are logical integrity requirements. Physical foreign keys will be designed after SRS approval, but the implemented system shall enforce these relationships.

**FR-DC-1.** A Student belongs to the college academic structure (programme/section/term as applicable) and is uniquely identifiable by college identifier (USN/roll — OD-STU-ID).

**FR-DC-2.** A Subject Offering references exactly one Subject and a defined class context (term, section/programme).

**FR-DC-3.** A Professor is associated to Class Sessions only through assignment (or Admin-authorized substitute recorded on the session).

**FR-DC-4.** A Class Session references exactly one Subject Offering, one date/time window, and one responsible professor for submission.

**FR-DC-5.** An Attendance Record references exactly one Class Session and exactly one Student, and shall imply the correct Subject and Professor via the session. **Every attendance record must be associated with the correct student, subject, professor and class session.**

**FR-DC-6.** An Attendance Record’s student shall be on that session’s roster at submit time.

**FR-DC-7.** An Absence Case references exactly one originating Attendance Record (or an equivalent unique session+student pair) and therefore the same student, subject, professor and session.

**FR-DC-8.** A Leave Request references exactly one Student and a set of dates/sessions that belong to that student’s offerings. Leave shall not be filed against another student’s sessions.

**FR-DC-9.** A Supporting Document references exactly one owner entity (Absence Case or Leave Request) and inherits that entity’s student scope.

**FR-DC-10.** A Notification Outbox Event that is child-specific shall reference the student, the parent recipient, and the source entity (attendance record, absence case, leave request, CIE publish, SEE publish, correction). It shall not point at a different student than the source entity.

**FR-DC-11.** Parent–child access: a notification recipient parent shall have an active verified link to the event’s student at creation time. If the link is later revoked, new events shall not be created for that pair; already SENT messages are not deleted from WhatsApp.

**FR-DC-12.** CIE marks reference student + subject offering + component. SEE marks reference student + subject (or offering) as per OD-SEE-GRAIN.

**FR-DC-13.** Orphan records are forbidden: attendance without session, absence without attendance, document without case/leave, outbox event without source type and source id.

**FR-DC-14.** Cascading deletes of students or sessions in production shall be restricted. Deactivate/cancel shall be used instead of hard-delete where history exists.

**FR-DC-15.** Timetable changes shall not silently reassign historical class sessions to a different subject. Historical sessions remain as conducted.

---

## 7. WhatsApp Integration Requirements

### 7.1 Channel role

**WA-1.** WhatsApp shall be the **primary** communication channel for parents.

**WA-2.** The parent web dashboard is **optional**. All daily parent tasks in WA-TASK shall work without dashboard login.

**WA-3.** The system shall use the WhatsApp Business Platform **Cloud API**.

**WA-4.** Staff (Admin, Professor, Student) primary UI is the web application. Staff may receive operational WhatsApp messages only if later configured; that is not required for MVP parents.

### 7.2 Parent daily tasks that shall work on WhatsApp alone (WA-TASK)

The parent shall be able to:

| ID | Task | Direction |
| --- | --- | --- |
| WA-TASK-1 | Receive class-wise attendance notifications | Outbound |
| WA-TASK-2 | Receive daily attendance summaries | Outbound |
| WA-TASK-3 | Check attendance | Inbound query |
| WA-TASK-4 | Check CIE and SEE marks | Inbound query |
| WA-TASK-5 | Submit advance leave requests | Inbound action |
| WA-TASK-6 | Explain already-recorded absences | Inbound action |
| WA-TASK-7 | Upload supporting documents | Inbound media |
| WA-TASK-8 | Check leave request status | Inbound query |
| WA-TASK-9 | Change notification preferences | Inbound action |
| WA-TASK-10 | Receive approval and rejection notifications | Outbound |

**WA-TASK-11.** The parent shall also be able to check absence-case status (companion to WA-TASK-8).

**WA-TASK-12.** The parent shall receive a simple help/menu of available actions without using the website.

### 7.3 Opt-in, opt-out and consent

**WA-OPT-1.** The system shall not send academic WhatsApp notifications to a number until **opt-in** is recorded, except a one-time verification/opt-in invitation that the college is allowed to send under Meta and college policy (OD-OPTIN-METHOD).

**WA-OPT-2.** Opt-in shall be captured with: mobile number, parent identity, timestamp, method (web checkbox, keyword, registration form, Admin-assisted), and linked student(s) as applicable.

**WA-OPT-3.** Mobile verification shall prove control of the number (OTP or equivalent) before the number is treated as verified (WF-2).

**WA-OPT-4.** Parents shall opt out by a documented keyword (for example STOP) and via preferences. The system shall acknowledge opt-out and stop subsequent notifications.

**WA-OPT-5.** Re-opt-in shall require an explicit action (START/YES or dashboard).

**WA-OPT-6.** Template usage shall comply with Meta category and opt-in rules. CollegeConnect shall not spam promotional content.

**WA-OPT-7.** Changing a mobile number shall reset verification and require new opt-in for the new number. The old number shall stop receiving child data.

### 7.4 Templates, session window and inbound handling

**WA-TPL-1.** Business-initiated messages outside the customer-care window shall use **pre-approved templates**. The college must obtain Meta approval. This SRS lists required template purposes, not final approved template names.

Required template purposes:

| Template purpose ID | When used |
| --- | --- |
| TPL-ATT-ABSENT | Class-wise absence notification |
| TPL-ATT-PRESENT | Class-wise present notification, if enabled |
| TPL-ATT-SUMMARY | Daily summary |
| TPL-ABS-REMIND | End-of-day unexplained absence reminder |
| TPL-LVE-SUBMITTED | Leave received acknowledgement if needed outside session window |
| TPL-LVE-DECISION | Leave approved or rejected |
| TPL-ABS-DECISION | Absence explanation accepted/rejected/more info |
| TPL-CIE-PUB | CIE marks published |
| TPL-SEE-PUB | SEE marks published |
| TPL-VERIFY | Registration / number verification (if allowed) |
| TPL-COR | Attendance correction parent notice, if enabled |

**WA-TPL-2.** After a parent messages the business, session messages may be used for interactive flows (menus, questions, acknowledgements) within the provider window.

**WA-TPL-3.** Interactive lists/buttons should be used where the Cloud API allows, to reduce typing errors. Text commands shall still work for parents who type naturally.

**WA-TPL-4.** Inbound messages shall be authenticated as coming from WhatsApp webhooks with signature verification.

**WA-TPL-5.** Inbound messages from numbers not registered as parents shall receive a short unregistered reply and shall not disclose student data.

**WA-TPL-6.** Inbound commands shall be parsed in a case-insensitive, user-friendly way. Unknown text shall return the help menu rather than fail silently.

**WA-TPL-7.** Media inbound (images, PDF) shall be accepted for WA-TASK-7, size-limited, associated to the in-progress leave or absence flow. If the parent is not in a document flow, the system shall ask which case/leave the file belongs to.

**WA-TPL-8.** Language of templates is OD-LANG. Default content in this project is English until the college decides.

### 7.5 Suggested parent command set (normative behaviour, not UI design)

The system shall understand at least these intents, whether via button, list, or typed command:

| Intent | Parent goal |
| --- | --- |
| MENU / HELP | Show available actions |
| ATTENDANCE | Check attendance |
| SUMMARY | Request today’s or latest daily summary replay if allowed |
| MARKS / CIE / SEE | Check published marks |
| LEAVE | Start advance leave request |
| STATUS | Check leave/absence status |
| EXPLAIN | Explain a recorded absence |
| DOCUMENT | Upload/attach proof |
| PREFERENCES | Change notification preferences |
| STOP | Opt out |
| START | Opt in again |

Multi-child selection is mandatory when needed (FR-LNK-2).

### 7.6 Reliability, duplicates, history and recovery

**WA-REL-1.** For a given outbox idempotency key, the system SHALL NOT automatically send another WhatsApp Cloud API message when the previous send outcome is **known** (provider message ID stored, SENT, or DELIVERED). That is **internal idempotent processing**. It is **not** a promise of provider-level exactly-once delivery. Ambiguous outcomes SHALL follow FR-OUT-21 rather than a second automatic send.

**WA-REL-2.** Automatic retries shall follow FR-OUT-5, FR-OUT-7 item 1, and FR-OUT-14, and SHALL occur only when never-accepted is confirmed.

**WA-REL-3.** Failed messages shall be trackable by Admin with reason, student, parent number (masked in less-privileged views if required), last attempt, and next retry time.

**WA-REL-4.** Full notification delivery history shall be retained per NFR-DATA retention.

**WA-REL-5.** Sending shall occur in background processing, not in the professor’s HTTP request thread as a blocking dependency.

**WA-REL-6.** After application restart, pending events shall resume. Stuck PROCESSING events with no provider message ID SHALL be treated as ambiguous until reconciled (FR-OUT-13, FR-OUT-21).

**WA-REL-7.** Temporary WhatsApp API outages shall not block attendance submit and shall be recovered by retry.

**WA-REL-8.** Provider message IDs SHALL be stored as soon as returned and SHALL be used to reconcile webhook delivery, read and failure callbacks (FR-OUT-16, FR-OUT-22).

**WA-REL-9.** Manual Admin retry and resend SHALL be audited (who acted, when, which event, action type, reason).

**WA-REL-10.** Manual actions SHALL follow FR-OUT-7 and WF-13:
- confirmed never-accepted: retry the **existing** event with a new attempt;
- already SENT/DELIVERED: **new** event, new idempotency key, audited reason (explicit resend);
- ambiguous outcome: reconcile first; do not use the never-accepted retry path until never-accepted is confirmed.

### 7.7 Content and privacy on WhatsApp

**WA-PRIV-1.** Messages shall name the linked child clearly and shall not include other students’ names or USNs.

**WA-PRIV-2.** Marks messages shall include only published components.

**WA-PRIV-3.** Medical document content shall not be copied into WhatsApp text; the parent uploads, staff view in the secure app.

**WA-PRIV-4.** Message logs visible to support staff shall mask parts of phone numbers where feasible.

**WA-PRIV-5.** The system shall not use parent WhatsApp numbers for purposes outside CollegeConnect academic communication.

---

## 8. Application Workflows

Each workflow below is a required behaviour sequence. It must stay consistent with FR, WA and FR-DC.

### 8.1 WF-1 Student onboarding

1. Admin creates the student record with official identifier, name, programme, section, academic year, contact as available, and status Active.
2. Admin enrols the student in subject offerings/section roster.
3. The system creates a student login (credentials issued through a college-controlled process; passwords never sent as WhatsApp academic content).
4. Student can authenticate and view own empty or existing records.
5. Parent link is **not** automatic solely from a name match. Linking is WF-2.
6. Until a parent is linked and opted in, attendance can still be marked; notifications for that child are SKIPPED_NO_OPTIN and visible to Admin.

**Consistency:** Student identity used later in attendance must be this onboarded student, not a free-typed name.

### 8.2 WF-2 Parent registration and mobile verification

1. Admin invites parent, or parent begins registration using a college-provided student identifier plus a one-time verification code/token issued by the college (OD-PAR-LINK-PROOF). Self-association without proof is forbidden.
2. Parent enters mobile number intended for WhatsApp.
3. System sends a verification OTP (channel: OD-OTP-CHANNEL — SMS, WhatsApp template, or both).
4. Parent submits OTP. Number becomes verified.
5. Parent records opt-in consent (checkbox + timestamp, and/or WhatsApp START).
6. Admin (or automated rule if college later approves) activates the parent–child link.
7. Parent may set a dashboard password (optional usage).
8. Parent immediately can use WhatsApp menu tasks for the linked child.
9. Failed OTP retries are rate-limited.

**Consistency:** Notifications use only verified, opted-in numbers with an active link.

### 8.3 WF-3 Professor onboarding

1. Admin creates professor profile (name, employee id, department, contact, Active status).
2. Admin assigns professor to subject offerings.
3. Professor login is issued.
4. Professor sees only assigned offerings and sessions.
5. Substitute teaching for a session shall be an explicit session-level assignment by Admin (or OD-SUBSTITUTE process), recorded on the Class Session so FR-DC-3 holds.

### 8.4 WF-4 Class and timetable creation

1. Admin ensures academic year, semester, sections and subjects exist.
2. Admin creates subject offerings and professor assignments.
3. Admin creates timetable slots.
4. System materializes Class Sessions for working days (or Admin generates a date range).
5. Extra/cancelled sessions are recorded explicitly.
6. Roster for each session is derived from enrolments (FR-ACA-7).
7. Professors see upcoming sessions to mark.

**Consistency:** A session always has subject, professor, date/time and roster before attendance submit is allowed.

### 8.5 WF-5 Attendance marking

1. Professor opens an assigned Class Session that is not cancelled and not already submitted.
2. System shows the correct roster only.
3. Professor marks each student with an allowed status. Approved future leave, if any, is displayed (FR-LVE-6).
4. Professor submits.
5. In **one transaction**:
   - attendance records are written for every roster student;
   - absence cases are created for unauthorized absences;
   - outbox events are written for parents who should be notified, or SKIPPED_* events where applicable;
   - session status becomes Submitted.
6. If the transaction fails, the professor is told to retry; no partial session submit remains.
7. If the professor’s client retries after a success that the client did not see, FR-ATT-7 prevents duplicate rows and duplicate parent messages.

**Consistency:** Each attendance row references that session (hence subject and professor) and a roster student.

### 8.6 WF-6 WhatsApp attendance notification

1. After commit of WF-5, workers pick PENDING outbox events.
2. Worker claims event (FR-OUT-12), checks opt-in, link, and preferences.
3. If not sendable, mark SKIPPED_* with reason; do not call WhatsApp.
4. If sendable, send via Cloud API template or session message as required.
5. Store the attempt. Persist the provider message ID as soon as the Cloud API returns it, then mark SENT (FR-OUT-22). If the API call times out or the process crashes before the ID is stored, mark the attempt unknown and follow FR-OUT-21; do not mark SENT.
6. Webhooks later set DELIVERED/READ/FAILED using the provider message ID.
7. Professor submit response does not wait for WhatsApp delivery.

### 8.7 WF-7 End-of-day absence confirmation

1. At OD-EOD-TIME (college local time), a scheduled job identifies submitted sessions of that date with unauthorized absences whose Absence Cases are still unexplained and inside OD-ABS-WINDOW.
2. For each parent+student pair, the job opens a transaction that records “EOD reminder generated for date D” and inserts outbox events with idempotency key including date, student, parent, and type EOD_ABSENCE_REMIND.
3. Re-running the job the same day shall not create duplicate sendable events.
4. Parents receive a reminder to explain or upload proof via WhatsApp.
5. Already explained or closed cases are excluded.
6. Sessions not yet submitted are OD-EOD-UNSUBMITTED (remind professors vs skip). This SRS does not invent a penalty.

### 8.8 WF-8 Parent submitting absence explanations

1. Parent receives absence notification or uses EXPLAIN.
2. If multiple unexplained absences, parent selects child (if needed) then session/subject/date.
3. Parent submits explanation text.
4. Absence Case status becomes Under Review (or Submitted).
5. Attendance status is unchanged until policy review (FR-ABS-6).
6. Reviewer is notified in the web application (staff notification channel is in-app; WhatsApp to staff is not required).
7. Parent can later query STATUS.

### 8.9 WF-9 Parent uploading supporting proof

1. During leave or explanation flow, parent sends an image/PDF or uploads via dashboard.
2. System validates type/size (OD-DOC-POLICY).
3. Document is stored privately and linked to the correct Absence Case or Leave Request (FR-DC-9).
4. Parent receives acknowledgement on WhatsApp.
5. Reviewer can view the file in the web application.
6. If the parent sends a file with no active context, the system asks which case/leave to attach; it shall not attach to the wrong child.

### 8.10 WF-10 Advance leave request and approval

1. Parent starts LEAVE, selects child, dates/sessions, reason, optional documents.
2. System rejects if after OD-LVE-CUTOFF or overlapping duplicate (FR-LVE-8).
3. Leave Request is stored as Submitted; optional acknowledgement outbox event is written in the same transaction.
4. Designated approver reviews in web application.
5. Approver approves or rejects with comment.
6. In one transaction: leave status updates; any policy-driven attendance reservations are recorded; decision outbox events are written.
7. Parent receives approval or rejection on WhatsApp (WA-TASK-10).
8. At future marking time, professor sees approved leave (FR-LVE-6).
9. Parent can check STATUS at any time.

### 8.11 WF-11 Attendance correction

1. Authorized actor opens a submitted session record.
2. Actor changes one or more student statuses with a mandatory reason.
3. In one transaction: current attendance status updates; immutable correction history row is appended; absence cases are opened/closed as needed; outbox events for affected parents are inserted if preferences allow.
4. WhatsApp send happens asynchronously.
5. Audit history is visible to Admin and to professors if OD-COR-FACULTY-VIEW allows.

### 8.12 WF-12 CIE/SEE marks publishing

1. Professor/Admin enters CIE component marks (draft) for an offering.
2. Validation against max marks occurs.
3. Publisher executes Publish (OD-CIE-PUBLISHER).
4. Same transaction: publish flag/history and parent/student notification outbox events.
5. Parents may query MARKS on WhatsApp; they also receive TPL-CIE-PUB if opted in.
6. SEE follows the same publish discipline with OD-SEE-SOURCE and TPL-SEE-PUB.
7. Unpublished drafts never appear on WhatsApp.

### 8.13 WF-13 WhatsApp notification failure and recovery

This workflow SHALL stay consistent with FR-OUT-7. Attendance and other business data remain committed throughout. WhatsApp failure never rolls them back.

**A. Confirmed failure — provider did not accept the message**

1. Worker attempts send. The Cloud API returns a definite non-acceptance (for example documented 4xx that created no message) or a retryable error (429, 5xx, timeout) **and** no provider message ID is returned.
2. The attempt row is stored with failure reason. If never-accepted is certain, the event becomes FAILED_RETRYABLE (or FAILED_DEAD_LETTER for permanent 4xx). If the error is a timeout or crash with no ID, go to **C**, not this path.
3. For FAILED_RETRYABLE, backoff delays the next **attempt on the same event** (configurable). The idempotency key does not change.
4. If max automatic attempts are exceeded, the event becomes FAILED_DEAD_LETTER. Admin dashboard shows it. The professor is not blocked.
5. **Manual retry (same event, new attempt):** Admin MAY retry an event that is **confirmed never to have been accepted**. Retry uses the **existing** event and appends a new attempt. The action is audited.

**B. Confirmed success — then explicit resend**

6. On a successful Cloud API accept, persist the provider message ID immediately, mark SENT, and let webhooks set DELIVERED/READ/FAILED.
7. Automatic processing SHALL NOT send this event again.
8. **Explicit resend of an already SENT/DELIVERED message:** Admin creates a **new** outbox event with a **new** idempotency key, records the reason, actor and timestamp, and links it to the original event. The original event is unchanged. This is not a retry of the original event.

**C. Ambiguous delivery — provider MAY have accepted, application did not store the message ID**

9. Typical cause: the provider accepted the message, then the application crashed, the HTTP client timed out, or the process died before the provider message ID was stored.
10. The event SHALL be marked AMBIGUOUS_DELIVERY (or kept as stale PROCESSING classified as unknown). Automatic send SHALL be suppressed.
11. **Reconciliation procedure (mandatory before another send):**
    - persist any late provider response if it arrives;
    - match inbound WhatsApp webhooks by provider message ID and request correlation;
    - if a webhook or later lookup **confirms accepted/delivered**, set SENT/DELIVERED and stop (then only **B.8** can send again);
    - if reconciliation **confirms never accepted**, the event MAY follow **A.5** (same event, new attempt);
    - if still unknown after the wait, keep AMBIGUOUS_DELIVERY for Admin review.
12. Admin SHALL complete reconciliation before using retry. A send while still ambiguous is a controlled residual-risk action that MAY duplicate a parent message and SHALL be audited as such. It is not the default retry.

**D. Permanent errors and restart**

13. If permanent (opt-out, invalid number, rejected template), Admin/parent must fix data. Automatic retry SHALL NOT loop forever.
14. After application restart: PENDING events resume. Stale PROCESSING events **with** a stored provider message ID are completed via webhooks, not resent. Stale PROCESSING events **without** a provider message ID follow **C**, not a blind resume-and-send.

---

## 9. Non-Functional Requirements

### 9.1 Reliability and availability

**NFR-REL-1.** Attendance submit shall succeed independently of WhatsApp availability, provided the CollegeConnect database transaction succeeds.

**NFR-REL-2.** Notification sending shall resume after process restart, deploy, or crash (FR-OUT-13).

**NFR-REL-3.** Target availability for the web application during college working hours shall be defined operationally after hosting is chosen (OD-SLA). Until then, the requirement is: planned design shall avoid single-threaded blocking on WhatsApp during marking.

**NFR-REL-4.** Scheduled jobs (daily summary, EOD reminder) shall be re-runnable without creating a second sendable outbox event for the same idempotency key. Cloud API sends for those events still follow FR-OUT-7 and FR-OUT-21.

### 9.2 Performance

**NFR-PER-1.** Professor attendance screen shall load the session roster with interactive performance acceptable for typical section sizes (assume up to OD-SECTION-SIZE; until decided, design should remain comfortable for at least 80 students per session).

**NFR-PER-2.** Attendance submit of a full roster shall complete the **database transaction** without waiting for per-parent WhatsApp HTTP calls.

**NFR-PER-3.** WhatsApp sending shall be queued and rate-limited to respect Cloud API limits. Back-pressure shall delay events, not fail attendance.

**NFR-PER-4.** Parent inbound WhatsApp commands shall return an acknowledgement quickly; slower data assembly may follow as a second message if needed.

### 9.3 Scalability

**NFR-SCL-1.** The logical design shall support one college, multiple departments, multiple parallel sessions at peak hours, and burst notification load at period end.

**NFR-SCL-2.** Workers shall be horizontally scalable; claiming must remain exclusive (FR-OUT-12).

### 9.4 Security

**NFR-SEC-1.** All web traffic shall use HTTPS/TLS in any non-local deployment.

**NFR-SEC-2.** Authentication via Spring Security and JWT as constrained in Section 4.5.

**NFR-SEC-3.** Authorization is server-side RBAC plus resource scope (Section 5.3).

**NFR-SEC-4.** Secrets (JWT signing key, WhatsApp access tokens, webhook verify tokens, DB credentials) shall not be stored in source code.

**NFR-SEC-5.** SQL injection, XSS and CSRF shall be mitigated using framework standard protections.

**NFR-SEC-6.** Upload scanning/type checks shall reduce malware risk (OD-DOC-POLICY).

**NFR-SEC-7.** Audit logs for login success/failure, corrections, link changes, publish actions, and manual notification retries shall be retained.

**NFR-SEC-8.** Least privilege for database and API credentials.

**NFR-SEC-9.** WhatsApp webhook endpoints shall verify signatures and reject spoofed inbound messages.

**NFR-SEC-LOCK.** Repeated failed logins and OTP guesses shall be throttled.

### 9.5 Privacy

**NFR-PRI-1.** Personal data (names, phones, attendance, marks, documents) shall be processed only for CollegeConnect purposes.

**NFR-PRI-2.** Parent–child isolation is mandatory.

**NFR-PRI-3.** Data retention for attendance, audit, documents and message history is OD-RETENTION. Until decided, the requirement is: do not hard-delete audit or attendance history as part of normal operations.

**NFR-PRI-4.** Access to medical-style documents is limited to authorized reviewers and the submitting parent/student as allowed.

**NFR-PRI-5.** Exports and reports shall be limited to the requester’s scope.

### 9.6 Usability

**NFR-USE-1.** Parent WhatsApp language shall be simple, short, and action-oriented. Avoid jargon. Expand CIE/SEE on first use in a message where practical (“Internal marks (CIE)”).

**NFR-USE-2.** Professor marking UI shall minimize time at end of class: roster, mark, submit.

**NFR-USE-3.** The optional parent dashboard shall be usable on a mobile browser, but shall not be required.

**NFR-USE-4.** Error messages shall be understandable to non-technical users. Internal exception details shall not be shown to parents.

### 9.7 Maintainability and implementation quality (deferred execution)

**NFR-MAI-1.** After approval, implementation shall use maintainable module boundaries on the approved stack.

**NFR-MAI-2.** Outbox processing shall be a dedicated mechanism, not copied ad-hoc into each controller.

**NFR-MAI-3.** Requirement IDs shall be traceable to tests.

**NFR-MAI-4.** No Phase 2 architecture or coding is authorized by this document until SRS 1.3 is explicitly approved.

### 9.8 Auditability

**NFR-AUD-1.** Attendance corrections, leave decisions, parent link changes, CIE/SEE publish and amendments, document replacements, and notification manual retries shall be attributable to an actor and time.

**NFR-AUD-2.** Outbox attempt history is part of the audit trail for communication.

### 9.9 Observability

**NFR-OBS-1.** Admin shall see counts of PENDING, PROCESSING, SENT, DELIVERED, FAILED_RETRYABLE, FAILED_DEAD_LETTER and AMBIGUOUS_DELIVERY.

**NFR-OBS-2.** Alerts for worker not running or dead-letter spike are required at operations design time (OD-ALERTING).

### 9.10 Localization and time

**NFR-TIME-1.** All session dates/times shall use the college local timezone (OD-TZ, likely Asia/Kolkata for this project context but confirmed by college).

**NFR-TIME-2.** “End of day” and “daily summary” shall use that timezone, not the server’s default if different.

### 9.11 Compliance with WhatsApp / Meta rules

**NFR-WA-1.** The implementation shall comply with Meta’s current opt-in, template, and unique-message policies.

**NFR-WA-2.** If Meta policy conflicts with a desired message, the Open Decision process shall adjust templates rather than sending non-compliant traffic.

---

## 10. System Constraints and Open Decisions

### 10.1 System constraints

**CON-1.** Single college deployment unless OD-TENANT changes.

**CON-2.** WhatsApp Cloud API is the only primary parent messaging provider.

**CON-3.** Technology stack is fixed as in Section 4.5.

**CON-4.** Indian college academic concepts CIE and SEE are in scope; the exact mark scheme is not invented here.

**CON-5.** The product shall not block class registers on external API failure.

**CON-6.** No code, database implementation, or architecture phase starts automatically after this document is delivered; explicit approval is required.

**CON-7.** CollegeConnect does not replace the university’s official SEE conducting system.

### 10.2 Open decisions requiring college input

These items are **intentionally unresolved**. Implementers shall not invent production policy values.

| ID | Topic | Question for the college | Until decided |
| --- | --- | --- | --- |
| OD-ATT-STATUS | Attendance statuses | Which statuses exist beyond Present/Absent (Late, Duty, Authorized Leave, etc.)? | Present, Absent, and Authorized Leave if leave is approved |
| OD-ATT-ELIGIBILITY | Shortage rule | What percentage and formula defines exam eligibility / shortage? | Show percentages; do not auto-declare shortage legally |
| OD-ATT-DRAFT | Draft attendance | May professors save drafts before submit? | Drafts allowed only if they never notify |
| OD-NOTIFY-PRESENT | Present messages | Notify parents when Present, or only when Absent? | Absence + daily summary on; Present off until decided |
| OD-DAILY-SUMMARY-TIME | Summary schedule | When is the daily summary sent? | Configurable; not sent until time configured |
| OD-EOD-TIME | End-of-day job | When does unexplained-absence confirmation run? | Configurable |
| OD-EOD-UNSUBMITTED | Unmarked sessions | What if a professor has not submitted by EOD? | Do not invent penalties; Admin report of unsubmitted sessions |
| OD-ABS-WINDOW | Explanation window | How many hours/days can a parent explain an absence? | Configurable window required in software |
| OD-ABS-REVIEWER | Absence reviewer | Who accepts explanations: mentor, subject professor, HoD, Admin? | Configurable role mapping |
| OD-ABS-EFFECT | Explanation effect | Does an accepted explanation change Absent to Authorized Leave? | Default: do not auto-change without FR-COR |
| OD-LVE-CUTOFF | Leave cutoff | How far in advance, and until what time before class, can leave be filed? | Configurable; reject leave after session start until decided otherwise |
| OD-LVE-APPROVER | Leave approver | Who approves leave? | Configurable; Admin can always act as fallback |
| OD-LVE-EFFECT | Approved leave vs register | Auto-mark leave vs advisory banner only? | At minimum show banner to professor |
| OD-LVE-OVERLAP | Duplicate leave | Merge or reject overlapping requests? | Reject exact active overlap |
| OD-LVE-CANCEL | Leave cancel | Can parent cancel after approval? | Cancel allowed until session starts, if not decided |
| OD-STU-LEAVE | Student-filed leave | May students file leave, or parents only? | Parents only |
| OD-STU-EXPLAIN | Student-filed explanation | May students explain absences? | Parents only |
| OD-COR-WHO | Correction actors | May professors correct, or only Admin? | Professor within window + Admin always |
| OD-COR-WINDOW | Correction window | How long after submit can faculty correct? | Configurable |
| OD-COR-FACULTY-VIEW | Audit visibility | May professors see full correction history? | Professors see history for assigned sessions |
| OD-CIE-SCHEME | CIE components | Names, counts, max marks, weightage | Configurable components; no fake scheme |
| OD-CIE-PUBLISHER | Who publishes CIE | Professor, Admin, or both? | Professor submits, Admin may publish; both possible |
| OD-CIE-NEGATIVE | Negative marks | Allowed? | Not allowed |
| OD-SEE-SOURCE | SEE data source | Manual, import, faculty? | Admin/authorized entry until decided |
| OD-SEE-GRAIN | SEE association | Per subject or per offering? | Per subject offering if it exists |
| OD-DOC-POLICY | Files | Types, max size, max count, virus scan | Images + PDF; executables rejected; size configurable |
| OD-PAR-LOGIN | Dashboard login | Password vs OTP | Either allowed; WhatsApp does not need it |
| OD-PAR-LINK-PROOF | Link proof | How does a parent prove they are the guardian? | College-issued token / Admin verification |
| OD-OTP-CHANNEL | OTP delivery | SMS vs WhatsApp vs both | Must exist; channel configurable |
| OD-OPTIN-METHOD | Opt-in capture | Form, keyword, assisted | Record method; all three may be enabled |
| OD-MANDATORY-MSG | Mandatory messages | Any category parents cannot mute? | Only what college/legal later names |
| OD-NPF-PER-CHILD | Preferences grain | Per parent or per child? | Per parent for all children |
| OD-MULTI-FACULTY | Co-teaching | Multiple professors per offering? | One primary professor required |
| OD-SUBSTITUTE | Substitutes | How is a substitute assigned? | Admin sets session professor |
| OD-TT-FACULTY | Faculty timetable edits | Can professors create extra classes? | Admin only until decided |
| OD-EXTRA-CLASS | Extra class | Who may create? | Admin |
| OD-ROOM | Rooms | Track classrooms? | Optional field |
| OD-ROSTER-RULES | Roster | Include lateral entry, repeaters, held students? | Enrolment list is source of truth |
| OD-CANCEL-SESSION | Cancelled class | What happens to already submitted attendance? | Use FR-COR / void policy decided later |
| OD-STU-ID | Student identifier | USN, roll, both? | College official id, unique |
| OD-JWT-TTL | Token lifetime | Access/refresh minutes | Secure finite TTL required |
| OD-DUAL-ROLE | Dual roles | Same login for professor-parent? | Separate accounts until decided |
| OD-LANG | Language | English, Kannada, both? | English first |
| OD-TZ | Timezone | Confirm Asia/Kolkata? | College local time, configurable |
| OD-SECTION-SIZE | Peak roster | Maximum students per session | Design for ≥ 80 |
| OD-SLA | Availability SLA | Hosting hours and RPO/RTO | To be set with hosting |
| OD-RETENTION | Data retention | Years to keep attendance, chats, documents | No casual deletion of audit/attendance |
| OD-ALERTING | Ops alerts | Email/SMS to Admin if worker down | Admin dashboard minimum |
| OD-RETRY-QUIET | Quiet hours | Avoid parent messages at night? | Configurable sending window recommended |
| OD-TENANT | Multi-college | Needed? | Single college |
| OD-NOTIFY-COR | Correction notices | Always notify parent of corrections? | Notify if status becomes Absent or changes from Absent |

---

## 11. Risks and Mitigation

| ID | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| RISK-WA-OUTAGE | WhatsApp Cloud API unavailable | Parents not informed | Transactional Outbox; retries; attendance still saved; Admin dead-letter monitor |
| RISK-WA-DUP | Duplicate parent messages on retry | Trust loss, Meta policy issues | Internal idempotency; exclusive claim; same-event retry only when never-accepted is confirmed; SENT/DELIVERED only via new resend event; webhook reconciliation |
| RISK-WA-IDEM | Crash after provider accepted a message but before CollegeConnect stored the provider message ID | Ambiguous outcome; a later send MAY duplicate | Do not claim exactly-once; classify AMBIGUOUS_DELIVERY; persist provider ID immediately (FR-OUT-22); webhook reconciliation (FR-OUT-21); suppress automatic retry until reconciled; Admin controlled action if still unknown |
| RISK-WA-TPL | Templates not approved in time | Cannot send business-initiated notices | Start approval early; use session messages after parent replies; track TPL purposes in WA-TPL-1 |
| RISK-OPTIN | Sending without opt-in | Policy/legal violation | WA-OPT rules; SKIPPED_NO_OPTIN |
| RISK-WRONG-CHILD | Message or document attached to wrong student | Severe privacy incident | FR-LNK, FR-DC, child picker, roster checks |
| RISK-PROXY | Student marked present while absent | Academic integrity | Out of scope biometrics; still require professor accountability and audit |
| RISK-LATE-MARK | Professor submits hours late | Parent notified late | Unsubmitted session report; OD-EOD-UNSUBMITTED |
| RISK-POLICY | Building software on invented 75% or approver rules | Wrong college process | Open Decisions table; configurable policies |
| RISK-PII | Marks and medical files leak | Privacy harm | RBAC, private document access, WhatsApp content minimization |
| RISK-SEC-TOKEN | JWT or Cloud API token leak | Account/message takeover | Secrets management, HTTPS, short TTL, webhook signatures |
| RISK-SCALE | Period-end notification storm | Rate limits | Queue, backoff, 429 handling |
| RISK-PARENT-UX | Parents cannot use web, and WhatsApp menu is confusing | Objective failure | WhatsApp-first tasks, help menu, buttons, simple language |
| RISK-PARTIAL-TX | Attendance saved without outbox rows | Silent missing notices | Same-transaction requirement FR-OUT-1/2; tests |
| RISK-SCOPE-CREEP | Native apps, fees, LMS | Delay learning project | Section 4.2 out of scope |
| RISK-META-POLICY | Meta changes template rules | Delivery breakage | Isolate WhatsApp adapter; NFR-WA-2 |

---

## 12. Future Enhancements

These are **not** in current scope. They are preserved from the Version 1.1 future list and may be scheduled later.

**FE-1.** Native Android/iOS applications.

**FE-2.** Biometric / QR / geo-verified attendance.

**FE-3.** Student WhatsApp self-service (FE-STU-WA).

**FE-4.** SMS fallback if WhatsApp permanently fails for a number.

**FE-5.** Multi-language parent messages (Kannada/Hindi) after OD-LANG.

**FE-6.** Integration with existing college ERP / university result import automation.

**FE-7.** Analytics dashboards for chronic absenteeism and mentoring.

**FE-8.** Automated shortage condonation workflow.

**FE-9.** Mentoring notes and counsellor referrals.

**FE-10.** Multi-college tenancy.

**FE-11.** Voice notes from parents transcribed into explanations.

**FE-12.** Parent signature on leave using a legally reviewed e-sign process.

**FE-13.** Push notifications for the optional dashboard.

**FE-14.** Online fee or fine collection related to attendance (only if college later asks).

**FE-15.** AI assistance for office reports (human-reviewed).

---

## 13. Requirement Traceability

### 13.1 Objectives to requirements

| Objective | Primary requirements |
| --- | --- |
| OBJ-1 Four roles | Section 5, FR-ADM, FR-PRF, FR-STU, FR-PAR |
| OBJ-2 Class-wise attendance | FR-ACA, FR-ATT, WF-4, WF-5 |
| OBJ-3 WhatsApp communication | WA-*, FR-OUT, WF-6 |
| OBJ-4 WhatsApp-only parent tasks | WA-TASK-1–12, FR-PAR |
| OBJ-5 Absence vs advance leave | FR-ABS, FR-LVE, WF-8, WF-10 |
| OBJ-6 Documents | FR-DOC, WF-9 |
| OBJ-7 Corrections | FR-COR, WF-11 |
| OBJ-8 CIE/SEE | FR-CIE, FR-SEE, WF-12 |
| OBJ-9 Access restrictions | SEC-*, FR-LNK, FR-DC |
| OBJ-10 Preferences | FR-NPF, WA-OPT |
| OBJ-11 Security and privacy | NFR-SEC, NFR-PRI, NFR-AUD |
| OBJ-12 Reports | FR-RPT |
| OBJ-13 Reliable notifications | FR-OUT including FR-OUT-7 and FR-OUT-21–23, WA-REL, WF-13, NFR-REL |
| OBJ-14 Maintainable stack | Section 4.5, NFR-MAI, CON-3 |

### 13.2 Workflows to requirements

| Workflow | Requirements |
| --- | --- |
| WF-1 Student onboarding | FR-ADM-1, FR-ADM-4, FR-STU, FR-DC-1 |
| WF-2 Parent registration and mobile verification | FR-LNK, WA-OPT, FR-AUTH-8, SEC-SCOPE-PAR |
| WF-3 Professor onboarding | FR-ADM-1, FR-ADM-2, FR-PRF-1, FR-DC-3 |
| WF-4 Class and timetable creation | FR-ACA-* |
| WF-5 Attendance marking | FR-ATT-*, FR-OUT-1, FR-OUT-2, FR-DC-5, FR-DC-6 |
| WF-6 WhatsApp attendance notification | FR-OUT-3–8, FR-OUT-21–23, WA-TASK-1, WA-TPL, WA-REL |
| WF-7 End-of-day absence confirmation | FR-ATT-13, FR-ABS-10, FR-OUT-17 |
| WF-8 Absence explanations | FR-ABS, WA-TASK-6 |
| WF-9 Supporting proof | FR-DOC, WA-TASK-7, FR-DC-9 |
| WF-10 Advance leave and approval | FR-LVE, WA-TASK-5, WA-TASK-8, WA-TASK-10 |
| WF-11 Attendance correction | FR-COR, FR-OUT-6, FR-OUT-10 |
| WF-12 CIE/SEE publishing | FR-CIE, FR-SEE, WA-TASK-4 |
| WF-13 Notification failure and recovery | FR-OUT-5–16, FR-OUT-7, FR-OUT-21–23, WA-REL, NFR-REL |

### 13.3 Data entities to relationships

| Entity | Must relate to | Requirement |
| --- | --- | --- |
| Student | Programme/section, enrolments | FR-DC-1 |
| Subject / Offering | Term, section, subject | FR-DC-2 |
| Professor | Assignments, sessions | FR-DC-3 |
| Class Session | Offering (subject), professor, date/time, roster | FR-DC-4 |
| Attendance Record | Session + student (implies subject + professor) | FR-DC-5, FR-DC-6 |
| Absence Case | Attendance record / session+student | FR-DC-7 |
| Leave Request | Student + that student’s sessions/dates | FR-DC-8 |
| Supporting Document | Absence case or leave request | FR-DC-9 |
| Notification Event | Source entity + student + parent recipient | FR-DC-10, FR-DC-11 |

### 13.4 Version 1.1 preservation map

| Version 1.1 area to preserve | Where restated in 1.2 and 1.3 |
| --- | --- |
| User roles and permissions | Section 5 |
| Attendance management | FR-ATT, WF-5 |
| Absence cases and explanations | FR-ABS, WF-8 |
| Advance leave requests | FR-LVE, WF-10 |
| Attendance correction and audit history | FR-COR, WF-11 |
| CIE and SEE management | FR-CIE, FR-SEE, WF-12 |
| Supporting document uploads | FR-DOC, WF-9 |
| WhatsApp integration | Section 7 |
| Parent–child access restrictions | FR-LNK, SEC-SCOPE-PAR |
| Notification preferences | FR-NPF, WA-OPT |
| Security and privacy | Section 5.3, NFR-SEC, NFR-PRI |
| Reports | FR-RPT |
| Future enhancements | Section 12 |

### 13.5 Version 1.2 additions (did not remove 1.1)

| Addition | IDs |
| --- | --- |
| Transactional Outbox Pattern | FR-OUT-1 to FR-OUT-23, Section 6.16.1 and 6.16.1.1 explanation |
| Notification reliability | WA-REL-*, NFR-REL-*, WF-13 |
| Data consistency | FR-DC-1 to FR-DC-15 |
| Complete WhatsApp daily tasks | WA-TASK-* |
| Opt-in / opt-out / templates | WA-OPT-*, WA-TPL-* |
| Failure recovery workflow | WF-13 |

### 13.6 Version 1.3 corrections (did not remove 1.2)

| Correction | Where applied |
| --- | --- |
| Internal idempotency vs exactly-once WhatsApp delivery | Section 1.5 glossary; 6.16.1; 6.16.1.1; FR-OUT-7; FR-OUT-21–23; WA-REL-1; OBJ-13; RISK-WA-IDEM |
| SHALL / SHOULD / MAY terminology | Section 1.4 |
| Manual retry vs explicit resend vs ambiguous reconciliation | FR-OUT-7, FR-OUT-9, FR-OUT-13, WA-REL-10, WF-13 |

---

## 14. Approval

### 14.1 Statement

SRS Version 1.3 is submitted as the **complete, finalized requirements baseline** for CollegeConnect, pending explicit human approval.

Approval of this document means:

1. The scope in Section 4 is accepted.
2. Open Decisions in Section 10.2 remain open until the college fills them; they are not hidden as if already decided.
3. Implementation shall not silently drop any FR/WA/NFR listed here.
4. **Architecture, database implementation, and coding shall not start until the approver explicitly authorizes Phase 2.**

### 14.2 Approval table

| Role | Name | Signature | Date | Decision |
| --- | --- | --- | --- | --- |
| Product owner / student developer | Chandreshekar MG | | | ☐ Approve SRS 1.3 ☐ Approve with comments ☐ Reject |
| College academic coordinator (if applicable) | | | | ☐ Approve ☐ Approve with comments ☐ Reject |
| Faculty mentor (if applicable) | | | | ☐ Approve ☐ Approve with comments ☐ Reject |

### 14.3 Approval comments

_Record requested changes here. Do not start Phase 2 until comments are merged or explicitly waived._

| Date | Reviewer | Comment | Resolution |
| --- | --- | --- | --- |
| | | | |

### 14.4 Post-approval gate

| Gate | Status |
| --- | --- |
| SRS 1.2 delivered | Yes |
| SRS 1.3 delivered | Yes |
| SRS 1.3 explicitly approved by Chandreshekar | **Waiting** |
| Phase 2 — architecture | **Blocked until explicit approval** |
| Database implementation | **Blocked until explicit approval** |
| Application code generation | **Blocked until explicit approval** |

---

## Appendix A — Quality review checklist (Version 1.3)

| Check | Result |
| --- | --- |
| Section numbering 1–14 complete | Pass |
| Required subsections present (roles, FR, WhatsApp, workflows, NFR, constraints, risks, future, traceability, approval) | Pass |
| Requirement IDs unique within prefix groups | Pass |
| Duplicate capabilities consolidated (parent tasks specified once in FR-PAR and mapped in WA-TASK; sending reliability specified in FR-OUT and referenced by WA-REL rather than conflicting rules) | Pass |
| No requirement that WhatsApp failure rolls back attendance | Pass (FR-OUT-6, WF-5, WF-13) |
| No requirement that attendance can commit without outbox intent | Pass (FR-OUT-1, FR-OUT-2) |
| No claim of absolute exactly-once WhatsApp delivery | Pass (6.16.1.1, FR-OUT-7, FR-OUT-23, WA-REL-1) |
| Ambiguous outcomes use provider IDs, webhooks and controlled retries | Pass (FR-OUT-21, WF-13 path C) |
| FR-OUT-7 and WF-13 agree on retry vs resend vs reconcile | Pass |
| SHALL / SHOULD / MAY defined as mandatory / recommended / optional | Pass (Section 1.4) |
| Parent dashboard optional; WhatsApp sufficient | Pass (FR-PAR-11, WA-2) |
| Security and authorization explicit | Pass (Section 5.3, NFR-SEC) |
| WhatsApp opt-in, opt-out, templates | Pass (Section 7.3–7.4) |
| College-specific numbers/approvers not invented | Pass (Section 10.2) |
| Workflows 1–13 consistent with data relationships | Pass (Section 8 and 13.2–13.3) |
| Understandable to technical and non-technical readers | Pass (plain-language outbox and delivery-guarantee explanation; glossary) |
| Code/schema not included | Pass |

## Appendix B — Change summary from Version 1.1 to Version 1.2

See Section 13.5. No preserved Version 1.1 capability listed in Section 13.4 was removed.

## Appendix C — Change summary from Version 1.2 to Version 1.3

Only the three requested technical corrections were applied. All other Version 1.2 requirements are preserved. Details are in Section 13.6.

---

**End of CollegeConnect Software Requirements Specification Version 1.3**

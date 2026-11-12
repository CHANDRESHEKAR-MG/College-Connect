CREATE TABLE department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_department_name UNIQUE (name)
);

CREATE TABLE programme (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_programme_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT uk_programme_department_name UNIQUE (department_id, name)
);

CREATE TABLE academic_year (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_year_name UNIQUE (name),
    CONSTRAINT ck_academic_year_dates CHECK (end_date >= start_date)
);

CREATE TABLE semester (
    id BIGINT NOT NULL AUTO_INCREMENT,
    academic_year_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_semester_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id),
    CONSTRAINT uk_semester_year_name UNIQUE (academic_year_id, name),
    CONSTRAINT ck_semester_dates CHECK (end_date >= start_date)
);

CREATE TABLE section (
    id BIGINT NOT NULL AUTO_INCREMENT,
    programme_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_section_programme FOREIGN KEY (programme_id) REFERENCES programme (id),
    CONSTRAINT fk_section_semester FOREIGN KEY (semester_id) REFERENCES semester (id),
    CONSTRAINT uk_section_programme_semester_name UNIQUE (programme_id, semester_id, name)
);

CREATE TABLE subject (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subject_code UNIQUE (code)
);

CREATE TABLE subject_offering (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subject_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_offering_subject FOREIGN KEY (subject_id) REFERENCES subject (id),
    CONSTRAINT fk_offering_section FOREIGN KEY (section_id) REFERENCES section (id),
    CONSTRAINT fk_offering_semester FOREIGN KEY (semester_id) REFERENCES semester (id),
    CONSTRAINT uk_offering_subject_section_semester UNIQUE (subject_id, section_id, semester_id)
);

CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_identifier VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_login_identifier UNIQUE (login_identifier)
);

CREATE TABLE admin_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_profile_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT uk_admin_profile_account UNIQUE (account_id)
);

CREATE TABLE professor_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    employee_identifier VARCHAR(60) NOT NULL,
    department_id BIGINT NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_professor_profile_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT fk_professor_profile_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT uk_professor_profile_account UNIQUE (account_id),
    CONSTRAINT uk_professor_employee_identifier UNIQUE (employee_identifier)
);

CREATE TABLE student_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    college_identifier VARCHAR(60) NOT NULL,
    programme_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_student_profile_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT fk_student_profile_programme FOREIGN KEY (programme_id) REFERENCES programme (id),
    CONSTRAINT fk_student_profile_section FOREIGN KEY (section_id) REFERENCES section (id),
    CONSTRAINT uk_student_profile_account UNIQUE (account_id),
    CONSTRAINT uk_student_college_identifier UNIQUE (college_identifier)
);

CREATE TABLE parent_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    verified_mobile VARCHAR(24),
    mobile_verified_at TIMESTAMP(6),
    whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    opt_in_recorded_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_parent_profile_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT uk_parent_profile_account UNIQUE (account_id),
    CONSTRAINT uk_parent_verified_mobile UNIQUE (verified_mobile)
);

CREATE TABLE parent_student_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    verified_by_account_id BIGINT,
    verification_status VARCHAR(30) NOT NULL,
    activated_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_parent_student_link_parent FOREIGN KEY (parent_id) REFERENCES parent_profile (id),
    CONSTRAINT fk_parent_student_link_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT fk_parent_student_link_verifier FOREIGN KEY (verified_by_account_id) REFERENCES user_account (id)
);
CREATE INDEX ix_parent_student_link_parent_status ON parent_student_link (parent_id, verification_status);
CREATE INDEX ix_parent_student_link_student_status ON parent_student_link (student_id, verification_status);

CREATE TABLE whatsapp_consent_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    parent_student_link_id BIGINT,
    actor_account_id BIGINT,
    action VARCHAR(20) NOT NULL,
    method VARCHAR(40) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_whatsapp_consent_parent FOREIGN KEY (parent_id) REFERENCES parent_profile (id),
    CONSTRAINT fk_whatsapp_consent_link FOREIGN KEY (parent_student_link_id) REFERENCES parent_student_link (id),
    CONSTRAINT fk_whatsapp_consent_actor FOREIGN KEY (actor_account_id) REFERENCES user_account (id)
);
CREATE INDEX ix_whatsapp_consent_parent_time ON whatsapp_consent_event (parent_id, recorded_at);

CREATE TABLE professor_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professor_id BIGINT NOT NULL,
    offering_id BIGINT NOT NULL,
    valid_from DATE,
    valid_to DATE,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_professor_assignment_professor FOREIGN KEY (professor_id) REFERENCES professor_profile (id),
    CONSTRAINT fk_professor_assignment_offering FOREIGN KEY (offering_id) REFERENCES subject_offering (id),
    CONSTRAINT ck_professor_assignment_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_professor_assignment_professor_status ON professor_assignment (professor_id, status);
CREATE INDEX ix_professor_assignment_offering_status ON professor_assignment (offering_id, status);

CREATE TABLE enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    offering_id BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT fk_enrollment_offering FOREIGN KEY (offering_id) REFERENCES subject_offering (id),
    CONSTRAINT ck_enrollment_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_enrollment_student_status ON enrollment (student_id, status);
CREATE INDEX ix_enrollment_offering_status ON enrollment (offering_id, status);

CREATE TABLE timetable_slot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    weekday VARCHAR(12) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room VARCHAR(80),
    PRIMARY KEY (id),
    CONSTRAINT fk_timetable_slot_assignment FOREIGN KEY (assignment_id) REFERENCES professor_assignment (id),
    CONSTRAINT ck_timetable_slot_times CHECK (end_time > start_time)
);
CREATE INDEX ix_timetable_slot_assignment_weekday ON timetable_slot (assignment_id, weekday);

CREATE TABLE class_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slot_id BIGINT,
    assignment_id BIGINT NOT NULL,
    substitute_professor_id BIGINT,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_class_session_slot FOREIGN KEY (slot_id) REFERENCES timetable_slot (id),
    CONSTRAINT fk_class_session_assignment FOREIGN KEY (assignment_id) REFERENCES professor_assignment (id),
    CONSTRAINT fk_class_session_substitute FOREIGN KEY (substitute_professor_id) REFERENCES professor_profile (id),
    CONSTRAINT ck_class_session_times CHECK (end_time > start_time),
    CONSTRAINT uk_class_session_slot_date UNIQUE (slot_id, session_date)
);
CREATE INDEX ix_class_session_date_status ON class_session (session_date, status);
CREATE INDEX ix_class_session_assignment_date ON class_session (assignment_id, session_date);

CREATE TABLE session_roster (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_session_roster_session FOREIGN KEY (session_id) REFERENCES class_session (id),
    CONSTRAINT fk_session_roster_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT uk_session_roster_session_student UNIQUE (session_id, student_id)
);

CREATE TABLE attendance_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    submitted_by_professor_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_record_session FOREIGN KEY (session_id) REFERENCES class_session (id),
    CONSTRAINT fk_attendance_record_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT fk_attendance_record_submitter FOREIGN KEY (submitted_by_professor_id) REFERENCES professor_profile (id),
    CONSTRAINT uk_attendance_session_student UNIQUE (session_id, student_id),
    CONSTRAINT fk_attendance_record_roster FOREIGN KEY (session_id, student_id)
        REFERENCES session_roster (session_id, student_id)
);
CREATE INDEX ix_attendance_student_session ON attendance_record (student_id, session_id);
CREATE INDEX ix_attendance_session_status ON attendance_record (session_id, status);

CREATE TABLE attendance_correction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attendance_id BIGINT NOT NULL,
    actor_account_id BIGINT NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    correction_sequence INT NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_correction_record FOREIGN KEY (attendance_id) REFERENCES attendance_record (id),
    CONSTRAINT fk_attendance_correction_actor FOREIGN KEY (actor_account_id) REFERENCES user_account (id),
    CONSTRAINT uk_attendance_correction_sequence UNIQUE (attendance_id, correction_sequence)
);
CREATE INDEX ix_attendance_correction_created_at ON attendance_correction (created_at);

CREATE TABLE absence_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attendance_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    explanation VARCHAR(2000),
    reviewer_account_id BIGINT,
    review_comments VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_absence_case_attendance FOREIGN KEY (attendance_id) REFERENCES attendance_record (id),
    CONSTRAINT fk_absence_case_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES user_account (id),
    CONSTRAINT uk_absence_case_attendance UNIQUE (attendance_id)
);
CREATE INDEX ix_absence_case_status_created ON absence_case (status, created_at);

CREATE TABLE leave_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    requester_account_id BIGINT NOT NULL,
    reviewer_account_id BIGINT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason_category VARCHAR(80),
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    review_comment VARCHAR(2000),
    submitted_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_request_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT fk_leave_request_requester FOREIGN KEY (requester_account_id) REFERENCES user_account (id),
    CONSTRAINT fk_leave_request_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES user_account (id),
    CONSTRAINT ck_leave_request_dates CHECK (end_date >= start_date)
);
CREATE INDEX ix_leave_request_student_status ON leave_request (student_id, status);
CREATE INDEX ix_leave_request_status_dates ON leave_request (status, start_date, end_date);

CREATE TABLE leave_request_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    leave_request_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_request_session_request FOREIGN KEY (leave_request_id) REFERENCES leave_request (id),
    CONSTRAINT fk_leave_request_session_session FOREIGN KEY (session_id) REFERENCES class_session (id),
    CONSTRAINT uk_leave_request_session UNIQUE (leave_request_id, session_id)
);

CREATE TABLE cie_assessment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    offering_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    maximum_marks DECIMAL(7,2) NOT NULL,
    publication_status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_cie_assessment_offering FOREIGN KEY (offering_id) REFERENCES subject_offering (id),
    CONSTRAINT ck_cie_maximum_marks CHECK (maximum_marks > 0),
    CONSTRAINT uk_cie_assessment_offering_name UNIQUE (offering_id, name)
);

CREATE TABLE cie_mark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    marks DECIMAL(7,2) NOT NULL,
    revision_number INT NOT NULL DEFAULT 1,
    published_revision_number INT,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_cie_mark_assessment FOREIGN KEY (assessment_id) REFERENCES cie_assessment (id),
    CONSTRAINT fk_cie_mark_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT ck_cie_mark_revision CHECK (revision_number > 0),
    CONSTRAINT ck_cie_mark_published_revision CHECK (published_revision_number IS NULL OR published_revision_number <= revision_number),
    CONSTRAINT uk_cie_mark_assessment_student UNIQUE (assessment_id, student_id)
);

CREATE TABLE cie_mark_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cie_mark_id BIGINT NOT NULL,
    actor_account_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    previous_marks DECIMAL(7,2),
    new_marks DECIMAL(7,2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cie_mark_history_mark FOREIGN KEY (cie_mark_id) REFERENCES cie_mark (id),
    CONSTRAINT fk_cie_mark_history_actor FOREIGN KEY (actor_account_id) REFERENCES user_account (id),
    CONSTRAINT ck_cie_mark_history_revision CHECK (revision_number > 0),
    CONSTRAINT uk_cie_mark_history_revision UNIQUE (cie_mark_id, revision_number)
);

CREATE TABLE see_assessment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    offering_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    publication_status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_see_assessment_offering FOREIGN KEY (offering_id) REFERENCES subject_offering (id),
    CONSTRAINT uk_see_assessment_offering_name UNIQUE (offering_id, name)
);

CREATE TABLE see_mark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    see_assessment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    result_value VARCHAR(32) NOT NULL,
    revision_number INT NOT NULL DEFAULT 1,
    published_revision_number INT,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_see_mark_assessment FOREIGN KEY (see_assessment_id) REFERENCES see_assessment (id),
    CONSTRAINT fk_see_mark_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT ck_see_mark_revision CHECK (revision_number > 0),
    CONSTRAINT ck_see_mark_published_revision CHECK (published_revision_number IS NULL OR published_revision_number <= revision_number),
    CONSTRAINT uk_see_mark_assessment_student UNIQUE (see_assessment_id, student_id)
);

CREATE TABLE see_mark_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    see_mark_id BIGINT NOT NULL,
    actor_account_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    previous_value VARCHAR(32),
    new_value VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_see_mark_history_mark FOREIGN KEY (see_mark_id) REFERENCES see_mark (id),
    CONSTRAINT fk_see_mark_history_actor FOREIGN KEY (actor_account_id) REFERENCES user_account (id),
    CONSTRAINT ck_see_mark_history_revision CHECK (revision_number > 0),
    CONSTRAINT uk_see_mark_history_revision UNIQUE (see_mark_id, revision_number)
);

CREATE TABLE supporting_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    absence_case_id BIGINT,
    leave_request_id BIGINT,
    uploaded_by_account_id BIGINT NOT NULL,
    private_storage_key VARCHAR(512) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    scan_status VARCHAR(30) NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_supporting_document_absence FOREIGN KEY (absence_case_id) REFERENCES absence_case (id),
    CONSTRAINT fk_supporting_document_leave FOREIGN KEY (leave_request_id) REFERENCES leave_request (id),
    CONSTRAINT fk_supporting_document_uploader FOREIGN KEY (uploaded_by_account_id) REFERENCES user_account (id),
    CONSTRAINT ck_supporting_document_one_owner CHECK (
        (absence_case_id IS NULL AND leave_request_id IS NOT NULL)
        OR (absence_case_id IS NOT NULL AND leave_request_id IS NULL)
    ),
    CONSTRAINT ck_supporting_document_size CHECK (file_size_bytes > 0),
    CONSTRAINT uk_supporting_document_storage_key UNIQUE (private_storage_key)
);
CREATE INDEX ix_supporting_document_absence ON supporting_document (absence_case_id);
CREATE INDEX ix_supporting_document_leave ON supporting_document (leave_request_id);

CREATE TABLE notification_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    category VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_preference_parent FOREIGN KEY (parent_id) REFERENCES parent_profile (id),
    CONSTRAINT uk_notification_preference_parent_category UNIQUE (parent_id, category)
);

CREATE TABLE notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL,
    original_event_id BIGINT,
    source_type VARCHAR(60) NOT NULL,
    source_id BIGINT NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    payload_json VARCHAR(4000) NOT NULL,
    idempotency_key VARCHAR(190) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6),
    lease_until TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_outbox_student FOREIGN KEY (student_id) REFERENCES student_profile (id),
    CONSTRAINT fk_notification_outbox_parent FOREIGN KEY (parent_id) REFERENCES parent_profile (id),
    CONSTRAINT fk_notification_outbox_original FOREIGN KEY (original_event_id) REFERENCES notification_outbox (id),
    CONSTRAINT uk_notification_outbox_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_notification_outbox_attempt_count CHECK (attempt_count >= 0)
);
CREATE INDEX ix_notification_outbox_status_next_attempt ON notification_outbox (status, next_attempt_at);
CREATE INDEX ix_notification_outbox_parent_created ON notification_outbox (parent_id, created_at);

CREATE TABLE notification_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    request_correlation_id VARCHAR(190),
    provider_message_id VARCHAR(190),
    result VARCHAR(40) NOT NULL,
    delivery_status VARCHAR(30),
    provider_error_code VARCHAR(120),
    http_status SMALLINT,
    failure_reason VARCHAR(1000),
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_attempt_event FOREIGN KEY (event_id) REFERENCES notification_outbox (id),
    CONSTRAINT uk_notification_attempt_number UNIQUE (event_id, attempt_number),
    CONSTRAINT uk_notification_provider_message_id UNIQUE (provider_message_id)
);
CREATE INDEX ix_notification_attempt_correlation ON notification_attempt (request_correlation_id);

CREATE TABLE whatsapp_webhook_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_event_id VARCHAR(190),
    parent_id BIGINT,
    attempt_id BIGINT,
    event_type VARCHAR(40) NOT NULL,
    payload_hash VARCHAR(128),
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_whatsapp_webhook_parent FOREIGN KEY (parent_id) REFERENCES parent_profile (id),
    CONSTRAINT fk_whatsapp_webhook_attempt FOREIGN KEY (attempt_id) REFERENCES notification_attempt (id),
    CONSTRAINT uk_whatsapp_webhook_provider_event UNIQUE (provider_event_id)
);
CREATE INDEX ix_whatsapp_webhook_received_at ON whatsapp_webhook_event (received_at);

CREATE TABLE in_app_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_account_id BIGINT NOT NULL,
    source_type VARCHAR(60) NOT NULL,
    source_id BIGINT NOT NULL,
    title VARCHAR(240) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    read_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_in_app_notification_recipient FOREIGN KEY (recipient_account_id) REFERENCES user_account (id)
);
CREATE INDEX ix_in_app_notification_recipient_read_created
    ON in_app_notification (recipient_account_id, read_at, created_at);

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_account_id BIGINT,
    actor_role VARCHAR(30),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_account_id) REFERENCES user_account (id)
);
CREATE INDEX ix_audit_log_actor_time ON audit_log (actor_account_id, created_at);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id, created_at);

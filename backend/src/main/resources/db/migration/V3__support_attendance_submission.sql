ALTER TABLE attendance_record
    ADD COLUMN source_action VARCHAR(40) NOT NULL DEFAULT 'PROFESSOR_SUBMIT';

ALTER TABLE notification_outbox
    MODIFY parent_id BIGINT NULL;

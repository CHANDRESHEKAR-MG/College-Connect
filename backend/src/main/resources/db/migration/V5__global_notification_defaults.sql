CREATE TABLE notification_default_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_by_account_id BIGINT,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_default_actor FOREIGN KEY (updated_by_account_id) REFERENCES user_account (id),
    CONSTRAINT uk_notification_default_category UNIQUE (category)
);

INSERT INTO notification_default_preference (category, enabled, updated_at) VALUES
    ('ATTENDANCE_ABSENT', TRUE, CURRENT_TIMESTAMP(6)),
    ('ATTENDANCE_PRESENT', FALSE, CURRENT_TIMESTAMP(6)),
    ('ATTENDANCE_SUMMARY', TRUE, CURRENT_TIMESTAMP(6)),
    ('LEAVE', TRUE, CURRENT_TIMESTAMP(6)),
    ('LEAVE_SUBMITTED', FALSE, CURRENT_TIMESTAMP(6)),
    ('ABSENCE', TRUE, CURRENT_TIMESTAMP(6)),
    ('ABSENCE_REMINDER', FALSE, CURRENT_TIMESTAMP(6)),
    ('CIE', FALSE, CURRENT_TIMESTAMP(6)),
    ('SEE', FALSE, CURRENT_TIMESTAMP(6)),
    ('ALL_MESSAGES', TRUE, CURRENT_TIMESTAMP(6));

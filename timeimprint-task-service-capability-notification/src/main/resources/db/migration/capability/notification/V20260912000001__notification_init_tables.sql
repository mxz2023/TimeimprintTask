-- G01 · Notification capability tables (05-DATABASE.md v2.2)
-- FK to platform tables; ON DELETE/UPDATE RESTRICT.

CREATE TABLE tt_notification (
    notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    definition_id BIGINT UNSIGNED NOT NULL,
    instance_id BIGINT UNSIGNED NOT NULL,
    transition_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NULL,
    purpose VARCHAR(32) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT fk_notification_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_transition FOREIGN KEY (transition_id)
        REFERENCES tt_task_transition (transition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_notification_instance (instance_id, created_at, notification_id),
    INDEX ix_notification_transition (transition_id, notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_inbox (
    inbox_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    notification_id BIGINT UNSIGNED NOT NULL,
    action_job_id BIGINT UNSIGNED NOT NULL,
    definition_id BIGINT UNSIGNED NOT NULL,
    instance_id BIGINT UNSIGNED NOT NULL,
    recipient_type VARCHAR(32) NOT NULL,
    recipient_id VARCHAR(128) NOT NULL,
    read_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (inbox_id),
    CONSTRAINT fk_inbox_notification FOREIGN KEY (notification_id)
        REFERENCES tt_notification (notification_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inbox_action_job FOREIGN KEY (action_job_id)
        REFERENCES tt_action_job (action_job_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inbox_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inbox_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_inbox_action UNIQUE (action_job_id),
    CONSTRAINT uk_inbox_notification_recipient UNIQUE (notification_id, recipient_type, recipient_id),
    INDEX ix_inbox_recipient (tenant_id, recipient_type, recipient_id, created_at, inbox_id),
    INDEX ix_inbox_unread (tenant_id, recipient_type, recipient_id, read_at, created_at, inbox_id),
    INDEX ix_inbox_instance (instance_id, created_at, inbox_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

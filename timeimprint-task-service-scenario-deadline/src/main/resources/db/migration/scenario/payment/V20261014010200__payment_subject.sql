-- P09 S05 manual payment subject and events.

CREATE TABLE tt_payment_subject (
    subject_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    paid_fen BIGINT UNSIGNED NOT NULL,
    period_label VARCHAR(40) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    local_time VARCHAR(8) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT uk_payment_definition UNIQUE (definition_id),
    CONSTRAINT fk_payment_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_payment_status CHECK (status IN ('UNPAID', 'PARTIAL', 'PAID', 'CANCELLED', 'OVERDUE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_payment_event (
    event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    amount_fen BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    recorded_at DATETIME(0) NOT NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT uk_payment_event_request UNIQUE (definition_id, request_id),
    CONSTRAINT fk_payment_event_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_payment_event_type CHECK (event_type IN ('CONFIRM', 'REVERSE', 'CANCEL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

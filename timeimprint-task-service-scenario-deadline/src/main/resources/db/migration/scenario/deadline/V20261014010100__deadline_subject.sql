-- P09 S04 deadline subject and renewal history.

CREATE TABLE tt_deadline_subject (
    subject_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    local_time VARCHAR(8) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT uk_deadline_definition UNIQUE (definition_id),
    CONSTRAINT fk_deadline_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_deadline_type CHECK (subject_type IN ('DOCUMENT', 'CONTRACT', 'SUBSCRIPTION', 'WARRANTY')),
    CONSTRAINT chk_deadline_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_deadline_renewal (
    renewal_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    previous_due_date DATE NOT NULL,
    new_due_date DATE NOT NULL,
    recorded_at DATETIME(0) NOT NULL,
    PRIMARY KEY (renewal_id),
    CONSTRAINT fk_deadline_renewal_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

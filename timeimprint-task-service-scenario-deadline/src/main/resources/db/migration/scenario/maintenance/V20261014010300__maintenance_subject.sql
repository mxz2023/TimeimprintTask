-- P10 S14 maintenance subject and completion history.

CREATE TABLE tt_maintenance_subject (
    subject_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    subject_name VARCHAR(40) NOT NULL,
    interval_days INT UNSIGNED NOT NULL,
    next_due_date DATE NOT NULL,
    local_time VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT uk_maintenance_definition UNIQUE (definition_id),
    CONSTRAINT fk_maintenance_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_maintenance_status CHECK (status = 'ACTIVE'),
    CONSTRAINT chk_maintenance_interval CHECK (interval_days BETWEEN 1 AND 3650)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_maintenance_completion (
    completion_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    instance_id BIGINT UNSIGNED NOT NULL,
    completed_on DATE NOT NULL,
    previous_due_date DATE NOT NULL,
    next_due_date DATE NOT NULL,
    recorded_at DATETIME(0) NOT NULL,
    PRIMARY KEY (completion_id),
    CONSTRAINT uk_maintenance_completion_instance UNIQUE (instance_id),
    CONSTRAINT fk_maintenance_completion_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_maintenance_completion_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

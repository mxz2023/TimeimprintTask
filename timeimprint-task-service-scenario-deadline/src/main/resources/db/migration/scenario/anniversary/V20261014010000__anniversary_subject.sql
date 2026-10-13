-- P09 S03 anniversary subject and date revisions.

CREATE TABLE tt_anniversary_subject (
    subject_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(16) NOT NULL,
    subject_name VARCHAR(40) NOT NULL,
    month_value TINYINT UNSIGNED NOT NULL,
    day_value TINYINT UNSIGNED NOT NULL,
    start_year INT UNSIGNED NULL,
    local_time VARCHAR(8) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT uk_anniversary_definition UNIQUE (definition_id),
    CONSTRAINT fk_anniversary_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_anniversary_kind CHECK (kind IN ('BIRTHDAY', 'ANNIVERSARY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_anniversary_revision (
    revision_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    month_value TINYINT UNSIGNED NOT NULL,
    day_value TINYINT UNSIGNED NOT NULL,
    start_year INT UNSIGNED NULL,
    recorded_at DATETIME(0) NOT NULL,
    PRIMARY KEY (revision_id),
    CONSTRAINT fk_anniversary_revision_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

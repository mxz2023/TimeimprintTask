-- G01 · Platform public tables (05-DATABASE.md v2.2)
-- InnoDB, utf8mb4, utf8mb4_bin; FK ON DELETE/UPDATE RESTRICT; no cascade deletes.

CREATE TABLE tt_task_definition (
    definition_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    scenario_key VARCHAR(64) NOT NULL,
    scenario_schema_version INT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NULL,
    scenario_config_json JSON NOT NULL,
    scenario_config_hash BINARY(32) NOT NULL,
    control_state VARCHAR(16) NOT NULL,
    control_generation BIGINT UNSIGNED NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    paused_at DATETIME(0) NULL,
    retired_at DATETIME(0) NULL,
    PRIMARY KEY (definition_id),
    CONSTRAINT uk_definition_tenant_id UNIQUE (tenant_id, definition_id),
    CONSTRAINT chk_definition_control_state CHECK (control_state IN ('ACTIVE', 'PAUSED', 'RETIRED')),
    CONSTRAINT chk_definition_revision_range CHECK (
        revision BETWEEN 1 AND 9007199254740991
        AND control_generation BETWEEN 1 AND 9007199254740991
    ),
    CONSTRAINT chk_definition_paused_at CHECK (control_state <> 'PAUSED' OR paused_at IS NOT NULL),
    CONSTRAINT chk_definition_retired_at_active CHECK (control_state = 'RETIRED' OR retired_at IS NULL),
    CONSTRAINT chk_definition_retired_at_terminal CHECK (control_state <> 'RETIRED' OR retired_at IS NOT NULL),
    INDEX ix_definition_list (tenant_id, updated_at, definition_id),
    INDEX ix_definition_filter (tenant_id, scenario_key, control_state, updated_at, definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_trigger_binding (
    trigger_binding_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    binding_key VARCHAR(64) NOT NULL,
    provider_key VARCHAR(64) NOT NULL,
    schema_version INT UNSIGNED NOT NULL,
    config_json JSON NOT NULL,
    config_hash BINARY(32) NOT NULL,
    binding_state VARCHAR(16) NOT NULL,
    schedule_generation BIGINT UNSIGNED NOT NULL,
    next_fire_at DATETIME(0) NULL,
    cursor_json JSON NOT NULL,
    exhausted TINYINT(1) NOT NULL DEFAULT 0,
    revision BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (trigger_binding_id),
    CONSTRAINT fk_trigger_binding_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_trigger_definition_binding UNIQUE (definition_id, binding_key),
    CONSTRAINT chk_trigger_binding_state CHECK (binding_state IN ('ACTIVE', 'PAUSED', 'RETIRED')),
    CONSTRAINT chk_trigger_revision_range CHECK (revision BETWEEN 1 AND 9007199254740991),
    INDEX ix_trigger_due (provider_key, binding_state, next_fire_at, trigger_binding_id),
    INDEX ix_trigger_live_schema (provider_key, binding_state, schema_version, trigger_binding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_task_instance (
    instance_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    trigger_binding_id BIGINT UNSIGNED NULL,
    schedule_generation BIGINT UNSIGNED NULL,
    definition_control_generation BIGINT UNSIGNED NOT NULL,
    occurrence_key VARCHAR(160) NULL,
    occurrence_at DATETIME(0) NULL,
    due_at DATETIME(0) NULL,
    lifecycle_category VARCHAR(16) NOT NULL,
    scenario_state VARCHAR(64) NOT NULL,
    scenario_schema_version INT UNSIGNED NOT NULL,
    scenario_snapshot_json JSON NOT NULL,
    snapshot_hash BINARY(32) NOT NULL,
    title_snapshot VARCHAR(200) NOT NULL,
    description_snapshot VARCHAR(4000) NULL,
    revision BIGINT UNSIGNED NOT NULL,
    terminal_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (instance_id),
    CONSTRAINT fk_instance_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_instance_trigger_binding FOREIGN KEY (trigger_binding_id)
        REFERENCES tt_trigger_binding (trigger_binding_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_instance_occurrence UNIQUE (
        definition_id,
        trigger_binding_id,
        schedule_generation,
        definition_control_generation,
        occurrence_key
    ),
    CONSTRAINT chk_instance_lifecycle_category CHECK (lifecycle_category IN ('WAITING', 'ACTIVE', 'TERMINAL')),
    CONSTRAINT chk_instance_revision_range CHECK (revision BETWEEN 1 AND 9007199254740991),
    CONSTRAINT chk_instance_control_generation CHECK (definition_control_generation BETWEEN 1 AND 9007199254740991),
    CONSTRAINT chk_instance_trigger_occurrence_shape CHECK (
        (
            trigger_binding_id IS NULL
            AND schedule_generation IS NULL
            AND occurrence_key IS NULL
        )
        OR (
            trigger_binding_id IS NOT NULL
            AND schedule_generation IS NOT NULL
            AND occurrence_key IS NOT NULL
        )
    ),
    CONSTRAINT chk_instance_terminal_at CHECK (
        (lifecycle_category = 'TERMINAL' AND terminal_at IS NOT NULL)
        OR (lifecycle_category IN ('WAITING', 'ACTIVE') AND terminal_at IS NULL)
    ),
    INDEX ix_instance_definition (definition_id, occurrence_at, instance_id),
    INDEX ix_instance_lifecycle (definition_id, lifecycle_category, occurrence_at, instance_id),
    INDEX ix_instance_scenario (definition_id, scenario_state, updated_at, instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_task_participant (
    participant_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    instance_id BIGINT UNSIGNED NULL,
    instance_scope_id BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(instance_id, 0)) STORED,
    principal_type VARCHAR(32) NOT NULL,
    principal_id VARCHAR(128) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    metadata_json JSON NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (participant_id),
    CONSTRAINT fk_participant_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_participant_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_participant_scope UNIQUE (
        definition_id,
        instance_scope_id,
        principal_type,
        principal_id,
        role_code
    ),
    INDEX ix_participant_principal (principal_type, principal_id, role_code, definition_id, instance_scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_task_signal (
    signal_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    definition_id BIGINT UNSIGNED NOT NULL,
    trigger_binding_id BIGINT UNSIGNED NULL,
    instance_id BIGINT UNSIGNED NULL,
    definition_control_generation BIGINT UNSIGNED NOT NULL,
    parent_signal_id BIGINT UNSIGNED NULL,
    redrive_no INT UNSIGNED NOT NULL DEFAULT 0,
    provider_key VARCHAR(64) NOT NULL,
    signal_key VARCHAR(160) NOT NULL,
    schema_version INT UNSIGNED NOT NULL,
    occurred_at DATETIME(0) NOT NULL,
    received_at DATETIME(0) NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    process_status VARCHAR(16) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL,
    max_attempts INT UNSIGNED NOT NULL,
    next_attempt_at DATETIME(0) NOT NULL,
    lease_owner VARCHAR(64) NULL,
    lease_until DATETIME(0) NULL,
    execution_token CHAR(36) NULL,
    result_code VARCHAR(64) NULL,
    result_summary VARCHAR(1000) NULL,
    processed_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (signal_id),
    CONSTRAINT fk_signal_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_signal_trigger_binding FOREIGN KEY (trigger_binding_id)
        REFERENCES tt_trigger_binding (trigger_binding_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_signal_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_signal_parent FOREIGN KEY (parent_signal_id)
        REFERENCES tt_task_signal (signal_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_signal_source UNIQUE (tenant_id, provider_key, signal_key),
    CONSTRAINT uk_signal_redrive_root UNIQUE (parent_signal_id, redrive_no),
    CONSTRAINT chk_signal_process_status CHECK (
        process_status IN ('READY', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'IGNORED', 'DEAD')
    ),
    CONSTRAINT chk_signal_attempt_bounds CHECK (attempt_count <= max_attempts),
    CONSTRAINT chk_signal_control_generation CHECK (definition_control_generation BETWEEN 1 AND 9007199254740991),
    CONSTRAINT chk_signal_redrive_shape CHECK (
        (parent_signal_id IS NULL AND redrive_no = 0)
        OR (parent_signal_id IS NOT NULL AND redrive_no BETWEEN 1 AND 3)
    ),
    CONSTRAINT chk_signal_lease_triple CHECK (
        (
            lease_owner IS NULL
            AND lease_until IS NULL
            AND execution_token IS NULL
        )
        OR (
            lease_owner IS NOT NULL
            AND lease_until IS NOT NULL
            AND execution_token IS NOT NULL
        )
    ),
    CONSTRAINT chk_signal_ready_retry_wait CHECK (
        process_status NOT IN ('READY', 'RETRY_WAIT')
        OR (
            lease_owner IS NULL
            AND lease_until IS NULL
            AND execution_token IS NULL
            AND processed_at IS NULL
        )
    ),
    CONSTRAINT chk_signal_running CHECK (
        process_status <> 'RUNNING'
        OR (
            lease_owner IS NOT NULL
            AND lease_until IS NOT NULL
            AND execution_token IS NOT NULL
            AND processed_at IS NULL
        )
    ),
    CONSTRAINT chk_signal_terminal CHECK (
        process_status NOT IN ('SUCCEEDED', 'IGNORED', 'DEAD')
        OR (
            processed_at IS NOT NULL
            AND result_code IS NOT NULL
            AND lease_owner IS NULL
            AND lease_until IS NULL
            AND execution_token IS NULL
        )
    ),
    CONSTRAINT chk_signal_calendar_plan CHECK (
        provider_key <> 'calendar'
        OR redrive_no <> 0
        OR (trigger_binding_id IS NOT NULL AND instance_id IS NOT NULL)
    ),
    INDEX ix_signal_claim (process_status, next_attempt_at, signal_id),
    INDEX ix_signal_lease (process_status, lease_until, signal_id),
    INDEX ix_signal_definition (definition_id, created_at, signal_id),
    INDEX ix_signal_live_schema (process_status, provider_key, schema_version, signal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_task_transition (
    transition_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id BIGINT UNSIGNED NOT NULL,
    instance_id BIGINT UNSIGNED NULL,
    instance_scope_id BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(instance_id, 0)) STORED,
    source_type VARCHAR(16) NOT NULL,
    source_key VARCHAR(160) NOT NULL,
    command_key VARCHAR(64) NULL,
    from_control_state VARCHAR(16) NULL,
    to_control_state VARCHAR(16) NULL,
    from_lifecycle VARCHAR(16) NULL,
    to_lifecycle VARCHAR(16) NULL,
    from_scenario_state VARCHAR(64) NULL,
    to_scenario_state VARCHAR(64) NULL,
    from_revision BIGINT UNSIGNED NOT NULL,
    to_revision BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    summary_json JSON NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (transition_id),
    CONSTRAINT fk_transition_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transition_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_transition_revision UNIQUE (definition_id, instance_scope_id, to_revision),
    CONSTRAINT chk_transition_source_type CHECK (source_type IN ('COMMAND', 'SIGNAL', 'SYSTEM')),
    CONSTRAINT chk_transition_command_key CHECK (
        (source_type = 'COMMAND' AND command_key IS NOT NULL)
        OR (source_type IN ('SIGNAL', 'SYSTEM') AND command_key IS NULL)
    ),
    CONSTRAINT chk_transition_revision_step CHECK (to_revision = from_revision + 1),
    CONSTRAINT chk_transition_def_initial CHECK (
        instance_id IS NOT NULL
        OR from_revision <> 0
        OR (
            to_revision = 1
            AND from_control_state IS NULL
            AND to_control_state IS NOT NULL
            AND from_lifecycle IS NULL
            AND to_lifecycle IS NULL
            AND from_scenario_state IS NULL
            AND to_scenario_state IS NULL
        )
    ),
    CONSTRAINT chk_transition_def_subsequent CHECK (
        instance_id IS NOT NULL
        OR from_revision = 0
        OR (
            from_control_state IS NOT NULL
            AND to_control_state IS NOT NULL
            AND from_lifecycle IS NULL
            AND to_lifecycle IS NULL
            AND from_scenario_state IS NULL
            AND to_scenario_state IS NULL
        )
    ),
    CONSTRAINT chk_transition_inst_initial CHECK (
        instance_id IS NULL
        OR from_revision <> 0
        OR (
            to_revision = 1
            AND from_control_state IS NULL
            AND to_control_state IS NULL
            AND from_lifecycle IS NULL
            AND to_lifecycle IS NOT NULL
            AND from_scenario_state IS NULL
            AND to_scenario_state IS NOT NULL
        )
    ),
    CONSTRAINT chk_transition_inst_subsequent CHECK (
        instance_id IS NULL
        OR from_revision = 0
        OR (
            from_control_state IS NULL
            AND to_control_state IS NULL
            AND from_lifecycle IS NOT NULL
            AND to_lifecycle IS NOT NULL
            AND from_scenario_state IS NOT NULL
            AND to_scenario_state IS NOT NULL
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_action_job (
    action_job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    definition_id BIGINT UNSIGNED NOT NULL,
    instance_id BIGINT UNSIGNED NOT NULL,
    transition_id BIGINT UNSIGNED NOT NULL,
    definition_control_generation BIGINT UNSIGNED NOT NULL,
    parent_action_job_id BIGINT UNSIGNED NULL,
    redrive_no INT UNSIGNED NOT NULL DEFAULT 0,
    handler_key VARCHAR(64) NOT NULL,
    action_key VARCHAR(200) NOT NULL,
    execution_mode VARCHAR(24) NOT NULL,
    schema_version INT UNSIGNED NOT NULL,
    target_type VARCHAR(32) NULL,
    target_id VARCHAR(128) NULL,
    payload_json JSON NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    available_at DATETIME(0) NOT NULL,
    expires_at DATETIME(0) NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL,
    max_attempts INT UNSIGNED NOT NULL,
    next_attempt_at DATETIME(0) NOT NULL,
    lease_owner VARCHAR(64) NULL,
    lease_until DATETIME(0) NULL,
    execution_token CHAR(36) NULL,
    outcome_code VARCHAR(64) NULL,
    outcome_summary VARCHAR(1000) NULL,
    completed_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (action_job_id),
    CONSTRAINT fk_action_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_action_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_action_transition FOREIGN KEY (transition_id)
        REFERENCES tt_task_transition (transition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_action_parent FOREIGN KEY (parent_action_job_id)
        REFERENCES tt_action_job (action_job_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_action_key UNIQUE (tenant_id, handler_key, action_key),
    CONSTRAINT uk_action_redrive_root UNIQUE (parent_action_job_id, redrive_no),
    CONSTRAINT chk_action_execution_mode CHECK (execution_mode IN ('LOCAL_TRANSACTIONAL', 'EXTERNAL')),
    CONSTRAINT chk_action_status CHECK (
        status IN (
            'READY',
            'RUNNING',
            'RETRY_WAIT',
            'SUCCEEDED',
            'DEAD',
            'CANCELLED',
            'EXPIRED',
            'UNKNOWN'
        )
    ),
    CONSTRAINT chk_action_attempt_bounds CHECK (attempt_count <= max_attempts),
    CONSTRAINT chk_action_control_generation CHECK (definition_control_generation BETWEEN 1 AND 9007199254740991),
    CONSTRAINT chk_action_expires_at CHECK (expires_at IS NULL OR expires_at >= available_at),
    CONSTRAINT chk_action_target_pair CHECK (
        (target_type IS NULL AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL)
    ),
    CONSTRAINT chk_action_redrive_shape CHECK (
        (parent_action_job_id IS NULL AND redrive_no = 0)
        OR (parent_action_job_id IS NOT NULL AND redrive_no BETWEEN 1 AND 3)
    ),
    CONSTRAINT chk_action_lease_triple CHECK (
        (
            lease_owner IS NULL
            AND lease_until IS NULL
            AND execution_token IS NULL
        )
        OR (
            lease_owner IS NOT NULL
            AND lease_until IS NOT NULL
            AND execution_token IS NOT NULL
        )
    ),
    CONSTRAINT chk_action_ready_retry_wait CHECK (
        status NOT IN ('READY', 'RETRY_WAIT')
        OR (
            lease_owner IS NULL
            AND lease_until IS NULL
            AND execution_token IS NULL
            AND completed_at IS NULL
        )
    ),
    CONSTRAINT chk_action_running CHECK (
        status <> 'RUNNING'
        OR (
            lease_owner IS NOT NULL
            AND lease_until IS NOT NULL
            AND execution_token IS NOT NULL
            AND completed_at IS NULL
        )
    ),
    CONSTRAINT chk_action_terminal CHECK (
        status NOT IN ('SUCCEEDED', 'DEAD', 'CANCELLED', 'EXPIRED', 'UNKNOWN')
        OR (
            completed_at IS NOT NULL
            AND outcome_code IS NOT NULL
            AND lease_owner IS NULL
            AND lease_until IS NULL
            AND execution_token IS NULL
        )
    ),
    INDEX ix_action_claim (status, next_attempt_at, available_at, action_job_id),
    INDEX ix_action_lease (status, lease_until, action_job_id),
    INDEX ix_action_instance (instance_id, created_at, action_job_id),
    INDEX ix_action_live_schema (status, handler_key, schema_version, action_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_action_attempt (
    attempt_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    action_job_id BIGINT UNSIGNED NOT NULL,
    attempt_no INT UNSIGNED NOT NULL,
    execution_token CHAR(36) NOT NULL,
    started_at DATETIME(0) NOT NULL,
    finished_at DATETIME(0) NULL,
    effect_started_at DATETIME(0) NULL,
    outcome VARCHAR(32) NULL,
    error_class VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    provider_reference VARCHAR(200) NULL,
    safe_summary VARCHAR(1000) NULL,
    PRIMARY KEY (attempt_id),
    CONSTRAINT fk_attempt_action_job FOREIGN KEY (action_job_id)
        REFERENCES tt_action_job (action_job_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uk_attempt_no UNIQUE (action_job_id, attempt_no),
    CONSTRAINT uk_attempt_token UNIQUE (action_job_id, execution_token),
    CONSTRAINT chk_attempt_outcome CHECK (
        outcome IS NULL
        OR outcome IN (
            'SUCCEEDED',
            'RETRYABLE_FAILURE',
            'PERMANENT_FAILURE',
            'UNKNOWN',
            'POLICY_BLOCKED'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_command_dedup (
    dedup_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    operation VARCHAR(200) NOT NULL,
    request_id CHAR(36) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    process_status VARCHAR(16) NOT NULL,
    result_code VARCHAR(64) NULL,
    resource_type VARCHAR(32) NULL,
    resource_id VARCHAR(64) NULL,
    resource_revision BIGINT UNSIGNED NULL,
    response_json JSON NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (dedup_id),
    CONSTRAINT uk_command_request UNIQUE (tenant_id, actor_id, operation, request_id),
    CONSTRAINT chk_command_process_status CHECK (process_status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT chk_command_completed_payload CHECK (
        process_status <> 'COMPLETED'
        OR (
            result_code IS NOT NULL
            AND response_json IS NOT NULL
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tt_audit_log (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    definition_id BIGINT UNSIGNED NULL,
    instance_id BIGINT UNSIGNED NULL,
    transition_id BIGINT UNSIGNED NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_definition FOREIGN KEY (definition_id)
        REFERENCES tt_task_definition (definition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_audit_instance FOREIGN KEY (instance_id)
        REFERENCES tt_task_instance (instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_audit_transition FOREIGN KEY (transition_id)
        REFERENCES tt_task_transition (transition_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_audit_resource (tenant_id, resource_type, resource_id, created_at, audit_id),
    INDEX ix_audit_definition (definition_id, created_at, audit_id),
    INDEX ix_audit_trace (tenant_id, trace_id, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

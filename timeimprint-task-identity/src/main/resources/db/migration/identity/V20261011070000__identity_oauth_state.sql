CREATE TABLE tt_identity_oauth_state (
    oauth_state_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state_hash BINARY(32) NOT NULL,
    app_type VARCHAR(32) NOT NULL,
    expires_at DATETIME(0) NOT NULL,
    consumed_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (oauth_state_id),
    UNIQUE KEY uk_identity_oauth_state (state_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

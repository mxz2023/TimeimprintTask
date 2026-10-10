CREATE TABLE tt_identity_user (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    actor_key VARCHAR(128) NOT NULL,
    phone_number VARCHAR(15) NULL,
    username VARCHAR(150) NOT NULL,
    password_hash VARCHAR(128) NULL,
    nickname VARCHAR(30) NOT NULL,
    account_status VARCHAR(16) NOT NULL,
    is_admin TINYINT(1) NOT NULL,
    authorization_verified_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL,
    last_login_at DATETIME(0) NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_identity_user_actor (tenant_id, actor_key),
    UNIQUE KEY uk_identity_user_username (tenant_id, username),
    UNIQUE KEY uk_identity_user_phone (tenant_id, phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tt_identity_verification_code (
    verification_code_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone_number VARCHAR(15) NOT NULL,
    code VARCHAR(6) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (verification_code_id),
    KEY ix_identity_code_phone (phone_number, created_at, verification_code_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tt_identity_session (
    session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash BINARY(32) NOT NULL,
    created_at DATETIME(0) NOT NULL,
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_identity_session_token (token_hash),
    CONSTRAINT fk_identity_session_user FOREIGN KEY (user_id) REFERENCES tt_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tt_identity_social_account (
    social_account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(32) NOT NULL,
    app_type VARCHAR(32) NOT NULL,
    openid VARCHAR(128) NOT NULL,
    unionid VARCHAR(128) NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(500) NOT NULL,
    raw_json JSON NOT NULL,
    created_at DATETIME(0) NOT NULL,
    updated_at DATETIME(0) NOT NULL,
    PRIMARY KEY (social_account_id),
    UNIQUE KEY uk_identity_social (provider, app_type, openid),
    KEY ix_identity_social_union (provider, unionid),
    CONSTRAINT fk_identity_social_user FOREIGN KEY (user_id) REFERENCES tt_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

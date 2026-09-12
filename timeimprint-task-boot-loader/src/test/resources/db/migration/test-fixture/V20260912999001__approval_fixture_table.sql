-- T07 test-only table for ApprovalFixture materializer.
-- This migration is applied ONLY in IT test contexts that include the test-fixture Flyway location.
-- It must NOT be placed under the platform or capability Flyway locations.
CREATE TABLE IF NOT EXISTS tt_test_approval_data (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    definition_id  BIGINT UNSIGNED NOT NULL,
    transition_id  BIGINT UNSIGNED NOT NULL,
    approval_meta_json JSON NOT NULL,
    created_at     DATETIME(0) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_approval_definition (definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

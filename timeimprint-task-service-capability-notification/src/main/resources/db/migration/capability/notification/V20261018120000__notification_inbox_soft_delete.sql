-- P13: soft-delete visibility for tt_inbox. Rows stay; notification and action tables are unchanged.

ALTER TABLE tt_inbox
    ADD COLUMN deleted_at DATETIME(0) NULL AFTER read_at,
    ADD INDEX ix_inbox_visible (tenant_id, recipient_type, recipient_id, deleted_at, created_at, inbox_id);

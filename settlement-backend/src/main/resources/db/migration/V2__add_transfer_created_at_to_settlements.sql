ALTER TABLE settlements
    ADD COLUMN transfer_created_at datetime(6) NULL;

UPDATE settlements
SET transfer_created_at = created_at
WHERE transfer_created_at IS NULL;

ALTER TABLE settlements
    MODIFY COLUMN transfer_created_at datetime(6) NOT NULL;

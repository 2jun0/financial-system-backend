CREATE TABLE transfers
(
    id                       BIGINT AUTO_INCREMENT NOT NULL,
    created_at               datetime              NOT NULL,
    transfer_key             VARCHAR(255)          NOT NULL,
    source_account_id        BIGINT                NOT NULL,
    recipient_bank_code      VARCHAR(255)          NOT NULL,
    recipient_account_number VARCHAR(255)          NOT NULL,
    amount                   BIGINT                NOT NULL,
    state                    VARCHAR(255)          NOT NULL,
    updated_at               datetime              NOT NULL,
    CONSTRAINT pk_transfers PRIMARY KEY (id)
);

ALTER TABLE transfers
    ADD CONSTRAINT uc_transfers_transfer_key UNIQUE (transfer_key);

CREATE INDEX idx_transfers_source_account_id ON transfers (source_account_id);

ALTER TABLE transfers
    ADD CONSTRAINT fk_transfers_source_account_id
        FOREIGN KEY (source_account_id) REFERENCES accounts (id);

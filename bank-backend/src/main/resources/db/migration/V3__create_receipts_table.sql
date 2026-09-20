CREATE TABLE receipts
(
    id                       BIGINT AUTO_INCREMENT NOT NULL,
    created_at               datetime              NOT NULL,
    transfer_key             VARCHAR(255)          NOT NULL,
    recipient_account_id     BIGINT                NOT NULL,
    source_bank_code         VARCHAR(255)          NOT NULL,
    source_account_number    VARCHAR(255)          NOT NULL,
    amount                   BIGINT                NOT NULL,
    state                    VARCHAR(255)          NOT NULL,
    updated_at               datetime              NOT NULL,
    CONSTRAINT pk_receipts PRIMARY KEY (id)
);

ALTER TABLE receipts
    ADD CONSTRAINT uc_receipts_transfer_key UNIQUE (transfer_key);

CREATE INDEX idx_receipts_recipient_account_id ON receipts (recipient_account_id);

ALTER TABLE receipts
    ADD CONSTRAINT fk_receipts_recipient_account_id
        FOREIGN KEY (recipient_account_id) REFERENCES accounts (id);

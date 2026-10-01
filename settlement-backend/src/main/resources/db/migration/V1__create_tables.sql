CREATE TABLE banks
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    created_at datetime              NOT NULL,
    bank_code  VARCHAR(255)          NOT NULL,
    base_uri   VARCHAR(255)          NOT NULL,
    balance    BIGINT                NOT NULL,
    updated_at datetime              NOT NULL,
    CONSTRAINT pk_banks PRIMARY KEY (id)
);

CREATE TABLE settlements
(
    id                       BIGINT AUTO_INCREMENT NOT NULL,
    created_at               datetime              NOT NULL,
    transfer_key             VARCHAR(255)          NOT NULL,
    source_bank_id           BIGINT                NOT NULL,
    source_account_number    VARCHAR(255)          NOT NULL,
    recipient_bank_id        BIGINT                NOT NULL,
    recipient_account_number VARCHAR(255)          NOT NULL,
    amount                   BIGINT                NOT NULL,
    state                    VARCHAR(255)          NOT NULL,
    updated_at               datetime              NOT NULL,
    CONSTRAINT pk_settlements PRIMARY KEY (id)
);

ALTER TABLE banks
    ADD CONSTRAINT uc_banks_bank_code UNIQUE (bank_code);

ALTER TABLE settlements
    ADD CONSTRAINT uc_settlements_transfer_key UNIQUE (transfer_key);

CREATE INDEX idx_settlements_recipient_bank_id ON settlements (recipient_bank_id);

CREATE INDEX idx_settlements_source_bank_id ON settlements (source_bank_id);

ALTER TABLE settlements
    ADD CONSTRAINT fk_settlements_source_bank_id
        FOREIGN KEY (source_bank_id) REFERENCES banks (id);

ALTER TABLE settlements
    ADD CONSTRAINT fk_settlements_recipient_bank_id
        FOREIGN KEY (recipient_bank_id) REFERENCES banks (id);

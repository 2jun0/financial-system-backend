CREATE TABLE account_transactions
(
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    created_at           datetime     NOT NULL,
    account_id           BIGINT       NOT NULL,
    transfer_key         VARCHAR(255) NOT NULL,
    other_bank_code      VARCHAR(255) NOT NULL,
    other_account_number VARCHAR(255) NOT NULL,
    amount               BIGINT       NOT NULL,
    type                 VARCHAR(255) NOT NULL,
    CONSTRAINT pk_account_transactions PRIMARY KEY (id)
);

CREATE TABLE accounts
(
    id             BIGINT AUTO_INCREMENT NOT NULL,
    created_at     datetime     NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    customer_id    BIGINT       NOT NULL,
    name           VARCHAR(255) NOT NULL,
    balance        BIGINT       NOT NULL,
    updated_at     datetime     NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id)
);

CREATE TABLE customers
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    created_at    datetime     NOT NULL,
    login_id      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    updated_at    datetime     NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id)
);

ALTER TABLE accounts
    ADD CONSTRAINT uc_accounts_account_number UNIQUE (account_number);

ALTER TABLE customers
    ADD CONSTRAINT uc_customers_login UNIQUE (login_id);

ALTER TABLE account_transactions
    ADD CONSTRAINT uk_account_transactions_transfer_key_type UNIQUE (transfer_key, type);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);

CREATE INDEX idx_account_transactions_account_id ON account_transactions (account_id);

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE account_transactions
    ADD CONSTRAINT fk_account_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts (id);

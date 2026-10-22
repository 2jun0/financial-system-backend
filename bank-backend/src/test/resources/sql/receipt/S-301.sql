INSERT INTO customers (id, login_id, password_hash, created_at, updated_at)
VALUES (1, 'customer', 'password-hash', UTC_TIMESTAMP(), UTC_TIMESTAMP());

INSERT INTO accounts (id, account_number, customer_id, name, balance, created_at, updated_at)
VALUES (1, 'B-001', 1, '수취 계좌', 100000, UTC_TIMESTAMP(), UTC_TIMESTAMP());

INSERT INTO receipts (id, transfer_key, recipient_account_id, source_bank_code, source_account_number, amount, state, transfer_created_at, created_at, updated_at)
VALUES (1, 'E-301', 1, 'BANK_A', 'A-001', 10000, 'RECEIVING', UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP());

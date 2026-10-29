INSERT INTO customers (id, login_id, password_hash, created_at, updated_at)
VALUES (1, 'customer', 'password-hash', UTC_TIMESTAMP(), UTC_TIMESTAMP());

INSERT INTO accounts (id, account_number, customer_id, name, balance, created_at, updated_at)
VALUES (1, 'A-001', 1, '송금 계좌', 90000, UTC_TIMESTAMP(), UTC_TIMESTAMP());

INSERT INTO transfers (id, transfer_key, source_account_id, recipient_bank_code, recipient_account_number, amount, state, created_at, updated_at)
VALUES (1, 'E-RECOVERY', 1, 'BANK_B', 'B-001', 10000, 'TRANSFERRING', UTC_TIMESTAMP() - INTERVAL 10 MINUTE, UTC_TIMESTAMP());

INSERT INTO account_transactions (account_id, transfer_key, other_bank_code, other_account_number, amount, type, created_at)
VALUES (1, 'E-RECOVERY', 'BANK_B', 'B-001', -10000, 'DEBIT', UTC_TIMESTAMP());

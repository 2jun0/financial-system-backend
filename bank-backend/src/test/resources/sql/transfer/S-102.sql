INSERT INTO customers (id, login_id, password_hash, created_at, updated_at)
VALUES (1, 'customer', 'password-hash', UTC_TIMESTAMP(), UTC_TIMESTAMP());

INSERT INTO accounts (id, account_number, customer_id, name, balance, created_at, updated_at)
VALUES (1, 'A-001', 1, '송금 계좌', 100000, UTC_TIMESTAMP(), UTC_TIMESTAMP());

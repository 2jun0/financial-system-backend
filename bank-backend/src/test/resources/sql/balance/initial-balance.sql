INSERT INTO customers (id, login_id, password_hash, created_at, updated_at)
VALUES (1, 'balance-check-customer', 'unused', UTC_TIMESTAMP(), UTC_TIMESTAMP());

INSERT INTO accounts (id, account_number, customer_id, name, balance, created_at, updated_at)
VALUES (1, 'CHECK-001', 1, '잔액 제약 검증', 100, UTC_TIMESTAMP(), UTC_TIMESTAMP());

-- 테스트 고객 비밀번호: 1234 (BCrypt)
INSERT INTO customers (id, login_id, password_hash, created_at, updated_at)
VALUES (1, 'customer-b', '$2a$10$IrQXkA6fFUaJLYi962kIYutrIiAdCkyZx28jA3WLAhXmAuxYEgUAi', UTC_TIMESTAMP(),
        UTC_TIMESTAMP());

INSERT INTO accounts (id, account_number, customer_id, name, balance, created_at, updated_at)
VALUES (1, 'B-001', 1, '수취 계좌', 100000, UTC_TIMESTAMP(), UTC_TIMESTAMP());

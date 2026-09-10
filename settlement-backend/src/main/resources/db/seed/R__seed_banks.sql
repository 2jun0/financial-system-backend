INSERT INTO banks (bank_code, balance, created_at, updated_at)
VALUES ('BANK_A', 1000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('BANK_B', 1000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE balance    = 1000000,
                        updated_at = CURRENT_TIMESTAMP;

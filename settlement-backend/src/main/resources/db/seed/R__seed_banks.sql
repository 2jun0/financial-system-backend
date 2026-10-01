INSERT INTO banks (bank_code, base_uri, balance, created_at, updated_at)
VALUES ('BANK_A', 'http://localhost:8081', 1000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('BANK_B', 'http://localhost:8082', 1000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) AS new
ON DUPLICATE KEY UPDATE base_uri   = new.base_uri,
                        balance    = new.balance,
                        updated_at = CURRENT_TIMESTAMP;

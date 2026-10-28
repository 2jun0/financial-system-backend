INSERT INTO banks (id, bank_code, base_uri, balance, created_at, updated_at)
VALUES (1, 'BANK_A', 'http://source-bank', 100000, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
       (2, 'BANK_B', 'http://recipient-bank', 1000000, UTC_TIMESTAMP(), UTC_TIMESTAMP());

ALTER TABLE banks
    ADD CONSTRAINT ck_banks_balance_nonnegative CHECK (balance >= 0);

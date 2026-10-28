ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_balance_nonnegative CHECK (balance >= 0);

-- A pre-built base so the routines have real work to do on first start.

INSERT INTO accounts (holder_name, balance)
SELECT 'Account Holder ' || LPAD(s::TEXT, 4, '0'), ROUND((RANDOM() * 10000)::NUMERIC, 2)
FROM generate_series(1, 200) AS s;

-- 20k pending transactions: enough that chunked processing is visibly different from
-- loading everything at once.
INSERT INTO transactions (account_id, amount, type, occurred_at, reconciled_at)
SELECT
    1 + (s % 200),
    ROUND((10 + (s % 5000) / 10.0)::NUMERIC, 2),
    CASE WHEN s % 3 = 0 THEN 'DEBIT' ELSE 'CREDIT' END,
    NOW() - ((s % 90) * INTERVAL '1 day'),
    NULL
FROM generate_series(1, 20000) AS s;

-- A slice of them already reconciled, so the pending count is not simply "all rows".
UPDATE transactions SET reconciled_at = NOW() WHERE id % 5 = 0;

INSERT INTO invoices (account_id, due_date, amount, status, interest_applied_on)
SELECT
    1 + (s % 200),
    CURRENT_DATE - (s % 120),
    ROUND((100 + (s % 9000) / 10.0)::NUMERIC, 2),
    CASE WHEN s % 7 = 0 THEN 'PAID' ELSE 'OPEN' END,
    NULL
FROM generate_series(1, 1000) AS s;

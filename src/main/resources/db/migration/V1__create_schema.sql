CREATE TABLE accounts (
    id          BIGSERIAL     PRIMARY KEY,
    holder_name VARCHAR(160)  NOT NULL,
    balance     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id            BIGSERIAL      PRIMARY KEY,
    account_id    BIGINT         NOT NULL REFERENCES accounts (id),
    amount        NUMERIC(14, 2) NOT NULL,
    type          VARCHAR(10)    NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    occurred_at   TIMESTAMPTZ    NOT NULL,
    -- NULL means "not reconciled yet". This single column is what makes the
    -- reconciliation routine idempotent: a second run finds nothing left to do.
    reconciled_at TIMESTAMPTZ
);

CREATE TABLE invoices (
    id                  BIGSERIAL      PRIMARY KEY,
    account_id          BIGINT         NOT NULL REFERENCES accounts (id),
    due_date            DATE           NOT NULL,
    amount              NUMERIC(14, 2) NOT NULL,
    interest_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(10)    NOT NULL CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    -- Guards against charging interest twice on the same day if the routine runs
    -- again after a failure or a manual trigger.
    interest_applied_on DATE
);

-- Partial index: the reconciliation query only ever looks at pending rows, and once
-- the backlog is cleared they are a tiny fraction of the table.
CREATE INDEX idx_transactions_pending ON transactions (id) WHERE reconciled_at IS NULL;
CREATE INDEX idx_invoices_due ON invoices (due_date, status);

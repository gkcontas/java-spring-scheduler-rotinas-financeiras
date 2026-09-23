-- Cron expressions live in the database, not in @Scheduled annotations, so the
-- schedule can change without a redeploy.
CREATE TABLE scheduled_job_config (
    id              BIGSERIAL    PRIMARY KEY,
    job_name        VARCHAR(80)  NOT NULL UNIQUE,
    cron_expression VARCHAR(120) NOT NULL,
    description     VARCHAR(255),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE job_execution_log (
    id              BIGSERIAL   PRIMARY KEY,
    job_name        VARCHAR(80) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    processed_count INTEGER     NOT NULL DEFAULT 0,
    error_message   VARCHAR(2000),
    triggered_by    VARCHAR(20) NOT NULL CHECK (triggered_by IN ('SCHEDULE', 'MANUAL'))
);

CREATE INDEX idx_job_execution_log_job ON job_execution_log (job_name, started_at DESC);

-- ShedLock's own table, exactly as the library expects it. Holding the lock is a row
-- in this table, which is why the lock works across replicas: they all see the same row.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

INSERT INTO scheduled_job_config (job_name, cron_expression, description, enabled) VALUES
    ('reconciliation', '0 */5 * * * *', 'Reconciles pending transactions in chunks', TRUE),
    ('interest-accrual', '0 0 3 * * *', 'Applies daily interest to overdue open invoices', TRUE);

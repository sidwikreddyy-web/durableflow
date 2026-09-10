CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_sessions(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id);
CREATE INDEX idx_refresh_sessions_expiry ON refresh_sessions(expires_at) WHERE revoked_at IS NULL;

CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    name VARCHAR(150) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    status VARCHAR(30) NOT NULL CHECK (
        status IN ('RUNNING', 'COMPENSATING', 'COMPENSATED', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    current_step VARCHAR(40) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workflows_owner_created ON workflow_instances(owner_id, created_at DESC);
CREATE INDEX idx_workflows_status ON workflow_instances(status);

CREATE TABLE workflow_events (
    id BIGSERIAL PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workflow_events_workflow ON workflow_events(workflow_id, id);

CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    task_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    attempt INTEGER NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts > 0),
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(200),
    lease_expires_at TIMESTAMPTZ,
    idempotency_key VARCHAR(250) NOT NULL UNIQUE,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workflow_tasks_claim
    ON workflow_tasks(available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_workflow_tasks_expired_lease
    ON workflow_tasks(lease_expires_at)
    WHERE status = 'RUNNING';

-- =============================================================================
-- buddy-chat-agent  ·  Database initialisation script
-- =============================================================================
-- This script is idempotent: every statement uses IF NOT EXISTS / ON CONFLICT
-- so it can be re-run safely against an already-initialised database.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Core tables
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS agent_session (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(128)  UNIQUE NOT NULL,
    client_id   VARCHAR(128)  NOT NULL,
    user_id     VARCHAR(128)  NOT NULL,
    channel     VARCHAR(32)   NOT NULL,
    state       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    metadata    TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversation_turn (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(128)  NOT NULL,
    role        VARCHAR(32)   NOT NULL,
    content     TEXT          NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_memory (
    id          BIGSERIAL PRIMARY KEY,
    client_id   VARCHAR(128)  NOT NULL,
    user_id     VARCHAR(128)  NOT NULL,
    category    VARCHAR(128),
    memory_key  VARCHAR(255)  NOT NULL,
    memory_val  TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS service (
    id          BIGSERIAL PRIMARY KEY,
    client_id   VARCHAR(128)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    base_url    VARCHAR(1024),
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS database_connection (
    id           BIGSERIAL PRIMARY KEY,
    client_id    VARCHAR(128)  NOT NULL,
    name         VARCHAR(255)  NOT NULL,
    db_type      VARCHAR(64)   NOT NULL,
    jdbc_url     VARCHAR(1024) NOT NULL,
    db_username  VARCHAR(255),
    db_password  VARCHAR(500),
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agent_chat_log (
    id             BIGSERIAL PRIMARY KEY,
    session_id     VARCHAR(128),
    client_id      VARCHAR(128)  NOT NULL,
    user_id        VARCHAR(128),
    channel        VARCHAR(32),
    user_message   TEXT,
    agent_response TEXT,
    agent_used     VARCHAR(128),
    tools_called   VARCHAR(1024),
    duration_ms    BIGINT,
    status         VARCHAR(16)   NOT NULL,
    error_message  VARCHAR(2048),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- Client auth table
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS client_auth (
    id          BIGSERIAL PRIMARY KEY,
    client_id   VARCHAR(255)  UNIQUE NOT NULL,
    secret_key  VARCHAR(500)  NOT NULL,
    client_name VARCHAR(255),
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Seed a default development client (no-op if already present)
INSERT INTO client_auth (client_id, secret_key, client_name, enabled)
VALUES ('default', 'buddyai-secret-123', 'Default Client', true)
ON CONFLICT (client_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------

-- agent_session
CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_session_session_id
    ON agent_session (session_id);

CREATE INDEX IF NOT EXISTS idx_agent_session_client_user
    ON agent_session (client_id, user_id);

CREATE INDEX IF NOT EXISTS idx_agent_session_state
    ON agent_session (state);

CREATE INDEX IF NOT EXISTS idx_agent_session_client
    ON agent_session (client_id);

-- agent_chat_log
CREATE INDEX IF NOT EXISTS idx_chat_log_client_id
    ON agent_chat_log (client_id);

CREATE INDEX IF NOT EXISTS idx_chat_log_session_id
    ON agent_chat_log (session_id);

CREATE INDEX IF NOT EXISTS idx_chat_log_created_at
    ON agent_chat_log (created_at);

CREATE INDEX IF NOT EXISTS idx_chat_log_status
    ON agent_chat_log (status);

CREATE INDEX IF NOT EXISTS idx_chat_log_created
    ON agent_chat_log (created_at DESC);

-- user_memory
CREATE INDEX IF NOT EXISTS idx_user_memory_client_user
    ON user_memory (client_id, user_id);

-- conversation_turn
CREATE INDEX IF NOT EXISTS idx_conversation_turn_session
    ON conversation_turn (session_id);

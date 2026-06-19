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

-- =============================================================================
-- Pipeline tables: intent/slot/state machine
-- =============================================================================

CREATE TABLE IF NOT EXISTS service_intent (
    id          BIGSERIAL PRIMARY KEY,
    client_id   VARCHAR(255) NOT NULL,
    service_id  BIGINT,
    intent_slug VARCHAR(255) NOT NULL,
    intent_type VARCHAR(100),
    intent_response TEXT,
    summary_prompt  TEXT,
    UNIQUE(client_id, intent_slug)
);

CREATE TABLE IF NOT EXISTS service_intent_question (
    service_intent_id BIGINT REFERENCES service_intent(id) ON DELETE CASCADE,
    question TEXT
);

CREATE TABLE IF NOT EXISTS integrated_service (
    id                   BIGSERIAL PRIMARY KEY,
    client_id            VARCHAR(255),
    name                 VARCHAR(255),
    endpoint             TEXT,
    method               VARCHAR(20)  DEFAULT 'POST',
    headers              TEXT,
    system_prompt        TEXT,
    slot_identify_prompt TEXT,
    summary_prompt       TEXT,
    bot_response_template TEXT,
    enabled              BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS service_parameter (
    id                  BIGSERIAL PRIMARY KEY,
    service_id          BIGINT REFERENCES integrated_service(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    param_type          VARCHAR(100) DEFAULT 'string',
    required            BOOLEAN DEFAULT TRUE,
    question_to_get_input TEXT,
    default_value       TEXT,
    is_auth_parameter   BOOLEAN DEFAULT FALSE,
    date_format         VARCHAR(100),
    sequence            INTEGER DEFAULT 0,
    allowed_values      TEXT,
    hint                TEXT,
    dependent_service_id BIGINT,
    mapping_rules       TEXT,
    enable_llm_fallback BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS slot_item (
    id               BIGSERIAL PRIMARY KEY,
    request_id       VARCHAR(255),
    name             VARCHAR(255),
    param_type       VARCHAR(100),
    question         TEXT,
    slot_value       TEXT,
    required         BOOLEAN,
    is_auth_parameter BOOLEAN DEFAULT FALSE,
    default_value    TEXT,
    date_format      VARCHAR(100),
    is_current_slot  BOOLEAN DEFAULT FALSE,
    sequence         INTEGER DEFAULT 0,
    allowed_values   TEXT,
    hint             TEXT
);

CREATE TABLE IF NOT EXISTS conversation_state (
    request_id    VARCHAR(255) PRIMARY KEY,
    session_id    VARCHAR(255),
    client_id     VARCHAR(255),
    state         VARCHAR(50),
    state_sub_type VARCHAR(50),
    input_text    TEXT,
    intent_slug   VARCHAR(255),
    service_id    BIGINT,
    intent_response TEXT,
    summary_prompt  TEXT,
    slots_json    TEXT,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP,
    completed     BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS slot_rule (
    id                   BIGSERIAL PRIMARY KEY,
    service_parameter_id BIGINT REFERENCES service_parameter(id) ON DELETE CASCADE,
    condition_expression TEXT,
    set_expression       TEXT,
    target_field         VARCHAR(255),
    priority             INTEGER DEFAULT 0,
    active               BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_conversation_state_session ON conversation_state(session_id, completed);
CREATE INDEX IF NOT EXISTS idx_slot_item_request ON slot_item(request_id);
CREATE INDEX IF NOT EXISTS idx_service_intent_client ON service_intent(client_id);

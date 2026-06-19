CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Agent sessions
CREATE TABLE IF NOT EXISTS agent_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) UNIQUE NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    channel VARCHAR(50) DEFAULT 'REST',
    state VARCHAR(50) DEFAULT 'ACTIVE',
    metadata TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Conversation turns (episodic memory)
CREATE TABLE IF NOT EXISTS conversation_turn (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(255),
    sequence_num INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conv_turn_session ON conversation_turn(session_id);

-- User memory (semantic memory with vector embeddings)
CREATE TABLE IF NOT EXISTS user_memory (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    fact TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'CONTEXT',
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT NOW(),
    last_accessed_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_memory_user ON user_memory(user_id, client_id);

-- API services registry
CREATE TABLE IF NOT EXISTS service (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    method VARCHAR(10) DEFAULT 'GET',
    description TEXT,
    request_schema TEXT,
    response_schema TEXT,
    client_id VARCHAR(255),
    auth_type VARCHAR(50) DEFAULT 'NONE',
    api_key VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Database connections
CREATE TABLE IF NOT EXISTS database_connection (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    host VARCHAR(500) NOT NULL,
    port INTEGER DEFAULT 5432,
    db_name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_encrypted VARCHAR(1000) NOT NULL,
    db_type VARCHAR(50) DEFAULT 'POSTGRESQL',
    schema_name VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Chat audit log
CREATE TABLE IF NOT EXISTS agent_chat_log (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255),
    client_id VARCHAR(255),
    user_id VARCHAR(255),
    channel VARCHAR(50),
    user_message TEXT,
    agent_response TEXT,
    agent_used VARCHAR(255),
    tools_called TEXT,
    duration_ms BIGINT,
    status VARCHAR(50),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_log_session ON agent_chat_log(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_log_client ON agent_chat_log(client_id);

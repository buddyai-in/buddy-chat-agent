# buddy-chat-agent

A production-ready, multi-module AI Agentic Chat Platform built on Spring Boot 3.5 and Spring AI 1.0.0. It routes user conversations through a central orchestrator that delegates to specialised MCP (Model Context Protocol) servers — each responsible for a distinct capability domain.

---

## Architecture

```
                         +---------------------------+
                         |        User / Client       |
                         |  (REST / WebSocket / Bot)  |
                         +------------+--------------+
                                      |
                                      v
                         +---------------------------+
                         |     buddy-chat-agent       |
                         |      Core Orchestrator     |
                         |   Spring Boot 3.5 :8080    |
                         |   Spring AI 1.0.0          |
                         |   Claude / GPT-4o          |
                         +---+---+---+---+---+--------+
                             |   |   |   |   |
              +--------------+   |   |   |   +--------------+
              |                  |   |   |                  |
              v                  v   |   v                  v
  +------------------+  +--------+  |  +----------+  +---------------------+
  | api-mcp-server   |  |document|  |  | database |  | notification-mcp    |
  | :8081            |  | :8082  |  |  | :8083    |  | :8086               |
  | - call HTTP APIs |  | - RAG  |  |  | - SQL    |  | - Email             |
  | - auth handling  |  | - embed|  |  | - schema |  | - Telegram          |
  +------------------+  +--------+  |  +----------+  | - SMS (Twilio)      |
                                    |                 | - WhatsApp          |
                                    v                 +---------------------+
                           +----------------+
                           | memory-mcp     |
                           | :8085          |
                           | - user facts   |
                           | - vector store |
                           +----------------+

  Shared Infrastructure:
  +---------------------+     +---------------------+
  | PostgreSQL + pgvector|     |       Redis          |
  |       :5432          |     |       :6379          |
  +---------------------+     +---------------------+
```

---

## Quick Start

### Prerequisites

- Docker 24+ and Docker Compose v2
- `ANTHROPIC_API_KEY` (required)
- `OPENAI_API_KEY` (required for document and memory embeddings)

### 1. Clone and configure

```bash
git clone https://github.com/your-org/buddy-chat-agent.git
cd buddy-chat-agent
cp .env.example .env
# Edit .env and fill in your API keys
```

### 2. Start all services

```bash
docker compose up --build
```

The core agent will be available at `http://localhost:8080` once all health checks pass (typically ~60 seconds on first run while images build).

### 3. Verify health

```bash
curl http://localhost:8080/manage/health
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

### 4. Run only infrastructure (for local development)

```bash
docker compose up postgres redis
# Then run the core module from your IDE or:
./mvnw spring-boot:run -pl core
```

---

## API Usage

### Send a chat message

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: my-app" \
  -d '{
    "sessionId": "session-abc-123",
    "userId": "user-42",
    "message": "What is the current balance for account 1001?"
  }'
```

Response:

```json
{
  "sessionId": "session-abc-123",
  "response": "The current balance for account 1001 is $4,320.50 as of today.",
  "agentUsed": "database-agent",
  "toolsCalled": ["query_database"],
  "durationMs": 812
}
```

### Start a new session

```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: my-app" \
  -d '{
    "userId": "user-42",
    "channel": "REST"
  }'
```

### Retrieve conversation history

```bash
curl http://localhost:8080/api/v1/sessions/session-abc-123/history \
  -H "X-Client-Id: my-app"
```

### Stream a response (SSE)

```bash
curl -N http://localhost:8080/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: my-app" \
  -d '{
    "sessionId": "session-abc-123",
    "userId": "user-42",
    "message": "Summarise the latest quarterly report."
  }'
```

---

## MCP Servers

| Server | Port | Key Tools Exposed |
|---|---|---|
| `api-mcp-server` | 8081 | `call_api`, `register_service`, `list_services` |
| `document-mcp-server` | 8082 | `search_documents`, `ingest_document`, `delete_document` |
| `database-mcp-server` | 8083 | `query_database`, `describe_schema`, `list_connections` |
| `memory-mcp-server` | 8085 | `remember_fact`, `recall_facts`, `forget_fact` |
| `notification-mcp-server` | 8086 | `send_email`, `send_telegram`, `send_sms`, `send_whatsapp` |

Each MCP server exposes its tools over HTTP using the Spring AI MCP transport protocol. The core orchestrator discovers and invokes them automatically at startup.

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | _(required)_ | Anthropic API key for Claude models |
| `OPENAI_API_KEY` | _(required for embeddings)_ | OpenAI API key for embeddings and GPT-4o fallback |
| `DB_URL` | `jdbc:postgresql://localhost:5432/buddyai` | PostgreSQL JDBC URL |
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `JPA_DDL` | `update` | Hibernate DDL mode (`update`, `validate`, `none`) |
| `SERVER_PORT` | `8080` | Core agent HTTP port |
| `LOG_LEVEL` | `INFO` | Log level for `com.buddyai` packages |
| `API_MCP_URL` | `http://localhost:8081` | URL for api-mcp-server |
| `DOCUMENT_MCP_URL` | `http://localhost:8082` | URL for document-mcp-server |
| `DATABASE_MCP_URL` | `http://localhost:8083` | URL for database-mcp-server |
| `MEMORY_MCP_URL` | `http://localhost:8085` | URL for memory-mcp-server |
| `NOTIFICATION_MCP_URL` | `http://localhost:8086` | URL for notification-mcp-server |
| `TELEGRAM_BOT_TOKEN` | _(optional)_ | Telegram bot token for notifications |
| `TWILIO_ACCOUNT_SID` | _(optional)_ | Twilio account SID for SMS/WhatsApp |
| `TWILIO_AUTH_TOKEN` | _(optional)_ | Twilio auth token |
| `TWILIO_WHATSAPP_FROM` | `whatsapp:+14155238886` | Twilio WhatsApp sender number |
| `SMTP_HOST` | `smtp.gmail.com` | SMTP host for email notifications |
| `SMTP_USER` | _(optional)_ | SMTP username |
| `SMTP_PASS` | _(optional)_ | SMTP password |

---

## Module Structure

```
buddy-chat-agent/
├── core/                          # Central orchestrator (Spring Boot :8080)
│   └── src/main/
│       ├── java/com/buddyai/
│       │   ├── agent/             # Agent beans and orchestration logic
│       │   ├── controller/        # REST controllers
│       │   ├── model/             # JPA entities
│       │   ├── repository/        # Spring Data repositories
│       │   └── service/           # Business services
│       └── resources/
│           └── application.yml
├── mcp-servers/
│   ├── api-mcp-server/            # HTTP API caller (:8081)
│   ├── document-mcp-server/       # RAG / document search (:8082)
│   ├── database-mcp-server/       # SQL query executor (:8083)
│   ├── memory-mcp-server/         # User memory / vector store (:8085)
│   └── notification-mcp-server/   # Multi-channel notifications (:8086)
├── docker-compose.yml
├── init-db.sql                    # Schema bootstrap (pgvector + tables)
├── Dockerfile                     # Core module Docker build
├── .env.example
└── README.md
```

---

## Migration Guide from chat-assistant

If you are migrating from the `chat-assistant` project in `/home/user/chat-assistant`:

### 1. Database schema

Run `init-db.sql` against your existing database. It uses `CREATE TABLE IF NOT EXISTS` so it is safe to apply on a database with existing data. The `agent_session` and `conversation_turn` tables supersede the old `chat_session` and `message` tables — migrate rows with:

```sql
-- Migrate sessions
INSERT INTO agent_session (session_id, client_id, user_id, channel, state, created_at)
SELECT id::text, 'default', user_id, 'REST', 'CLOSED', created_at
FROM chat_session
ON CONFLICT (session_id) DO NOTHING;

-- Migrate messages
INSERT INTO conversation_turn (session_id, role, content, sequence_num, created_at)
SELECT session_id::text, role, content, ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY created_at), created_at
FROM message;
```

### 2. Configuration

Copy your existing `ANTHROPIC_API_KEY` and `OPENAI_API_KEY` values into `.env`. The new `application.yml` consolidates all previous per-module config files.

### 3. API endpoints

| Old endpoint | New endpoint |
|---|---|
| `POST /chat` | `POST /api/v1/chat` |
| `GET /chat/history/{id}` | `GET /api/v1/sessions/{sessionId}/history` |
| `POST /chat/session` | `POST /api/v1/sessions` |

### 4. Model change

The default model has changed from `claude-3-5-sonnet-20241022` to `claude-sonnet-4-6`. Override with the `ANTHROPIC_API_KEY` env var or update `application.yml` if you need to pin to a specific version.

---

## Observability

Prometheus metrics are exposed at `http://localhost:8080/manage/prometheus`. A minimal Grafana dashboard JSON is available in `docs/grafana-dashboard.json` (import via Grafana UI).

Key metrics to monitor:

- `http_server_requests_seconds` — API latency by endpoint
- `spring_ai_chat_completions_tokens_total` — token usage per model
- `hikaricp_connections_active` — database connection pool utilisation
- `jvm_memory_used_bytes` — heap and non-heap memory

---

## License

Apache 2.0

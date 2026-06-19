package com.buddyai.mcp.database.tools;

import com.buddyai.mcp.database.entity.DatabaseConnection;
import com.buddyai.mcp.database.repository.DatabaseConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

@Component
public class DatabaseTools {

    private final DatabaseConnectionRepository connectionRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public DatabaseTools(DatabaseConnectionRepository connectionRepository,
                         ChatClient.Builder chatClientBuilder,
                         ObjectMapper objectMapper) {
        this.connectionRepository = connectionRepository;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Execute a natural language query against a registered database connection. The tool translates to SQL and returns results.")
    public String queryDatabase(
            @ToolParam(description = "Natural language question about the data") String naturalLanguageQuery,
            @ToolParam(description = "Database connection ID (from listDatabaseConnections)") String connectionId,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            DatabaseConnection conn = resolveConnection(connectionId, clientId);
            if (conn == null) {
                return errorJson("Database connection not found: " + connectionId);
            }

            // Get schema to help with SQL generation
            String schemaJson = fetchSchemaJson(conn);

            // Translate NL to SQL using the AI chat model
            String systemPrompt = """
                    You are a SQL expert. Given the database schema and a natural language question,
                    generate a safe, read-only SQL SELECT query. Output only the SQL query, no explanation,
                    no markdown, no code fences. The database type is: %s.
                    Schema: %s
                    """.formatted(conn.getDbType(), schemaJson);

            String sql = chatClient.prompt()
                    .system(systemPrompt)
                    .user(naturalLanguageQuery)
                    .call()
                    .content();

            if (sql == null || sql.isBlank()) {
                return errorJson("Failed to generate SQL for the query");
            }
            sql = sql.trim().replaceAll("```sql", "").replaceAll("```", "").trim();

            // Safety check: only allow SELECT queries
            if (!sql.toUpperCase().startsWith("SELECT")) {
                return errorJson("Only SELECT queries are allowed. Generated: " + sql);
            }

            // Execute the query
            try (Connection jdbcConn = getJdbcConnection(conn);
                 Statement stmt = jdbcConn.createStatement()) {
                stmt.setMaxRows(200);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    ArrayNode columns = objectMapper.createArrayNode();
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnName(i));
                    }

                    ArrayNode rows = objectMapper.createArrayNode();
                    while (rs.next()) {
                        ObjectNode row = objectMapper.createObjectNode();
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            if (val == null) {
                                row.putNull(meta.getColumnName(i));
                            } else {
                                row.put(meta.getColumnName(i), val.toString());
                            }
                        }
                        rows.add(row);
                    }

                    ObjectNode result = objectMapper.createObjectNode();
                    result.put("question", naturalLanguageQuery);
                    result.put("sql", sql);
                    result.put("rowCount", rows.size());
                    result.set("columns", columns);
                    result.set("rows", rows);
                    return objectMapper.writeValueAsString(result);
                }
            }

        } catch (Exception e) {
            return errorJson("Error querying database: " + e.getMessage());
        }
    }

    @Tool(description = "List all registered database connections for a client.")
    public String listDatabaseConnections(
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            List<DatabaseConnection> connections = connectionRepository.findByClientIdAndEnabled(clientId, true);
            ArrayNode array = objectMapper.createArrayNode();
            for (DatabaseConnection conn : connections) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", conn.getId().toString());
                node.put("name", conn.getName());
                node.put("description", conn.getDescription() != null ? conn.getDescription() : "");
                node.put("dbType", conn.getDbType());
                node.put("host", conn.getHost());
                node.put("port", conn.getPort());
                node.put("database", conn.getDatabaseName());
                array.add(node);
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("clientId", clientId);
            result.put("count", connections.size());
            result.set("connections", array);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return errorJson("Error listing connections: " + e.getMessage());
        }
    }

    @Tool(description = "Get the schema (tables, columns, types) of a database connection.")
    public String getDatabaseSchema(
            @ToolParam(description = "Connection ID") String connectionId,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            DatabaseConnection conn = resolveConnection(connectionId, clientId);
            if (conn == null) {
                return errorJson("Database connection not found: " + connectionId);
            }

            String schemaJson = fetchSchemaJson(conn);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("connectionId", connectionId);
            result.put("connectionName", conn.getName());
            result.put("dbType", conn.getDbType());
            result.set("schema", objectMapper.readTree(schemaJson));
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error getting schema: " + e.getMessage());
        }
    }

    @Tool(description = "Test a database connection to verify it is reachable.")
    public String testConnection(
            @ToolParam(description = "Connection ID") String connectionId,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            DatabaseConnection conn = resolveConnection(connectionId, clientId);
            if (conn == null) {
                return errorJson("Database connection not found: " + connectionId);
            }

            long startMs = System.currentTimeMillis();
            try (Connection jdbcConn = getJdbcConnection(conn)) {
                boolean valid = jdbcConn.isValid(5);
                long latencyMs = System.currentTimeMillis() - startMs;

                ObjectNode result = objectMapper.createObjectNode();
                result.put("connectionId", connectionId);
                result.put("connectionName", conn.getName());
                result.put("status", valid ? "ok" : "error");
                result.put("latencyMs", latencyMs);
                if (!valid) {
                    result.put("error", "Connection validation failed");
                }
                return objectMapper.writeValueAsString(result);
            }

        } catch (Exception e) {
            try {
                ObjectNode result = objectMapper.createObjectNode();
                result.put("connectionId", connectionId);
                result.put("status", "error");
                result.put("latencyMs", -1);
                result.put("error", e.getMessage());
                return objectMapper.writeValueAsString(result);
            } catch (Exception ex) {
                return errorJson("Error testing connection: " + e.getMessage());
            }
        }
    }

    private DatabaseConnection resolveConnection(String connectionId, String clientId) {
        try {
            Long id = Long.parseLong(connectionId);
            return connectionRepository.findByIdAndClientId(id, clientId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Connection getJdbcConnection(DatabaseConnection conn) throws SQLException {
        String url;
        String driverClass;
        switch (conn.getDbType().toLowerCase()) {
            case "mysql" -> {
                url = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabaseName();
                driverClass = "com.mysql.cj.jdbc.Driver";
            }
            case "postgresql", "postgres" -> {
                url = "jdbc:postgresql://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabaseName();
                driverClass = "org.postgresql.Driver";
            }
            case "mssql", "sqlserver" -> {
                url = "jdbc:sqlserver://" + conn.getHost() + ":" + conn.getPort() + ";databaseName=" + conn.getDatabaseName();
                driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            }
            default -> {
                url = "jdbc:" + conn.getDbType().toLowerCase() + "://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabaseName();
                driverClass = "";
            }
        }

        Properties props = new Properties();
        props.setProperty("user", conn.getUsername());
        props.setProperty("password", conn.getPassword());
        props.setProperty("connectTimeout", "10");
        props.setProperty("socketTimeout", "30");

        return DriverManager.getConnection(url, props);
    }

    private String fetchSchemaJson(DatabaseConnection conn) {
        try (Connection jdbcConn = getJdbcConnection(conn)) {
            DatabaseMetaData meta = jdbcConn.getMetaData();
            ArrayNode tables = objectMapper.createArrayNode();

            ResultSet tableRs = meta.getTables(conn.getDatabaseName(), null, "%", new String[]{"TABLE"});
            while (tableRs.next()) {
                String tableName = tableRs.getString("TABLE_NAME");
                ObjectNode tableNode = objectMapper.createObjectNode();
                tableNode.put("tableName", tableName);

                ArrayNode columns = objectMapper.createArrayNode();
                ResultSet colRs = meta.getColumns(null, null, tableName, "%");
                while (colRs.next()) {
                    ObjectNode colNode = objectMapper.createObjectNode();
                    colNode.put("name", colRs.getString("COLUMN_NAME"));
                    colNode.put("type", colRs.getString("TYPE_NAME"));
                    colNode.put("nullable", "YES".equals(colRs.getString("IS_NULLABLE")));
                    colNode.put("size", colRs.getInt("COLUMN_SIZE"));
                    columns.add(colNode);
                }
                tableNode.set("columns", columns);
                tables.add(tableNode);
            }

            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("database", conn.getDatabaseName());
            schema.put("dbType", conn.getDbType());
            schema.set("tables", tables);
            return objectMapper.writeValueAsString(schema);

        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String errorJson(String message) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("error", true);
            node.put("message", message);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{\"error\":true,\"message\":\"" + message.replace("\"", "'") + "\"}";
        }
    }
}

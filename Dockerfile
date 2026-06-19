# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy parent POM first to cache dependency resolution
COPY pom.xml ./
COPY core/pom.xml core/
COPY mcp-servers/api-mcp-server/pom.xml mcp-servers/api-mcp-server/
COPY mcp-servers/document-mcp-server/pom.xml mcp-servers/document-mcp-server/
COPY mcp-servers/database-mcp-server/pom.xml mcp-servers/database-mcp-server/
COPY mcp-servers/memory-mcp-server/pom.xml mcp-servers/memory-mcp-server/
COPY mcp-servers/notification-mcp-server/pom.xml mcp-servers/notification-mcp-server/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl core -am -q

# Copy source and build
COPY core/src core/src
RUN mvn package -pl core -am -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/core/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

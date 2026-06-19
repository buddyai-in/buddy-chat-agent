# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy parent POM first to cache dependency resolution
COPY pom.xml ./
COPY mcp-servers/api-mcp-server/pom.xml mcp-servers/api-mcp-server/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl mcp-servers/api-mcp-server -am -q

# Copy source and build
COPY mcp-servers/api-mcp-server/src mcp-servers/api-mcp-server/src
RUN mvn package -pl mcp-servers/api-mcp-server -am -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/mcp-servers/api-mcp-server/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

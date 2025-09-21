# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Leverage dependency layer caching
COPY backend/pom.xml ./
RUN --mount=type=cache,id=s/73616753-6fb3-4cd8-b40c-5be58e266018-/root/.m2,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources (excluding large data files via .dockerignore)
COPY backend/src ./src

# Copy the compressed database for production builds only (controlled by build arg)
ARG INCLUDE_DATABASE=false
COPY celebrity_graph.db.gz* ./
RUN if [ "$INCLUDE_DATABASE" = "false" ]; then rm -f ./celebrity_graph.db.gz; fi

# Package the app (skip tests) with Maven repo cache
RUN --mount=type=cache,id=s/73616753-6fb3-4cd8-b40c-5be58e266018-/root/.m2,target=/root/.m2 mvn -q -DskipTests package
# Runtime stage - Use Alpine for smaller size
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar /app/app.jar

# Copy the compressed database for fast startup (if it exists)
COPY --from=build /app/celebrity_graph.db.gz* /app/

EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=8080
ENTRYPOINT ["sh", "-c", "java -Xmx1g -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=$PORT -jar /app/app.jar"]

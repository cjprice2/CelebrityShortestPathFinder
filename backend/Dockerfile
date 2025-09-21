# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Leverage dependency layer caching
COPY pom.xml ./
RUN --mount=type=cache,id=s/73616753-6fb3-4cd8-b40c-5be58e266018-/root/.m2,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources (excluding large data files via .dockerignore)
COPY src ./src

# Download database during build (more resources available)
RUN mkdir -p /app/data
RUN wget -O /app/data/celebrity_graph.db.gz https://github.com/cjprice2/CelebrityShortestPathFinder/releases/download/v1.0.0/celebrity_graph.db.gz
RUN gunzip /app/data/celebrity_graph.db.gz

# Package the app (skip tests) with Maven repo cache
RUN --mount=type=cache,id=s/73616753-6fb3-4cd8-b40c-5be58e266018-/root/.m2,target=/root/.m2 mvn -q -DskipTests package
# Runtime stage - Use Alpine for smaller size
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar /app/app.jar

# Copy the pre-downloaded database
COPY --from=build /app/data/celebrity_graph.db /app/data/

EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=8080
ENTRYPOINT ["sh", "-c", "java -Xmx1g -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=$PORT -jar /app/app.jar"]

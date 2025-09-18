# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Leverage dependency layer caching
COPY backend/pom.xml ./
RUN --mount=type=cache,id=s/73616753-6fb3-4cd8-b40c-5be58e266018-/root/.m2,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources (including resources with the data files)
COPY backend/src ./src

# Package the app (skip tests) with Maven repo cache
RUN --mount=type=cache,id=s/73616753-6fb3-4cd8-b40c-5be58e266018-/root/.m2,target=/root/.m2 mvn -q -DskipTests package
# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install wget for basic utilities
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Copy the built JAR
COPY --from=build /app/target/*.jar /app/app.jar

# Copy graph cache into the image (workaround for broken Railway volumes)
COPY backend/graph-cache.bin /app/graph-cache.bin

EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Xmx1g -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=$PORT -jar /app/app.jar"]

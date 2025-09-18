# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Leverage dependency layer caching
COPY pom.xml ./
RUN --mount=type=cache,id=s/523342f2-3005-4f92-bd83-c9046975218b-/root/.m2,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources (including resources with the data files)
COPY src ./src

# Package the app (skip tests) with Maven repo cache
RUN --mount=type=cache,id=s/523342f2-3005-4f92-bd83-c9046975218b-/root/.m2,target=/root/.m2 mvn -q -DskipTests package
# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install wget for basic utilities
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Copy the built JAR
COPY --from=build /app/target/*.jar /app/app.jar

# Copy data processing scripts (optional, for data generation)
COPY --from=build /app/src/main/resources/process_imdb_data.py /app/

EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Xmx1g -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=$PORT -jar /app/app.jar"]

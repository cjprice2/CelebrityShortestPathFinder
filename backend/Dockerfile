# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Leverage dependency layer caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources (including resources with the data files)
COPY src ./src

# Package the app (skip tests) with Maven repo cache
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar /app/app.jar

# Copy data processing scripts (optional, for data generation)
COPY --from=build /app/src/main/resources/process_imdb_data.py /app/
COPY --from=build /app/src/main/resources/convert_to_cytoscape.py /app/

# Copy cache download script
COPY download-cache.sh /app/
RUN chmod +x /app/download-cache.sh

EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENTRYPOINT ["sh", "-c", "/app/download-cache.sh && java $JAVA_OPTS -Dserver.address=0.0.0.0 -jar /app/app.jar"]

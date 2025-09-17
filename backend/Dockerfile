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

# Install curl for cache downloading (more memory efficient)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

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
ENTRYPOINT ["/bin/sh", "-c", "curl -L -o /tmp/graph-cache.bin https://github.com/cjprice2/CelebrityShortestPathFinder/releases/download/v1.1.0/graph-cache.bin --connect-timeout 30 --max-time 600 --retry 3 && java -Xmx1500m -Xms512m $JAVA_OPTS -Dserver.address=0.0.0.0 -jar /app/app.jar"]

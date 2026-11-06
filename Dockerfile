# Build stage. The pom is copied first so the dependency layer is cached between builds.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/cfp.jar extract --destination target/extracted

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
# The extracted layout (cfp.jar plus lib/), which the AOT cache below needs.
COPY --from=build /build/target/extracted/ /app/
# The container's CPU is slow enough that start-up took about 35 s before the warm-up. A training run records the
# classes Spring loads and links into an AOT cache (JEP 483) that the real start-up maps in. It stops once the context
# has refreshed, so it needs no database: SQL init and the warm-up are off. It runs here, not in the build stage,
# because the cache only works with the same JVM. If the cache can't be used, the JVM starts without it.
RUN java -XX:AOTCacheOutput=/app/app.aot -Dspring.context.exit=onRefresh -Dspring.sql.init.mode=never \
        -Dcfp.warm-up.rounds=0 -jar /app/cfp.jar
# Scaleway's database needs TLS; the Compose Postgres doesn't, so application.properties defaults to off.
ENV PGSSLMODE=require
# A deploy opens its port when the harness is quiet, so the hand-over from the old instance falls between two bursts.
# See OpenPortInQuietWindow. Off everywhere else, so tests and local runs start at once.
ENV CFP_QUIET_WINDOW_ENABLED=true
# GET /health reports this as its version: docker build --build-arg GIT_SHA=$(git rev-parse HEAD) .
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=/app/app.aot", "-jar", "/app/cfp.jar"]

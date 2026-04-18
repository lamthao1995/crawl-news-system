FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
# Pre-fetch dependencies so the later `package` step hits the local Maven cache.
COPY pom.xml .
RUN mvn -q -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -q -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system crawl && useradd --system --gid crawl --home /app crawl \
    && mkdir -p /app && chown -R crawl:crawl /app
WORKDIR /app
COPY --chown=crawl:crawl docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
# Glob avoids hardcoding the pom `version`; shade plugin replaces the plain jar with the uber jar.
COPY --from=build --chown=crawl:crawl /src/target/crawl-news-worker-*.jar /app/worker.jar
USER crawl
ENTRYPOINT ["/docker-entrypoint.sh"]

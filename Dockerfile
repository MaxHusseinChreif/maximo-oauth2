# ── Stage 1: build the JAR ───────────────────────────────────────────────────
FROM docker.io/library/maven:3.9.15-eclipse-temurin-17-noble AS build
WORKDIR /app

# copy only what’s needed for dependency resolution first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# now copy sources, run tests, and compile
COPY src ./src
RUN mvn clean package -B

# ── Stage 2: slimmer runtime ─────────────────────────────────────────────────
FROM docker.io/library/eclipse-temurin:17-jre-noble
WORKDIR /app

# Allow both local Docker and OpenShift's arbitrary UID model to run without root.
COPY --from=build --chown=1001:0 /app/target/*.jar app.jar
RUN chgrp -R 0 /app && chmod -R g=u /app

# expose whatever your app uses (default 8080)
EXPOSE 8080

# Docker Compose runs as 1001; OpenShift may replace it with an arbitrary non-root UID.
USER 1001

# launch
ENTRYPOINT ["java","-jar","app.jar"]

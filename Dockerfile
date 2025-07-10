# ── Stage 1: build the JAR ───────────────────────────────────────────────────
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app

# copy only what’s needed for dependency resolution first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# now copy sources and compile
COPY src ./src
RUN mvn package

# ── Stage 2: slimmer runtime ─────────────────────────────────────────────────
FROM openjdk:17-jdk-slim
WORKDIR /app

# grab the jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# expose whatever your app uses (default 8080)
EXPOSE 8080

# launch
ENTRYPOINT ["java","-jar","app.jar"]

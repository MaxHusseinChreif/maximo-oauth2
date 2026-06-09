# ── Stage 1: build the JAR ───────────────────────────────────────────────────
FROM docker.io/library/maven:3.9.15-eclipse-temurin-17-noble AS build
WORKDIR /app

# copy only what’s needed for dependency resolution first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# now copy sources and compile
COPY src ./src
RUN mvn clean package -DskipTests

# ── Stage 2: slimmer runtime ─────────────────────────────────────────────────
FROM docker.io/library/eclipse-temurin:17-jre-noble
WORKDIR /app

# grab the jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# expose whatever your app uses (default 8080)
EXPOSE 8080

# launch
ENTRYPOINT ["java","-jar","app.jar"]

# ---------- STAGE 1: Build & Extract Layers
FROM maven:3.9.11-eclipse-temurin-25-noble AS builder
WORKDIR /build

COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ---------- STAGE 2: Runtime
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl && \
    addgroup -S spring && \
    adduser -u 1000 -S -G spring spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /build/target/extracted/application/ ./

USER spring

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -f http://127.0.0.1:9090/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "org.springframework.boot.loader.launch.JarLauncher"]
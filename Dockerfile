# ---------- STAGE 1: Builder (Compilación y Extracción de Capas) ----------
FROM maven:3.9.12-eclipse-temurin-25-alpine AS builder
WORKDIR /build

COPY pom.xml .
# Aprovechamiento de la caché de BuildKit para dependencias de Maven
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src
# Compilación empaquetada omitiendo tests para acelerar el pipeline CI/CD
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package

# Extracción de las capas del JAR usando el modo layertools nativo de Spring Boot
# Esto optimiza drásticamente el uso de caché de Docker en la subida a registries
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted


# ---------- STAGE 2: Custom JRE Builder (Minimización y Hardening) ----------
FROM eclipse-temurin:25-jdk-alpine AS jre-builder
WORKDIR /jre-gen

# Generación de un JRE mínimo a medida mediante jlink.
# Se eliminan herramientas de desarrollo y módulos innecesarios para reducir peso y vectores de ataque.
# Incluye módulos criptográficos esenciales (TLS) y bases requeridas para arquitecturas Spring Boot y bases de datos.
RUN jlink \
    --add-modules java.base,java.logging,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-9 \
    --output custom-jre


# ---------- STAGE 3: Runtime (Entorno de Producción Ultra Liviano y Seguro) ----------
FROM alpine:3.23.3 AS runtime

# Variables de entorno para referenciar el JRE personalizado
ENV JAVA_HOME=/opt/jre
ENV PATH="$JAVA_HOME/bin:$PATH"

# 1. Instalación de paquetes esenciales del sistema:
#    - curl: Requerido para el Healthcheck y peticiones de red.
#    - bash: Solicitado para scripts de inicialización o debugging controlado.
#    - tzdata: Gestión de zonas horarias.
#    - ca-certificates: Para comunicación segura TLS/SSL externa.
# 2. Creación de un usuario y grupo de sistema sin privilegios (UID/GID 1000).
RUN apk add --no-cache curl bash tzdata ca-certificates && \
    addgroup -S spring && \
    adduser -u 1000 -S -G spring spring

WORKDIR /app

# Copia del JRE minimizado desde el Stage 2
COPY --from=jre-builder /jre-gen/custom-jre /opt/jre

# Copia de las capas de Spring Boot desde el Stage 1, asignando propiedad directa al usuario no-root
COPY --from=builder --chown=spring:spring /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /build/target/extracted/application/ ./

# Aplicación de política de mínimos privilegios: Transición al usuario sin permisos root
USER spring

EXPOSE 8080 9090

# Monitorización de estado de la aplicación utilizando el actuador de Spring Boot
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -f http://127.0.0.1:9090/actuator/health || exit 1

# Entrypoint optimizado para contenedores (Container-Aware)
# Se define límite de RAM relativo y política de finalización ante falta de memoria para delegar el reinicio al orquestador.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-XX:+UseG1GC", \
            "org.springframework.boot.loader.launch.JarLauncher"]
#
# APP_VERSION  - versión de la aplicación (se inyecta desde el build y se usa para:
#                - fijar el -Drevision de Maven (versionado del artefacto)
#                - etiquetar la imagen (labels OCI)
#                - exponerla como variable de entorno dentro del contenedor)
#
# Ejemplo (build):
#   docker build --build-arg APP_VERSION=1.0.0 -t primer-springboot-azure:1.0.0 .
#
# Ejemplo (run local):
#   docker run --rm -p 8080:8080 primer-springboot-azure:1.0.0
#
# (Opcional) Metadatos CI/CD:
#   - VCS_REF: commit SHA
#   - BUILD_DATE: timestamp ISO-8601
#   - SOURCE_URL: URL del repositorio

ARG APP_VERSION=0.0.0
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
ARG SOURCE_URL=unknown

FROM maven:3.9.12-eclipse-temurin-25 AS build
ARG APP_VERSION

WORKDIR /workspace

COPY pom.xml ./

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -Drevision="${APP_VERSION}" -DskipTests dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -Drevision="${APP_VERSION}" -DskipTests clean package


# Build App
FROM eclipse-temurin:25-jre


ARG APP_VERSION=0.0.0
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
ARG SOURCE_URL=unknown

# Labels OCI
LABEL org.opencontainers.image.title="${artifactId}" \
      org.opencontainers.image.description="Spring Boot microservice" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.source="${SOURCE_URL}"

ENV APP_VERSION="${APP_VERSION}" \
    PORT="8080" \
    JAVA_TOOL_OPTIONS=""

WORKDIR /app

# Instalar curl en imagenes Debian/Ubuntu
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

# Usuario no-root
RUN useradd -r -u 10001 appuser
USER appuser

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]

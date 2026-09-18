# Imagen del microservicio.
#
# ⚠️ Parte de un jar YA CONSTRUIDO (`mvn package`), no compila dentro de la imagen. `make docker-build`
# encadena las dos cosas, asi que en la practica no cambia nada para quien la usa.
#
# Antes si compilaba aqui dentro, con Maven, y salio caro: para aprovechar la cache de capas habia que
# copiar los pom.xml uno a uno, o sea REPETIR la lista de modulos del proyecto dentro del Dockerfile. El
# dia que el proyecto paso a tener cuatro modulos, esa lista se quedo en dos y `docker build` empezo a
# fallar con «Child module /workspace/client-java does not exist». Nadie se entero durante semanas porque
# nada construia la imagen.
#
# La leccion no es sobre Docker: un fichero que REPITE la estructura del proyecto se desincroniza. Este
# ya no la repite — copia un jar y punto — y ademas la puerta lo construye en cada cambio.
#
# Build:
#   make docker-build                      (recomendado: empaqueta y construye)
#   docker build --build-arg APP_VERSION=1.0.0 -t ${artifactId}:1.0.0 .
#
# Run:
#   docker run --rm -p 8080:8080 ${artifactId}:1.0.0
#   docker run --rm -p 9090:9090 -e SERVER_PORT=9090 ${artifactId}:1.0.0
#
# Metadatos opcionales para CI/CD: VCS_REF (commit), BUILD_DATE (ISO-8601), SOURCE_URL (repositorio).

# ══════════════════════════════════════════════════════════════════════════════════════════════
# Fase 1: partir el jar en capas
# ══════════════════════════════════════════════════════════════════════════════════════════════
# Un jar de Spring Boot son ~60 MB de los que TUS clases son unos cientos de kilobytes. Copiado
# entero, cada cambio de una linea invalida la capa completa y el push se lleva los 60 MB otra vez.
# `jarmode=tools ... extract --layers` lo separa en cuatro capas por frecuencia de cambio: las
# dependencias (que casi nunca cambian) quedan en una capa que se reaprovecha entre builds.
FROM eclipse-temurin:25-jre AS extractor

WORKDIR /extract

# Wildcard y no un nombre fijo: el jar lleva la version en el nombre. `*.jar` no captura el
# `.jar.original` que deja spring-boot:repackage, porque ese no termina en `.jar`.
ARG JAR_FILE=app/target/*.jar
COPY ${JAR_FILE} app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ══════════════════════════════════════════════════════════════════════════════════════════════
# Fase 2: la imagen final
# ══════════════════════════════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:25-jre

ARG APP_VERSION=0.0.0
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
ARG SOURCE_URL=unknown

LABEL org.opencontainers.image.title="${artifactId}" \
      org.opencontainers.image.description="Spring Boot microservice" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.source="${SOURCE_URL}"

# SERVER_PORT lo lee la propia aplicacion (`server.port` en application.yaml) Y el HEALTHCHECK de aqui
# abajo. Antes esta variable se llamaba PORT y solo la miraba el healthcheck: cambiarla movia la
# comprobacion a un puerto donde no habia nadie, y el contenedor se declaraba enfermo estando sano.
ENV APP_VERSION="${APP_VERSION}" \
    SERVER_PORT="8080" \
    JAVA_TOOL_OPTIONS=""

RUN useradd -r -u 10001 appuser

WORKDIR /app

# El orden importa: de lo que menos cambia a lo que mas. Asi un cambio en tu codigo solo invalida la
# ultima capa.
COPY --from=extractor --chown=appuser:appuser /extract/extracted/dependencies/ ./
COPY --from=extractor --chown=appuser:appuser /extract/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appuser /extract/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appuser /extract/extracted/application/ ./

USER appuser

EXPOSE 8080

# ⚠️ Aqui habia un HEALTHCHECK con curl, y se ha quitado a proposito.
#
# La imagen base (JRE sobre Ubuntu) no trae curl, ni wget, ni nc: habia que instalarlo con apt, lo que
# anade una capa, engorda la imagen y —lo peor— mete una dependencia de RED en el build, que es de las
# cosas que rompen un dia sin avisar.
#
# Y a cambio de eso, ¿quien lo usaba? Nadie: **Kubernetes ignora el HEALTHCHECK de Docker**, porque usa
# las probes del Deployment (estan en helm/, que es donde hay que tocarlas). Y el compose de este
# proyecto levanta Postgres y Redis, no la aplicacion.
#
# Si lo necesitas para un `docker run` o un compose propio, se recupera anadiendo curl y:
#   HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#     CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

# `app.jar` aqui es el jar FINO que deja la extraccion: no lleva las dependencias dentro, las encuentra
# en las capas de al lado.
ENTRYPOINT ["java","-jar","app.jar"]

#
# APP_VERSION  - la versión de la aplicación (se inyecta como variable de la aplicación y del sistema).
#
# Ejemplo build:
#   > docker build --build-arg APP_VERSION=1.0 -t mi-imagen-name:1.0 .

# Args
ARG APP_VERSION=unknown

FROM maven:4.0.0-rc-4-eclipse-temurin-25 AS build


ARG APP_VERSION

WORKDIR /app
COPY pom.xml .

RUN mvn dependency:go-offline

COPY src/ /app/src

RUN mvn versions:set -DnewVersion="${APP_VERSION}" && mvn package -DskipTests -Dspring.profiles.active=build


# Build App
FROM eclipse-temurin:25-jdk

LABEL maintainer="Ramon Invarato"
LABEL version="1.0"
LABEL description="Docker Image for Spring Boot"

ARG APP_VERSION
ENV APP_VERSION ${APP_VERSION}

EXPOSE 8080
WORKDIR /usr/src/app

COPY --from=build /app/target/*.jar app.jar

RUN yum -y install curl
HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

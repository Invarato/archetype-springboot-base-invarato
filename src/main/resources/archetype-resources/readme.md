#[[#]]# ${artifactId}

Proyecto generado con el arquetipo base de Spring Boot.

- Grupo: ${groupId}
- Artefacto: ${artifactId}
- Versión: ${version}

#[[##]]# Requisitos mínimos
- JDK 25
- Maven 3.6.3+
- Docker 27+ (opcional, para base de datos y/o Testcontainers)

#[[##]]# Empezando
1. Compilar y ejecutar pruebas:
   ```shell
   mvn clean verify
   ```
2. Ejecutar la aplicación:
   ```shell
   mvn spring-boot:run
   ```
3. Endpoint de ejemplo (puede variar según plantilla):
   - GET http://localhost:8080/api/example?value=test

#[[##]]# Configuración
- Archivo principal: src/main/resources/application.properties
- Variables relevantes para la base de datos y puertos.

#[[##]]# Estructura del proyecto
- src/main/java: código fuente (controladores, servicios, repositorios, entidades)
- src/main/resources: configuración (application.properties, Liquibase, estáticos)
- src/test/java: pruebas unitarias e integración
- Dockerfile y compose.yml opcionales para entornos locales

#[[##]]# Base de datos y migraciones
- Liquibase con changelog en src/main/resources/db/changelog
- Para levantar un PostgreSQL local:
  ```shell
  docker compose -f compose.yml up -d
  ```

#[[##]]# Pruebas
- Unitarias y de integración con JUnit
- Integración con Testcontainers (si Docker está disponible)

#[[##]]# Comandos útiles
```shell
# Compilar sin tests
mvn -DskipTests package

# Ejecutar solo tests
mvn test

# Formato estático (si aplica) y verificación completa
mvn verify
```

#[[##]]# Licencia
Este proyecto se distribuye bajo licencia MIT (o la que definas en tu POM).

#[[##]]# Autor
Generado para ${groupId}:${artifactId} con la versión ${version} del arquetipo.

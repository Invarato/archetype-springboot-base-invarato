#[[#]]# ${artifactId}

Microservicio Spring Boot generado con el arquetipo base.

- Grupo: `${groupId}`
- Artefacto: `${artifactId}`

#[[##]]# Requisitos mínimos
- JDK 25
- Docker 27+ (para la base de datos local y para los tests de integración con Testcontainers)
- Maven **no** hace falta instalarlo: usa el wrapper `./mvnw`

#[[##]]# Empezando

```shell
make help          # todos los objetivos disponibles
make run           # levanta Postgres/Redis y arranca la aplicación en el 8080
make test          # tests unitarios (*Test.java)
make verify        # tests unitarios + integración (*IT.java, necesita Docker)
```

- API de ejemplo: `GET http://localhost:8080/api/v1/examples/hello`
- Swagger UI: `http://localhost:8080/docs`
- Actuator: `http://localhost:8080/actuator/health`

#[[##]]# Estructura

Es un proyecto **multi-módulo** aunque de momento solo haya un servicio: así el contrato y los clientes
generados tienen dónde vivir sin tener que reorganizarlo todo más adelante.

```
pom.xml            padre: la versión, las versiones de dependencias y los plugins comunes
.mvn/maven.config  el valor por defecto de ${revision} (necesario para poder hacer `mvn -pl`)
app/               EL MICROSERVICIO. Lo único que produce un jar ejecutable y una imagen Docker
  src/main/java/           controladores, servicios, repositorios, entidades, DTOs y mappers
  src/main/resources/      application.yaml y las migraciones de db/migration
contract/          el contrato OpenAPI, y más adelante los clientes Java/Python y stubs generados
compose-app.yml    Postgres y Redis para desarrollo
helm/            el chart de despliegue, con values por entorno
Dockerfile · compose-app.yml · .devcontainer/
```

⚠️ La dependencia va siempre `contract` ← `app`, nunca al revés.

#[[##]]# Configuración

Un **único** fichero: `app/src/main/resources/application.yaml` (y `app/src/test/resources/application.yaml`
para los tests). El perfil activo por defecto es `dev`.

#[[##]]# Versión del artefacto

La versión del reactor es la propiedad `${revision}`. En local vale `0.0.0-SNAPSHOT`; en CI se pasa desde el
build, normalmente desde el tag de git:

```shell
./mvnw -Drevision=1.2.3 clean install
```

`flatten-maven-plugin` se encarga de que el POM publicado lleve la versión ya resuelta.

#[[##]]# Base de datos y migraciones

El esquema lo gobierna **Flyway**: las migraciones están en `app/src/main/resources/db/migration/` y se
aplican **al arrancar la aplicación**. `ddl-auto` es `validate` en todos los entornos, así que si cambias una
entidad y olvidas la migración, la aplicación no arranca — que es justo lo que quieres que pase.

```shell
make db-baseline   # SOLO la primera vez: genera V1__init.sql desde las entidades
make db-info       # estado de las migraciones (con la app arrancada)
```

Las siguientes migraciones (`V2__...`, `V3__...`) se escriben a mano, y **una ya aplicada no se edita nunca**.
Los datos de prueba no van por Flyway.

#[[##]]# Pruebas

- Unitarias (`*Test.java`) con JUnit 5 y Mockito: no necesitan Docker.
- Integración (`*IT.java`) con **Testcontainers**: levantan su propio Postgres efímero, distinto del de
  desarrollo, para no ensuciar la base con la que estás trabajando.

#[[##]]# Licencia

Define la que quieras en el POM.

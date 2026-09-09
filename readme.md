# Maven Archetype: Spring Boot Base (Invarato)

[![verify](https://github.com/Invarato/archetype-springboot-base-invarato/actions/workflows/verify.yml/badge.svg)](https://github.com/Invarato/archetype-springboot-base-invarato/actions/workflows/verify.yml)
[![Maven Central](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fjarroba%2Farchetype-springboot-base-invarato%2Fmaven-metadata.xml&style=flat-square&label=Maven%20Central)](https://central.sonatype.com/artifact/com.jarroba/archetype-springboot-base-invarato)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

Arquetipo Maven para arrancar **microservicios Spring Boot** que funcionan desde el primer `make run`:
listos para contenedor y Kubernetes, con seguridad puesta, migraciones, contrato OpenAPI y clientes
generados.

La promesa es concreta: **lo generado compila, pasa sus tests y arranca a la primera**. Y no es una
declaración de intenciones — hay un gate automático que lo comprueba en cada cambio (ver más abajo).

---

## Crear un proyecto

```shell
mvn archetype:generate \
  -DarchetypeGroupId=com.jarroba \
  -DarchetypeArtifactId=archetype-springboot-base-invarato \
  -DarchetypeVersion=2.0.0 \
  -DgroupId=com.ejemplo \
  -DartifactId=mi-servicio
```

Y a trabajar:

```shell
cd mi-servicio
make help          # todo lo que puedes hacer
make run           # levanta Postgres/Redis y arranca la API en el 8080
make token         # un JWT de desarrollo para llamar a la API
make verify        # tests unitarios + integración (Testcontainers)
```

**Requisitos:** JDK 25 y Docker. Maven no hace falta: el proyecto trae wrapper.

---

## Qué genera

Un reactor multi-módulo, aunque de momento solo haya un servicio — porque el contrato y los clientes
necesitan un sitio, y montarlo después es un refactor que toca Dockerfile, compose, k8s y CI a la vez:

```
mi-servicio/
├── app/              el microservicio
├── contract/         el contrato OpenAPI, versionado y empaquetado
├── client-java/      cliente Java generado del contrato
├── client-python/    cliente Python generado del contrato
├── compose-app.yml · Dockerfile · k8s/ · .devcontainer/
```

### Lo que trae dentro

| | |
|---|---|
| **Java 25 (LTS)** · **Spring Boot 4.1** | Ni por debajo ni por encima: 26 no es LTS y su ventana de soporte se cierra enseguida. |
| **Seguridad siempre activa** | Una sola cadena de filtros, *stateless*, con JWT (OAuth2 Resource Server). **No hay perfil que la apague**, tampoco en los tests. Lo que cambia entre entornos no son las reglas, es de dónde salen los tokens. |
| **Migraciones con Flyway** | `make db-baseline` genera el `V1__init.sql` desde tus entidades JPA, sin necesitar base de datos. A partir de ahí manda Flyway, y `ddl-auto: validate` hace de detector de deriva: si olvidas una migración, la aplicación no arranca. |
| **Contrato OpenAPI *code-first*** | Escribes Java y el contrato cae solo. Un test lo compara con el fichero versionado: **ningún cambio de contrato pasa inadvertido**. |
| **Clientes generados** | Java (con `RestClient`) y Python, desde el contrato. Sin compartir los DTOs del servidor, que ataría al consumidor a tu versión de Boot. |
| **Tests de arquitectura** | ArchUnit impone la estructura que el arquetipo enseña: capas, nombres, inyección por constructor y *nunca* entidades en los controladores. |
| **Observabilidad** | `/actuator/prometheus`, logs en JSON fuera de local, y `traceId`/`spanId` correlacionados — más la cabecera `X-Trace-Id` en cada respuesta. |
| **Errores en `ProblemDetail`** | RFC 9457, con los errores de validación campo a campo. |
| **Testcontainers** | Los tests de integración levantan su propio Postgres, aparte del de desarrollo. |
| **Devcontainer** | Con Docker dentro, para poder correr los tests de integración desde el minuto cero. |

---

## Cómo se garantiza que funciona

El fallo clásico de un arquetipo es silencioso: `mvn install` da `BUILD SUCCESS` porque solo empaqueta
ficheros de texto — **no los compila**. Se puede publicar un arquetipo que genere un proyecto roto sin que
nada avise.

Por eso aquí el gate no es el build del arquetipo, sino:

```shell
make verify    # instala el arquetipo → genera un proyecto → lo compila, comprueba y pasa sus tests
```

Incluye ocho comprobaciones estructurales que el compilador no puede ver, y **cada una viene de un fallo
real**: placeholders sin sustituir, documentación que perdió líneas al filtrarse, un `devcontainer.json`
que no parsea, el wrapper sin permisos de ejecución, manifiestos referenciados que no existen, o una
entidad publicada en el contrato.

Corre en CI en cada push, en cada PR y una vez por semana — esto último porque las versiones las gestiona
el BOM de Spring Boot y el build puede romperse sin que nadie toque el repo.

---

## Documentación

- **[docs/base-del-proyecto.md](docs/base-del-proyecto.md)** — qué se decidió, **por qué**, y qué reabriría
  cada decisión. Además de los tropiezos ya pagados, para no repetirlos.
- **[docs/flujo-de-trabajo.md](docs/flujo-de-trabajo.md)** — el gate y qué caza cada comprobación.
- **[docs/migraciones.md](docs/migraciones.md)** — cómo nace y evoluciona el esquema.

El proyecto generado trae su propio `readme.md` y su `make help`.

---

## Desarrollo del arquetipo

```shell
make help        # objetivos y variables
make rebuild     # clean + build + generate (ciclo rápido mientras editas)
make verify      # EL GATE
```

⚠️ **No te fíes del `BUILD SUCCESS` del arquetipo.** El único verde que cuenta es el del proyecto generado.

Detalle que despista: dentro de `src/main/resources/archetype-resources/` todo es *plantilla*. No compila
por sí sola —lleva `${groupId}` y `${artifactId}` sin resolver— y pasa por Velocity al generarse, así que
`##` y `${VAR:defecto}` necesitan escaparse. Está explicado en `docs/`.

## Publicar una versión

Se empuja un tag y CI se encarga:

```shell
git tag v2.0.0 && git push origin v2.0.0
```

La versión sale **del tag**, no de un fichero, así que no puede desalinearse. Y el workflow **pasa el gate
antes de publicar**: en Maven Central una versión publicada no se puede borrar ni reemplazar.

Requiere estos secretos en el repositorio: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (token del
portal de Sonatype), `GPG_PRIVATE_KEY` y `GPG_PASSPHRASE`.

## Licencia

MIT. Ver [LICENSE](LICENSE).

# Makefile - Guía de Uso

Esta guía documenta el uso del Makefile incluido en el proyecto Spring Boot para facilitar el desarrollo y las operaciones comunes.

#[[##]]# Tabla de Contenidos

- [Requisitos Previos](#requisitos-previos)
- [Inicio Rápido](#inicio-rápido)
- [Comandos de Desarrollo](#comandos-de-desarrollo)
- [Comandos de Build](#comandos-de-build)
- [Comandos Docker](#comandos-docker)
- [Comandos de Base de Datos (Flyway)](#comandos-de-base-de-datos-flyway)
- [Comandos de Dependencias](#comandos-de-dependencias)
- [Comandos de Calidad de Código](#comandos-de-calidad-de-código)
- [Comandos de Monitoreo](#comandos-de-monitoreo)
- [Workflows Rápidos](#workflows-rápidos)
- [Variables de Configuración](#variables-de-configuración)
- [Ejemplos de Uso](#ejemplos-de-uso)

#[[##]]# Requisitos Previos

- **Make**: Instalado en tu sistema
- **Docker**: Para ejecutar servicios de base de datos
- **Maven**: Incluido vía Maven Wrapper (`./mvnw`)
- **Java 25**: Según configuración del proyecto

#[[##]]# Inicio Rápido

Para ver todos los comandos disponibles:

```bash
make help
```

Para iniciar el desarrollo rápidamente:

```bash
make dev
```

Este comando levanta los servicios Docker y ejecuta la aplicación en modo desarrollo.

---

#[[##]]# Comandos de Desarrollo

#[[###]]# `make run`

Ejecuta la aplicación con el perfil de desarrollo (dev).

```bash
make run
```

Automáticamente levanta los servicios Docker (PostgreSQL, Redis) antes de iniciar la aplicación.

#[[###]]# `make run-dev`

Ejecuta explícitamente la aplicación con el perfil dev.

```bash
make run-dev
```

#[[###]]# `make run-prod`

Ejecuta la aplicación con el perfil de producción.

```bash
make run-prod
```

#[[###]]# `make test`

Ejecuta todos los tests del proyecto.

```bash
make test
```

#[[###]]# `make verify`

Ejecuta tests y verifica que el build sea correcto.

```bash
make verify
```

#[[###]]# `make stop`

Detiene la aplicación en ejecución.

```bash
make stop
```

---

#[[##]]# Comandos de Build

#[[###]]# `make clean`

Limpia todos los artefactos de compilación.

```bash
make clean
```

#[[###]]# `make build`

Limpia y compila el proyecto.

```bash
make build
```

#[[###]]# `make package`

Crea el archivo JAR/WAR del proyecto (salta los tests).

```bash
make package
```

El artefacto generado se encuentra en `target/`.

#[[###]]# `make install`

Instala el proyecto en el repositorio Maven local.

```bash
make install
```

---

#[[##]]# Comandos Docker

#[[###]]# `make docker-up`

Inicia los servicios Docker definidos en `compose-app.yml`:
- PostgreSQL (puerto 5432)
- Redis (puerto 6379)

```bash
make docker-up
```

#[[###]]# `make docker-down`

Detiene todos los servicios Docker.

```bash
make docker-down
```

#[[###]]# `make docker-restart`

Reinicia todos los servicios Docker.

```bash
make docker-restart
```

#[[###]]# `make docker-build`

Construye la imagen Docker de la aplicación.

```bash
make docker-build
```

Por defecto usa el nombre del proyecto y el tag `latest`.

#[[###]]# `make docker-run`

Ejecuta la aplicación en un contenedor Docker.

```bash
make docker-run
```

#[[###]]# `make logs`

Muestra los logs de los servicios Docker en tiempo real.

```bash
make logs
```

Presiona `Ctrl+C` para salir.

---

#[[##]]# Comandos de Base de Datos (Flyway)

El esquema lo gobierna **Flyway**. Las migraciones viven en
`app/src/main/resources/db/migration/` y se aplican **al arrancar la aplicación**, no con un comando aparte.

`spring.jpa.hibernate.ddl-auto` es `validate` en todos los entornos: Hibernate nunca toca el esquema, solo
comprueba que las entidades cuadran con él. Ese `validate` es además el **detector de deriva**: si añades un
campo a una entidad y olvidas la migración, la aplicación no arranca.

#[[###]]# `make db-baseline`

Genera la **primera** migración (`V1__init.sql`) a partir del metadato de las entidades JPA. No necesita
ninguna base de datos levantada.

```shell
make db-baseline
```

Deja el resultado en `app/target/generated-schema/V1__init.sql`. **Léelo antes de moverlo**: Hibernate
nombra índices y constraints como le parece y no pone comentarios. Cuando te convenza:

```shell
mv app/target/generated-schema/V1__init.sql app/src/main/resources/db/migration/
```

⚠️ **Solo la primera vez.** A partir de ahí manda Flyway y las versiones siguientes (`V2__...`, `V3__...`) se
escriben a mano. El target se niega a ejecutarse si ya existe `V1__init.sql`: regenerarlo cambiaría su
checksum y Flyway abortaría con `Migration checksum mismatch` allí donde ya estuviera aplicada.

#[[###]]# `make db-info`

Muestra el estado de las migraciones aplicadas, leyéndolo del endpoint `/actuator/flyway` de la aplicación
**arrancada**.

```shell
make db-info
```

#[[###]]# Migraciones nuevas

Se crean a mano siguiendo la convención de Flyway `V<version>__<descripcion>.sql` (**dos** guiones bajos):

```
app/src/main/resources/db/migration/
├── V1__init.sql
├── V2__add_indice_nombre.sql
└── V3__tabla_pedidos.sql
```

**Una migración ya aplicada no se edita nunca**, ni para arreglar un typo: se escribe la siguiente. El
checksum existe justamente para impedirlo.

Los **datos de prueba no van por Flyway** (acabarían aplicándose en producción): van por un runner del
perfil `dev`. Flyway es para estructura.

#[[##]]# Seguridad

#[[###]]# `make token`

Acuña un JWT de desarrollo, firmado con el secreto local del perfil `dev`.

```shell
curl -H "Authorization: Bearer $(make token | head -1)" http://localhost:8080/api/v1/examples
```

La seguridad está **siempre activa**, también en local: lo que cambia entre entornos no son las reglas,
es de dónde salen los tokens. Así no hay fallos de autorización que aparezcan por primera vez al
desplegar.

#[[##]]# Contrato de la API

#[[###]]# `make openapi`

Regenera `contract/src/main/resources/openapi/openapi.json` desde el código y acepta el cambio.

Un test compara el contrato generado con el versionado en cada build: si la API cambia sin actualizar el
fichero, el build se pone rojo. No es para impedir cambiar la API, es para que **ningún cambio de
contrato pase inadvertido** — renombrar un campo de un DTO rompe a todos los clientes.

#[[###]]# `make api-fuzz`

Contrasta la API **arrancada** con su propio contrato usando Schemathesis: genera casos desde el esquema
y comprueba que no hay 500 y que las respuestas casan con lo documentado.

```shell
make run        # en otra terminal
make api-fuzz
```

Encuentra lo que no se te ocurrió probar. No sustituye a los tests: no dice nada de la lógica de negocio.

#[[##]]# Calidad

#[[###]]# `make coverage`

Informe de cobertura en `app/target/site/jacoco/index.html`.

No hay umbral que rompa el build a propósito: la cobertura sirve para **ver qué no está probado**, no como
nota que aprobar.

#[[##]]# Comandos de Dependencias

#[[###]]# `make deps-tree`

Muestra el árbol completo de dependencias del proyecto.

```bash
make deps-tree
```

#[[###]]# `make deps-updates`

Verifica si hay actualizaciones disponibles para las dependencias.

```bash
make deps-updates
```

---

#[[##]]# Comandos de Calidad de Código

#[[###]]# `make fmt`

Formatea el código según las convenciones de Spring.

```bash
make fmt
```

#[[###]]# `make check`

Valida que el código cumpla con las reglas de formato.

```bash
make check
```

---

#[[##]]# Comandos de Monitoreo

Estos comandos requieren que la aplicación esté ejecutándose con Actuator habilitado.

#[[###]]# `make actuator-health`

Verifica el estado de salud de la aplicación.

```bash
make actuator-health
```

#[[###]]# `make actuator-info`

Muestra información de la aplicación.

```bash
make actuator-info
```

#[[###]]# `make actuator-metrics`

Lista todas las métricas disponibles.

```bash
make actuator-metrics
```

---

#[[##]]# Workflows Rápidos

#[[###]]# `make dev`

Workflow completo de desarrollo: levanta Docker y ejecuta la aplicación.

```bash
make dev
```

Equivalente a:
```bash
make docker-up
make run-dev
```

#[[###]]# `make rebuild`

Limpia y reconstruye el proyecto.

```bash
make rebuild
```

#[[###]]# `make fresh-start`

Reinicio completo: limpia todo, reinicia Docker, instala y ejecuta.

```bash
make fresh-start
```

---

#[[##]]# Variables de Configuración

Puedes personalizar el comportamiento del Makefile usando variables:

#[[###]]# `SPRING_PROFILE`

Define el perfil de Spring a utilizar (default: `dev`).

```bash
make run SPRING_PROFILE=test
```

#[[###]]# `DOCKER_IMAGE_NAME`

Nombre de la imagen Docker (default: nombre del proyecto).

```bash
make docker-build DOCKER_IMAGE_NAME=mi-aplicacion
```

#[[###]]# `DOCKER_IMAGE_TAG`

Tag de la imagen Docker (default: `latest`).

```bash
make docker-build DOCKER_IMAGE_TAG=v1.0.0
```

---

#[[##]]# Ejemplos de Uso

#[[###]]# Desarrollo diario típico

```bash
# Iniciar el día
make dev

# Después de hacer cambios
make test

# Verificar salud de la aplicación
make actuator-health

# Ver logs de Docker
make logs

# Detener al final del día
make stop
make docker-down
```

#[[###]]# Crear una nueva migración de base de datos
```bash
# 1. Modificar las entidades JPA

# 2. Escribir A MANO la migracion que refleja el cambio
#    app/src/main/resources/db/migration/V2__add_campo_email.sql

# 3. Arrancar: Flyway la aplica y Hibernate valida que entidades y esquema cuadran
make run
```

⚠️ No hay "generar diff": eso solo existe en la primera version (`make db-baseline`). A partir de ahi el
detector de deriva es que la aplicacion NO ARRANCA si la migracion falta.

#[[###]]# Preparar un release

```bash
# Limpiar y verificar
make clean
make verify

# Crear package
make package

# Construir imagen Docker con tag específico
make docker-build DOCKER_IMAGE_TAG=v2.1.0
```

#[[###]]# Troubleshooting

```bash
# Reiniciar todo desde cero
make fresh-start

# Verificar dependencias
make deps-tree

# Ver actualizaciones disponibles
make deps-updates

# Reiniciar servicios Docker
make docker-restart
```

#[[###]]# Testing con diferentes perfiles

```bash
# Test con perfil de test
make run SPRING_PROFILE=test

# Ejecutar tests
make test

# Verificar con perfil específico
SPRING_PROFILES_ACTIVE=test make verify
```

#[[###]]# CI/CD

```bash
# Build en CI
make ci-build

# Package en CI (sin tests)
make ci-package
```

---

#[[##]]# Notas Adicionales

- El Makefile utiliza el Maven Wrapper (`./mvnw`) incluido en el proyecto
- Los servicios Docker se definen en `compose-app.yml`
- Las migraciones las gobierna Flyway (`app/src/main/resources/db/migration/`) y se aplican al arrancar
- Los colores en la salida ayudan a identificar el estado de las operaciones

#[[##]]# Soporte

Para más información sobre comandos específicos, ejecuta:

```bash
make help
```

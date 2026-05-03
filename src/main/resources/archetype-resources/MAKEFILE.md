# Makefile - Guía de Uso

Esta guía documenta el uso del Makefile incluido en el proyecto Spring Boot para facilitar el desarrollo y las operaciones comunes.

## Tabla de Contenidos

- [Requisitos Previos](#requisitos-previos)
- [Inicio Rápido](#inicio-rápido)
- [Comandos de Desarrollo](#comandos-de-desarrollo)
- [Comandos de Build](#comandos-de-build)
- [Comandos Docker](#comandos-docker)
- [Comandos Liquibase](#comandos-liquibase)
- [Comandos de Dependencias](#comandos-de-dependencias)
- [Comandos de Calidad de Código](#comandos-de-calidad-de-código)
- [Comandos de Monitoreo](#comandos-de-monitoreo)
- [Workflows Rápidos](#workflows-rápidos)
- [Variables de Configuración](#variables-de-configuración)
- [Ejemplos de Uso](#ejemplos-de-uso)

## Requisitos Previos

- **Make**: Instalado en tu sistema
- **Docker**: Para ejecutar servicios de base de datos
- **Maven**: Incluido vía Maven Wrapper (`./mvnw`)
- **Java 25**: Según configuración del proyecto

## Inicio Rápido

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

## Comandos de Desarrollo

### `make run`

Ejecuta la aplicación con el perfil de desarrollo (dev).

```bash
make run
```

Automáticamente levanta los servicios Docker (PostgreSQL, Redis) antes de iniciar la aplicación.

### `make run-dev`

Ejecuta explícitamente la aplicación con el perfil dev.

```bash
make run-dev
```

### `make run-prod`

Ejecuta la aplicación con el perfil de producción.

```bash
make run-prod
```

### `make test`

Ejecuta todos los tests del proyecto.

```bash
make test
```

### `make verify`

Ejecuta tests y verifica que el build sea correcto.

```bash
make verify
```

### `make stop`

Detiene la aplicación en ejecución.

```bash
make stop
```

---

## Comandos de Build

### `make clean`

Limpia todos los artefactos de compilación.

```bash
make clean
```

### `make build`

Limpia y compila el proyecto.

```bash
make build
```

### `make package`

Crea el archivo JAR/WAR del proyecto (salta los tests).

```bash
make package
```

El artefacto generado se encuentra en `target/`.

### `make install`

Instala el proyecto en el repositorio Maven local.

```bash
make install
```

---

## Comandos Docker

### `make docker-up`

Inicia los servicios Docker definidos en `compose-app.yml`:
- PostgreSQL (puerto 5432)
- Redis (puerto 6379)

```bash
make docker-up
```

### `make docker-down`

Detiene todos los servicios Docker.

```bash
make docker-down
```

### `make docker-restart`

Reinicia todos los servicios Docker.

```bash
make docker-restart
```

### `make docker-build`

Construye la imagen Docker de la aplicación.

```bash
make docker-build
```

Por defecto usa el nombre del proyecto y el tag `latest`.

### `make docker-run`

Ejecuta la aplicación en un contenedor Docker.

```bash
make docker-run
```

### `make logs`

Muestra los logs de los servicios Docker en tiempo real.

```bash
make logs
```

Presiona `Ctrl+C` para salir.

---

## Comandos Liquibase

### `make liquibase-diff`

Genera un changelog de diferencias entre la base de datos actual y el modelo de Hibernate.

```bash
make liquibase-diff
```

Utiliza internamente el script `liquibase.bash` (opción 2).

### `make liquibase-changelog`

Genera el changelog inicial de la base de datos.

```bash
make liquibase-changelog
```

Utiliza internamente el script `liquibase.bash` (opción 1).

### `make liquibase-sql`

Genera el archivo SQL con las migraciones pendientes.

```bash
make liquibase-sql
```

El archivo se genera en `target/liquibase/migrate.sql`.

### `make liquibase-update`

Aplica las migraciones pendientes a la base de datos.

```bash
make liquibase-update
```

---

## Comandos de Dependencias

### `make deps-tree`

Muestra el árbol completo de dependencias del proyecto.

```bash
make deps-tree
```

### `make deps-updates`

Verifica si hay actualizaciones disponibles para las dependencias.

```bash
make deps-updates
```

---

## Comandos de Calidad de Código

### `make fmt`

Formatea el código según las convenciones de Spring.

```bash
make fmt
```

### `make check`

Valida que el código cumpla con las reglas de formato.

```bash
make check
```

---

## Comandos de Monitoreo

Estos comandos requieren que la aplicación esté ejecutándose con Actuator habilitado.

### `make actuator-health`

Verifica el estado de salud de la aplicación.

```bash
make actuator-health
```

### `make actuator-info`

Muestra información de la aplicación.

```bash
make actuator-info
```

### `make actuator-metrics`

Lista todas las métricas disponibles.

```bash
make actuator-metrics
```

---

## Workflows Rápidos

### `make dev`

Workflow completo de desarrollo: levanta Docker y ejecuta la aplicación.

```bash
make dev
```

Equivalente a:
```bash
make docker-up
make run-dev
```

### `make rebuild`

Limpia y reconstruye el proyecto.

```bash
make rebuild
```

### `make fresh-start`

Reinicio completo: limpia todo, reinicia Docker, instala y ejecuta.

```bash
make fresh-start
```

---

## Variables de Configuración

Puedes personalizar el comportamiento del Makefile usando variables:

### `SPRING_PROFILE`

Define el perfil de Spring a utilizar (default: `dev`).

```bash
make run SPRING_PROFILE=test
```

### `DOCKER_IMAGE_NAME`

Nombre de la imagen Docker (default: nombre del proyecto).

```bash
make docker-build DOCKER_IMAGE_NAME=mi-aplicacion
```

### `DOCKER_IMAGE_TAG`

Tag de la imagen Docker (default: `latest`).

```bash
make docker-build DOCKER_IMAGE_TAG=v1.0.0
```

---

## Ejemplos de Uso

### Desarrollo diario típico

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

### Crear una nueva migración de base de datos

```bash
# 1. Modificar entidades JPA

# 2. Generar diff
make liquibase-diff

# 3. Revisar el changelog generado en target/liquibase/

# 4. Aplicar migración
make liquibase-update
```

### Preparar un release

```bash
# Limpiar y verificar
make clean
make verify

# Crear package
make package

# Construir imagen Docker con tag específico
make docker-build DOCKER_IMAGE_TAG=v2.1.0
```

### Troubleshooting

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

### Testing con diferentes perfiles

```bash
# Test con perfil de test
make run SPRING_PROFILE=test

# Ejecutar tests
make test

# Verificar con perfil específico
SPRING_PROFILES_ACTIVE=test make verify
```

### CI/CD

```bash
# Build en CI
make ci-build

# Package en CI (sin tests)
make ci-package
```

---

## Notas Adicionales

- El Makefile utiliza el Maven Wrapper (`./mvnw`) incluido en el proyecto
- Los servicios Docker se definen en `compose-app.yml`
- Las migraciones de Liquibase usan el script `liquibase.bash`
- Los colores en la salida ayudan a identificar el estado de las operaciones

## Soporte

Para más información sobre comandos específicos, ejecuta:

```bash
make help
```

.PHONY: help clean build test run run-dev run-prod stop docker-up docker-down docker-restart \
        db-baseline db-info token openapi api-fuzz coverage package install \
        deps-tree deps-updates verify fmt check docker-build docker-run logs actuator-health

# Colors for output
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

# Project configuration
PROJECT_NAME := ${artifactId}
SPRING_PROFILE ?= dev
# En MINUSCULAS, y no por gusto: Docker rechaza los nombres de repositorio con mayusculas
# ("repository name must be lowercase"). Con un artifactId en camelCase, `make docker-build` fallaba
# sin mas explicacion que esa. Es el mismo problema que en el chart de Helm, donde los nombres tienen
# que cumplir RFC 1123.
DOCKER_IMAGE_NAME ?= $(shell echo '$(PROJECT_NAME)' | tr '[:upper:]' '[:lower:]')
DOCKER_IMAGE_TAG ?= latest

# Maven options
MAVEN_OPTS ?= -Dmaven.test.skip=false
MVN := ./mvnw

# Docker compose files
COMPOSE_FILE := compose.yaml

help:
	@echo "$(GREEN)╔══════════════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(GREEN)║         Spring Boot Development Makefile - $(PROJECT_NAME)       $(NC)"
	@echo "$(GREEN)╚══════════════════════════════════════════════════════════════════╝$(NC)"
	@echo ""
	@echo "$(YELLOW)🚀 Development Commands:$(NC)"
	@echo "  make run              Run application with dev profile"
	@echo "  make run-dev          Run application with dev profile (explicit)"
	@echo "  make run-prod         Run application with prod profile"
	@echo "  make test             Run all tests"
	@echo "  make verify           Run tests and verify build"
	@echo ""
	@echo "$(YELLOW)🔨 Build Commands:$(NC)"
	@echo "  make build            Clean and compile"
	@echo "  make package          Create JAR/WAR package"
	@echo "  make install          Install to local Maven repository"
	@echo "  make clean            Clean build artifacts"
	@echo ""
	@echo "$(YELLOW)🐳 Docker Commands:$(NC)"
	@echo "  make docker-up        Start Docker services (PostgreSQL, Redis)"
	@echo "  make docker-down      Stop Docker services"
	@echo "  make docker-restart   Restart Docker services"
	@echo "  make docker-build     Build Docker image"
	@echo "  make docker-run       Run application in Docker"
	@echo "  make logs             Show Docker compose logs"
	@echo ""
	@echo "$(YELLOW)💾 Database Commands (Flyway):$(NC)"
	@echo "  make db-baseline      Genera V1__init.sql desde las entidades (SOLO la 1a vez)"
	@echo "  make db-info          Estado de las migraciones (app arrancada)"
	@echo ""
	@echo "$(YELLOW)🔐 Seguridad:$(NC)"
	@echo "  make token            Acuña un JWT de desarrollo (perfil dev)"
	@echo ""
	@echo "$(YELLOW)📄 Contrato:$(NC)"
	@echo "  make openapi          Regenera el contrato OpenAPI desde el codigo"
	@echo "  make api-fuzz         Prueba la API real CONTRA su contrato (Schemathesis)"
	@echo ""
	@echo "$(YELLOW)🔍 Calidad:$(NC)"
	@echo "  make coverage         Informe de cobertura (JaCoCo)"
	@echo ""
	@echo "$(YELLOW)📦 Dependencies Commands:$(NC)"
	@echo "  make deps-tree        Show dependency tree"
	@echo "  make deps-updates     Check for dependency updates"
	@echo ""
	@echo "$(YELLOW)🔍 Code Quality & Monitoring:$(NC)"
	@echo "  make fmt              Format code"
	@echo "  make check            Run code checks"
	@echo "  make actuator-health  Check application health"
	@echo ""
	@echo "$(YELLOW)🛠️  Utility Commands:$(NC)"
	@echo "  make stop             Stop running application"
	@echo "  make help             Show this help message"
	@echo ""
	@echo "$(YELLOW)Variables:$(NC)"
	@echo "  SPRING_PROFILE        Spring profile (default: dev)"
	@echo "  DOCKER_IMAGE_NAME     Docker image name (default: $(PROJECT_NAME))"
	@echo "  DOCKER_IMAGE_TAG      Docker image tag (default: latest)"
	@echo ""
	@echo "$(YELLOW)Examples:$(NC)"
	@echo "  make run SPRING_PROFILE=test"
	@echo "  make docker-build DOCKER_IMAGE_TAG=v1.0.0"

# ============================================
# Development Commands
# ============================================

# Sin `docker-up` delante: en el perfil dev, Spring Boot levanta compose.yaml el solo y espera a que
# los servicios esten sanos (spring.docker.compose en application.yaml). Tenerlo aqui TAMBIEN eran dos
# mecanismos para lo mismo, y dos mecanismos para lo mismo acaban desincronizados.
# `make docker-up` sigue disponible para levantar solo los servicios, sin la aplicacion.
run:
	@echo "$(GREEN)🚀 Running application with profile: $(SPRING_PROFILE)$(NC)"
	$(MVN) -pl app spring-boot:run -Dspring-boot.run.profiles=$(SPRING_PROFILE)

run-dev:
	@$(MAKE) run SPRING_PROFILE=dev

run-prod:
	@$(MAKE) run SPRING_PROFILE=prod

stop:
	@echo "$(YELLOW)🛑 Stopping application...$(NC)"
	@pkill -f "spring-boot:run" || true
	@echo "$(GREEN)✓ Application stopped$(NC)"

# ============================================
# Build Commands
# ============================================

clean:
	@echo "$(YELLOW)🧹 Cleaning build artifacts...$(NC)"
	$(MVN) clean
	@echo "$(GREEN)✓ Clean completed$(NC)"

build: clean
	@echo "$(GREEN)🔨 Building project...$(NC)"
	$(MVN) compile
	@echo "$(GREEN)✓ Build completed$(NC)"

package:
	@echo "$(GREEN)📦 Packaging application...$(NC)"
	$(MVN) clean package -DskipTests
	@echo "$(GREEN)✓ Package created in target/$(NC)"

install:
	@echo "$(GREEN)📥 Installing to local Maven repository...$(NC)"
	$(MVN) clean install
	@echo "$(GREEN)✓ Installed successfully$(NC)"

test:
	@echo "$(GREEN)🧪 Running tests...$(NC)"
	$(MVN) test
	@echo "$(GREEN)✓ Tests completed$(NC)"

verify:
	@echo "$(GREEN)✅ Verifying build...$(NC)"
	$(MVN) verify
	@echo "$(GREEN)✓ Verification completed$(NC)"

# ============================================
# Docker Commands
# ============================================

docker-up:
	@echo "$(GREEN)🐳 Starting Docker services...$(NC)"
	docker compose -f $(COMPOSE_FILE) up -d
	@echo "$(GREEN)✓ Docker services started$(NC)"
	@echo "$(YELLOW)   PostgreSQL: localhost:5432$(NC)"
	@echo "$(YELLOW)   Redis: localhost:6379$(NC)"

docker-down:
	@echo "$(YELLOW)🐳 Stopping Docker services...$(NC)"
	docker compose -f $(COMPOSE_FILE) down
	@echo "$(GREEN)✓ Docker services stopped$(NC)"

docker-restart:
	@$(MAKE) docker-down
	@$(MAKE) docker-up

docker-build: package
	@echo "$(GREEN)🐳 Building Docker image...$(NC)"
	docker build -t $(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG) .
	@echo "$(GREEN)✓ Docker image built: $(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG)$(NC)"

docker-run:
	@echo "$(GREEN)🐳 Running application in Docker...$(NC)"
	docker run -p 8080:8080 --name $(PROJECT_NAME) \
		-e SPRING_PROFILES_ACTIVE=$(SPRING_PROFILE) \
		$(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG)

logs:
	@echo "$(GREEN)📋 Docker compose logs (Ctrl+C to exit)...$(NC)"
	docker compose -f $(COMPOSE_FILE) logs -f

# ============================================
# Database Commands (Flyway)
# ============================================
# El esquema lo maneja Flyway. Ver docs/migraciones.md del arquetipo.

db-baseline:
	@echo "$(GREEN)💾 Generando V1__init.sql desde las entidades...$(NC)"
	@if [ -f app/src/main/resources/db/migration/V1__init.sql ]; then \
		echo "$(RED)✗ Ya existe V1__init.sql. Regenerarlo cambia su checksum y Flyway abortaria"; \
		echo "  con 'Migration checksum mismatch' donde ya estuviera aplicada."; \
		echo "  A partir de la primera version, las migraciones se escriben a mano (V2, V3...).$(NC)"; \
		exit 1; \
	fi
	$(MVN) -pl app -am -q -DskipTests compile
	$(MVN) -pl app spring-boot:run -Dspring-boot.run.profiles=ddl-dump
	@echo "$(YELLOW)→ Revisa app/target/generated-schema/V1__init.sql ANTES de moverlo:$(NC)"
	@echo "  Hibernate nombra indices y constraints como le parece y no pone comentarios."
	@echo "$(YELLOW)→ Cuando te convenza:$(NC)"
	@echo "  mv app/target/generated-schema/V1__init.sql app/src/main/resources/db/migration/"

# No hay target para "aplicar migraciones": las aplica Spring Boot AL ARRANCAR. Un comando aparte se
# desincroniza del arranque y acaba mintiendo. Para ver el estado, con la app levantada:
db-info:
	@echo "$(GREEN)💾 Estado de las migraciones (requiere la app arrancada):$(NC)"
	@curl -fsS http://localhost:8080/actuator/flyway | jq '.contexts[].flywayBeans.flyway.migrations[] | {version, description, state}' 2>/dev/null \
		|| echo "$(RED)✗ No responde. ¿Esta la app arrancada? (make run)$(NC)"

# ============================================
# Dependencies Commands
# ============================================

deps-tree:
	@echo "$(GREEN)📦 Dependency tree:$(NC)"
	$(MVN) dependency:tree

deps-updates:
	@echo "$(GREEN)📦 Checking for dependency updates...$(NC)"
	$(MVN) versions:display-dependency-updates

# ============================================
# Code Quality Commands
# ============================================

fmt:
	@echo "$(GREEN)✨ Formatting code...$(NC)"
	$(MVN) spring-javaformat:apply
	@echo "$(GREEN)✓ Code formatted$(NC)"

check:
	@echo "$(GREEN)🔍 Running code checks...$(NC)"
	$(MVN) spring-javaformat:validate
	@echo "$(GREEN)✓ Code checks passed$(NC)"

# ============================================
# Monitoring Commands
# ============================================

actuator-health:
	@echo "$(GREEN)🏥 Checking application health...$(NC)"
	@curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && \
		echo "$(GREEN)✓ Application is healthy$(NC)" || \
		echo "$(RED)✗ Application is down or unhealthy$(NC)"

actuator-info:
	@echo "$(GREEN)ℹ️  Application info:$(NC)"
	@curl -s http://localhost:8080/actuator/info | jq .

actuator-metrics:
	@echo "$(GREEN)📊 Available metrics:$(NC)"
	@curl -s http://localhost:8080/actuator/metrics | jq .

# ============================================
# Quick Development Workflows
# ============================================

dev: run-dev

rebuild: clean build

fresh-start: clean docker-restart install run

# ============================================
# CI/CD Commands
# ============================================

ci-build:
	@echo "$(GREEN)🔄 CI Build...$(NC)"
	$(MVN) clean verify -Ptest

ci-package:
	@echo "$(GREEN)📦 CI Package...$(NC)"
	$(MVN) clean package -DskipTests

# ============================================
# Default target
# ============================================

.DEFAULT_GOAL := help

# ============================================
# Seguridad
# ============================================
# La cadena de filtros es la MISMA en local y en produccion; lo unico que cambia es el emisor de los
# tokens. En dev, el emisor es un secreto local (configs/DevJwtConfig), y este target acuña un token
# firmado con el. Asi se trabaja autenticado sin levantar ningun servidor de identidad, y sin apagar la
# seguridad — que es lo que haria que los fallos de autorizacion aparecieran por primera vez al desplegar.
#
# Un JWT HS256 no es mas que base64url(cabecera).base64url(cuerpo).base64url(HMAC-SHA256), asi que no
# hace falta ninguna herramienta extra: openssl basta.
token:
	@secret=$$(grep -m1 -E '^[[:space:]]*jwt-secret:' app/src/main/resources/application.yaml | sed -E 's/.*jwt-secret:[[:space:]]*//'); \
	if [ -z "$$secret" ]; then echo "$(RED)✗ no encuentro app.dev.jwt-secret en application.yaml$(NC)"; exit 1; fi; \
	b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }; \
	header=$$(printf '{"alg":"HS256","typ":"JWT"}' | b64); \
	now=$$(date +%s); exp=$$((now + 3600)); \
	payload=$$(printf '{"sub":"dev-user","scope":"read write","iat":%s,"exp":%s}' "$$now" "$$exp" | b64); \
	sig=$$(printf '%s.%s' "$$header" "$$payload" | openssl dgst -sha256 -hmac "$$secret" -binary | b64); \
	tok="$$header.$$payload.$$sig"; \
	echo "$$tok"; \
	echo ""; \
	echo "$(YELLOW)Valido 1 hora. Uso:$(NC)"; \
	echo "  curl -H \"Authorization: Bearer $$tok\" http://localhost:8080/api/v1/examples"

# ============================================
# Contrato OpenAPI
# ============================================
# El contrato se genera DESDE EL CODIGO (code-first) y vive versionado en
# contract/src/main/resources/openapi/openapi.json. Un test lo compara en cada build: si la API cambia
# sin actualizar el fichero, el build se pone rojo. No es para impedir cambiar la API, es para que
# ningun cambio de contrato pase inadvertido — renombrar un campo de un DTO rompe a todos los clientes.
#
# Este target acepta el cambio. El openapi.json resultante se commitea JUNTO al cambio de codigo, que es
# lo que hace visible en la revision que la API se ha movido.
openapi:
	@echo "$(GREEN)📄 Regenerando el contrato OpenAPI desde el codigo...$(NC)"
	$(MVN) -pl app -am verify -Dopenapi.update=true
	@echo "$(GREEN)✓ contract/src/main/resources/openapi/openapi.json actualizado$(NC)"
	@echo "$(YELLOW)→ Revisa el diff y commitealo junto al cambio de codigo.$(NC)"

# ============================================
# Contraste de la API con su contrato
# ============================================
# Schemathesis lee el openapi.json y GENERA casos de prueba a partir del esquema: tipos, formatos,
# limites, campos obligatorios, enums. Despues comprueba propiedades que deben cumplirse siempre —que no
# haya 500, que la respuesta case con lo documentado, que la API no viole su propio contrato.
#
# Por que aporta algo que los tests no dan: nuestros tests comprueban los casos que se nos ocurrieron.
# Esto explora los que NO se nos ocurrieron, y son justo los que llegan a produccion.
#
# ⚠️ Complementa, no sustituye. Prueba que la API no se rompe ni miente; NO prueba que la logica de
# negocio sea correcta. Para eso estan los tests de siempre.
#
# Se lanza contra la API ARRANCADA (make run en otra terminal). En CI corre solo, en su propio job.
api-fuzz:
	@echo "$(GREEN)🎲 Contrastando la API con su contrato...$(NC)"
	@curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1 \
		|| { echo "$(RED)✗ La API no responde en el 8080. Arrancala antes: make run$(NC)"; exit 1; }
	@# La API exige autenticacion, asi que hay que darle un token: sin el, Schemathesis solo recibiria
	@# 401 en todo y no ejercitaria nada — pasaria en verde sin haber probado la API.
	@tok=$$($(MAKE) --no-print-directory token | head -1); \
	docker run --rm --network=host schemathesis/schemathesis:stable run \
		http://localhost:8080/v3/api-docs \
		--header "Authorization: Bearer $$tok" \
		--checks all \
		--max-examples 50
	@echo "$(GREEN)✓ La API concuerda con su contrato$(NC)"

coverage:
	@echo "$(GREEN)📊 Generando informe de cobertura...$(NC)"
	$(MVN) verify
	@echo "$(GREEN)✓ app/target/site/jacoco/index.html$(NC)"

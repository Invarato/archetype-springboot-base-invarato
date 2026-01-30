.PHONY: help clean build test run run-dev run-prod stop docker-up docker-down docker-restart \
        liquibase-diff liquibase-changelog liquibase-sql liquibase-update package install \
        deps-tree deps-updates verify fmt check docker-build docker-run logs actuator-health

# Colors for output
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

# Project configuration
PROJECT_NAME := ${artifactId}
SPRING_PROFILE ?= dev
DOCKER_IMAGE_NAME ?= $(PROJECT_NAME)
DOCKER_IMAGE_TAG ?= latest

# Maven options
MAVEN_OPTS ?= -Dmaven.test.skip=false
MVN := ./mvnw

# Docker compose files
COMPOSE_FILE := compose-app.yml

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
	@echo "$(YELLOW)💾 Database/Liquibase Commands:$(NC)"
	@echo "  make liquibase-diff      Generate diff changelog"
	@echo "  make liquibase-changelog Generate initial changelog"
	@echo "  make liquibase-sql       Generate migration SQL"
	@echo "  make liquibase-update    Update database"
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

run: docker-up
	@echo "$(GREEN)🚀 Running application with profile: $(SPRING_PROFILE)$(NC)"
	$(MVN) spring-boot:run -Dspring-boot.run.profiles=$(SPRING_PROFILE)

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
# Database/Liquibase Commands
# ============================================

liquibase-diff:
	@echo "$(GREEN)💾 Running Liquibase diff...$(NC)"
	@bash liquibase.bash <<< "2"
	@echo "$(GREEN)✓ Diff completed$(NC)"

liquibase-changelog:
	@echo "$(GREEN)💾 Generating Liquibase changelog...$(NC)"
	@bash liquibase.bash <<< "1"
	@echo "$(GREEN)✓ Changelog generated$(NC)"

liquibase-sql:
	@echo "$(GREEN)💾 Generating migration SQL...$(NC)"
	@bash liquibase.bash <<< "3"
	@echo "$(GREEN)✓ SQL generated in target/liquibase/migrate.sql$(NC)"

liquibase-update:
	@echo "$(GREEN)💾 Updating database...$(NC)"
	$(MVN) liquibase:update
	@echo "$(GREEN)✓ Database updated$(NC)"

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

dev: docker-up run-dev

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

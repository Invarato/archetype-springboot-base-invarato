.PHONY: help clean build generate rebuild check check-post check-image test-generated verify publish all

# ══════════════════════════════════════════════════════════════════════════════════════════════
# Flujo de trabajo del ARQUETIPO.
#
# ⚠️ Lo que hay que tener claro: `mvn install` sobre este repo da BUILD SUCCESS aunque el arquetipo
# produzca un proyecto que no compila — solo empaqueta ficheros de texto, no los compila. El unico
# verde que cuenta es el del PROYECTO GENERADO, y de eso se encarga `make verify`.
#
#   make rebuild   → clean + build + generate            (ciclo rapido mientras editas)
#   make verify    → rebuild + comprobaciones + mvn verify del generado   ← EL GATE
# ══════════════════════════════════════════════════════════════════════════════════════════════

SHELL := /bin/bash
# Java degrada a ASCII al leer argumentos si no hay locale, y los acentos acaban como '??'.
export LC_ALL := C.UTF-8

GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m

POM_XML_PATH := pom.xml
M2_REPOSITORY_PATH := $(HOME)/.m2/repository

# Del pom del arquetipo. `grep -m1` vale porque este pom NO tiene <parent>: el primer <groupId> y el
# primer <version> son los del proyecto. Si algun dia se le anade un padre, esto habria que cambiarlo.
ARCHETYPE_GROUP_ID := $(shell grep -m1 "<groupId>" $(POM_XML_PATH) | sed -E 's/.*<groupId>([^<]+)<\/groupId>.*/\1/')
ARCHETYPE_ARTIFACT_ID := $(shell grep -m1 "<artifactId>" $(POM_XML_PATH) | sed -E 's/.*<artifactId>([^<]+)<\/artifactId>.*/\1/')
# ⚠️ Se lee la propiedad <revision>, NO el <version>: desde que la version es CI-friendly, el <version>
# del pom es el literal `${revision}` y un grep sobre el devolvia eso mismo.
DEFAULT_ARCHETYPE_VERSION := $(shell grep -m1 "<revision>" $(POM_XML_PATH) | sed -E 's/.*<revision>([^<]+)<\/revision>.*/\1/')

ARCHETYPE_VERSION ?= $(DEFAULT_ARCHETYPE_VERSION)
ARTIFACT_ID ?= projectTestForAnalysis
GROUP_ID ?= com.jarroba.prueba

# El proyecto de prueba se genera FUERA del repo (por defecto, en el directorio padre) para que no
# ensucie el arbol ni el import del IDE. Se puede fijar con OUT_DIR, que es lo que hace el CI.
OUT_DIR ?= $(shell dirname $(CURDIR))
GEN_DIR := $(OUT_DIR)/$(ARTIFACT_ID)

MVN ?= mvn

help:
	@echo -e "$(GREEN)Arquetipo $(ARCHETYPE_ARTIFACT_ID) — $(ARCHETYPE_VERSION)$(NC)"
	@echo ""
	@echo -e "$(YELLOW)Ciclo normal:$(NC)"
	@echo "  make rebuild     clean + build + generate (rapido, mientras editas)"
	@echo -e "  make verify      $(GREEN)EL GATE$(NC): rebuild + comprobaciones + mvn verify del generado"
	@echo ""
	@echo -e "$(YELLOW)Por partes:$(NC)"
	@echo "  make build           instala el arquetipo en ~/.m2"
	@echo "  make generate        genera el proyecto de prueba"
	@echo "  make check           comprobaciones estructurales sobre lo generado (sin compilar)"
	@echo "  make test-generated  mvn verify sobre el proyecto generado (necesita Docker)"
	@echo "  make clean           borra el arquetipo de ~/.m2 y el proyecto generado"
	@echo ""
	@echo -e "$(YELLOW)Publicacion:$(NC)"
	@echo "  make publish         firma y publica en Maven Central (pide GPG y credenciales)"
	@echo ""
	@echo -e "$(YELLOW)Variables:$(NC)"
	@echo "  ARCHETYPE_VERSION  version del arquetipo   (por defecto: $(DEFAULT_ARCHETYPE_VERSION))"
	@echo "  ARTIFACT_ID        proyecto de prueba      (por defecto: $(ARTIFACT_ID))"
	@echo "  GROUP_ID           grupo del generado      (por defecto: $(GROUP_ID))"
	@echo "  OUT_DIR            donde se genera         (por defecto: $(OUT_DIR))"
	@echo ""
	@echo -e "$(YELLOW)Ejemplos:$(NC)"
	@echo "  make verify"
	@echo "  make generate ARTIFACT_ID=miPrueba GROUP_ID=com.ejemplo"

clean:
	@echo -e "$(YELLOW)🧹 Limpiando...$(NC)"
	@repo="$(M2_REPOSITORY_PATH)/$(subst .,/,$(ARCHETYPE_GROUP_ID))/$(ARCHETYPE_ARTIFACT_ID)/$(ARCHETYPE_VERSION)"; \
	if [ -d "$$repo" ]; then echo "  ~/.m2: $$repo"; rm -rf "$$repo"; fi
	@# Guardarrail: solo se borra si de verdad parece el proyecto generado. Esta ruta sale de un
	@# `dirname`, y un OUT_DIR mal puesto podria apuntar a cualquier sitio.
	@if [ -n "$(ARTIFACT_ID)" ] && [ -d "$(GEN_DIR)" ]; then \
		if [ -f "$(GEN_DIR)/pom.xml" ] && [ -d "$(GEN_DIR)/app" ]; then \
			echo "  generado: $(GEN_DIR)"; rm -rf "$(GEN_DIR)"; \
		else \
			echo -e "  $(RED)ojo:$(NC) $(GEN_DIR) existe pero no parece un proyecto generado; NO se borra"; \
		fi; \
	fi
	@echo -e "$(GREEN)✓ limpio$(NC)"

build:
	@echo -e "$(GREEN)🔨 Instalando el arquetipo en ~/.m2...$(NC)"
	$(MVN) -B clean install -DskipTests -Dgpg.skip=true
	@echo -e "$(GREEN)✓ instalado$(NC)"

generate:
	@echo -e "$(GREEN)📦 Generando $(GROUP_ID):$(ARTIFACT_ID) en $(OUT_DIR)...$(NC)"
	@mkdir -p "$(OUT_DIR)"
	$(MVN) -B archetype:generate \
		-DarchetypeGroupId="$(ARCHETYPE_GROUP_ID)" \
		-DarchetypeArtifactId="$(ARCHETYPE_ARTIFACT_ID)" \
		-DarchetypeVersion="$(ARCHETYPE_VERSION)" \
		-DgroupId="$(GROUP_ID)" \
		-DartifactId="$(ARTIFACT_ID)" \
		-DinteractiveMode=false \
		-DoutputDirectory="$(OUT_DIR)"
	@echo -e "$(GREEN)✓ generado en $(GEN_DIR)$(NC)"

check:
	@./scripts/check-generated.sh "$(GEN_DIR)"

# Las comprobaciones que necesitan el proyecto YA construido (codigo generado en target/).
# Van aparte porque `check` corre ANTES del build: metidas alli se saltaban en silencio.
check-post:
	@CHECK_FASE=post ./scripts/check-generated.sh "$(GEN_DIR)"

# Construye la imagen del proyecto generado.
#
# ⚠️ Esto faltaba, y el Dockerfile llevaba semanas roto sin que nadie lo supiera: copiaba los pom.xml
# modulo a modulo y se quedo en dos cuando el proyecto paso a tener cuatro. `mvn verify` no lo ve
# —no construye imagenes— y la promesa del arquetipo incluye `make docker-build`.
check-image:
	@command -v docker >/dev/null 2>&1 || { echo -e "$(RED)✗ no hay docker: no se puede construir la imagen$(NC)"; exit 1; }
	@echo -e "$(GREEN)🐳 construyendo la imagen del proyecto generado...$(NC)"
	@cd "$(GEN_DIR)" && docker build -q -t "$(shell echo '$(ARTIFACT_ID)' | tr '[:upper:]' '[:lower:]'):gate" . >/dev/null
	@echo -e "$(GREEN)✓ la imagen se construye$(NC)"

test-generated:
	@echo -e "$(GREEN)🧪 mvn verify sobre el proyecto generado...$(NC)"
	@command -v docker >/dev/null 2>&1 || { echo -e "$(RED)✗ no hay docker: los *IT con Testcontainers no pueden correr$(NC)"; exit 1; }
	cd "$(GEN_DIR)" && $(MVN) -B verify
	@echo -e "$(GREEN)✓ el proyecto generado pasa sus tests$(NC)"

rebuild: clean build generate

# EL GATE. Es lo que debe estar en verde antes de dar por bueno cualquier cambio, y lo que corre el CI.
verify: rebuild check test-generated check-post check-image
	@echo ""
	@echo -e "$(GREEN)══════════════════════════════════════════════════════════$(NC)"
	@echo -e "$(GREEN)✓ El arquetipo genera un proyecto que compila y pasa sus tests$(NC)"
	@echo -e "$(GREEN)══════════════════════════════════════════════════════════$(NC)"

publish:
	@echo -e "$(YELLOW)⚠️  Vas a publicar $(ARCHETYPE_ARTIFACT_ID):$(ARCHETYPE_VERSION) en Maven Central.$(NC)"
	@echo -e "$(YELLOW)   Es irreversible: una version publicada no se puede borrar ni reemplazar.$(NC)"
	@read -p "   Escribe la version para confirmar: " v; \
	 [ "$$v" = "$(ARCHETYPE_VERSION)" ] || { echo -e "$(RED)✗ no coincide; abortado$(NC)"; exit 1; }
	$(MVN) -B clean install central-publishing:publish

all: verify

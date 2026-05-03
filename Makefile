.PHONY: help clean build generate rebuild all

# Default values
DEFAULT_ARTIFACT_ID := projectTestForAnalysis
M2_REPOSITORY_PATH := $(HOME)/.m2/repository
POM_XML_PATH := pom.xml

# Extract values from pom.xml
ARCHETYPE_GROUP_ID := $(shell grep -m1 "<groupId>" $(POM_XML_PATH) | sed -E 's/.*<groupId>([^<]+)<\/groupId>.*/\1/')
DEFAULT_ARCHETYPE_VERSION := $(shell grep -m1 "<version>" $(POM_XML_PATH) | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')
ARCHETYPE_ARTIFACT_ID := $(shell grep -m1 "<artifactId>" $(POM_XML_PATH) | sed -E 's/.*<artifactId>([^<]+)<\/artifactId>.*/\1/')

# Override with environment variables if provided
ARCHETYPE_VERSION ?= $(DEFAULT_ARCHETYPE_VERSION)
ARTIFACT_ID ?= $(DEFAULT_ARTIFACT_ID)
PARENT_DIR := $(shell dirname $(PWD))

help:
	@echo "Usage: make [target] [VARIABLE=value]"
	@echo ""
	@echo "Targets:"
	@echo "  help      Show this help message"
	@echo "  clean     Clean Maven repository and artifact directory"
	@echo "  build     Build the archetype (mvn clean install)"
	@echo "  generate  Generate a new project from the archetype"
	@echo "  rebuild   Clean, build and generate (default)"
	@echo "  all       Alias for rebuild"
	@echo ""
	@echo "Variables:"
	@echo "  ARCHETYPE_VERSION  Archetype version (default: $(DEFAULT_ARCHETYPE_VERSION))"
	@echo "  ARTIFACT_ID        Artifact ID (default: $(DEFAULT_ARTIFACT_ID))"
	@echo ""
	@echo "Examples:"
	@echo "  make"
	@echo "  make rebuild ARCHETYPE_VERSION=1.0 ARTIFACT_ID=MyArtifact"
	@echo "  make clean"
	@echo "  make build"
	@echo "  make generate ARTIFACT_ID=TestProject"

clean:
	@echo "Cleaning Maven repository and artifact directory..."
	@if [ -d "$(M2_REPOSITORY_PATH)/com/jarroba/$(ARCHETYPE_ARTIFACT_ID)/$(ARCHETYPE_VERSION)" ]; then \
		echo "Cleaning Maven repository: $(M2_REPOSITORY_PATH)/com/jarroba/$(ARCHETYPE_ARTIFACT_ID)/$(ARCHETYPE_VERSION)"; \
		rm -rf "$(M2_REPOSITORY_PATH)/com/jarroba/$(ARCHETYPE_ARTIFACT_ID)/$(ARCHETYPE_VERSION)"; \
	fi
	@if [ -d "$(PARENT_DIR)/$(ARTIFACT_ID)" ]; then \
		echo "Cleaning artifact directory: $(PARENT_DIR)/$(ARTIFACT_ID)"; \
		rm -rf "$(PARENT_DIR)/$(ARTIFACT_ID)"; \
	fi

build:
	@echo "Rebuilding archetype..."
	mvn clean install -DskipTests -Dgpg.skip=true

generate:
	@echo "Generating archetype project..."
	mvn -o -U archetype:generate -B \
		-DarchetypeGroupId="$(ARCHETYPE_GROUP_ID)" \
		-DarchetypeArtifactId="$(ARCHETYPE_ARTIFACT_ID)" \
		-DarchetypeVersion="$(ARCHETYPE_VERSION)" \
		-DgroupId=com.jarroba \
		-DartifactId="$(ARTIFACT_ID)" \
		-DoutputDirectory="$(PARENT_DIR)"
	@echo "Project generated in: $(PARENT_DIR)/$(ARTIFACT_ID)"

rebuild: clean build generate

all: rebuild

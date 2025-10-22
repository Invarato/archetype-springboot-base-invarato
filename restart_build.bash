#!/bin/bash


# Default values
readonly DEFAULT_ARTIFACT_ID="projectTestForAnalysis"
readonly M2_REPOSITORY_PATH="$HOME/.m2/repository"

# Extract version from pom.xml
POM_XML_PATH="$(dirname "$0")/pom.xml"
ARCHETYPE_GROUP_ID=$(grep -m1 "<groupId>" "$POM_XML_PATH" | sed -E 's/.*<groupId>([^<]+)<\/groupId>.*/\1/')
DEFAULT_ARCHETYPE_VERSION=$(grep -m1 "<version>" "$POM_XML_PATH" | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')
ARCHETYPE_ARTIFACT_ID=$(grep -m1 "<artifactId>" "$POM_XML_PATH" | sed -E 's/.*<artifactId>([^<]+)<\/artifactId>.*/\1/')


# Helper
print_help() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  -v, --version    Archetype version (default: $DEFAULT_ARCHETYPE_VERSION)"
    echo "  -a, --artifact   Artifact ID (default: $DEFAULT_ARTIFACT_ID)"
    echo "  -h, --help       Show this help message"
    echo
    echo "Example:"
    echo "  $0 -v 1.0 -a MyArtifact"
    exit 0
}

# Clean directories
clean_directories() {
    local archetype_version="$1"
    local artifact_id="$2"
    local parent_dir="$3"

    # Clean Maven repository
    local repo_path="$M2_REPOSITORY_PATH/com/jarroba/$ARCHETYPE_ARTIFACT_ID/$archetype_version"
    if [ -d "$repo_path" ]; then
        echo "Cleaning Maven repository: $repo_path"
        rm -rf "$repo_path"
    fi

    # Clean artifact directory
    local artifact_path="${parent_dir:?}/${artifact_id:?}"
    if [ -d "$artifact_path" ]; then
        echo "Cleaning artifact directory: $artifact_path"
        rm -rf "$artifact_path"
    fi
}

# Process arguments
ARCHETYPE_VERSION="$DEFAULT_ARCHETYPE_VERSION"
ARTIFACT_ID="$DEFAULT_ARTIFACT_ID"

while [ "$#" -gt 0 ]; do
    case "$1" in
        -v|--version)
            shift
            ARCHETYPE_VERSION="${1:-$DEFAULT_ARCHETYPE_VERSION}"
            ;;
        -a|--artifact)
            shift
            ARTIFACT_ID="${1:-$DEFAULT_ARTIFACT_ID}"
            ;;
        -h|--help)
            print_help
            ;;
        *)
            echo "Error: Unrecognized option: $1" >&2
            print_help
            exit 1
            ;;
    esac
    shift
done

# Parent directory
PARENT_DIR=$(dirname "$PWD")

# Clean directories
clean_directories "$ARCHETYPE_VERSION" "$ARTIFACT_ID" "$PARENT_DIR"

# Rebuild archetype
echo "Rebuilding archetype..."
mvn clean install -DskipTests -Dgpg.skip=true

# Generate new archetype project
echo "Generating archetype project..."
mvn -o -U archetype:generate -B \
    -DarchetypeGroupId="$ARCHETYPE_GROUP_ID" \
    -DarchetypeArtifactId="$ARCHETYPE_ARTIFACT_ID" \
    -DarchetypeVersion="$ARCHETYPE_VERSION" \
    -DgroupId=com.jarroba \
    -DartifactId="$ARTIFACT_ID" \
    -DoutputDirectory="$PARENT_DIR"

echo "Project generated in: $PARENT_DIR/$ARTIFACT_ID"

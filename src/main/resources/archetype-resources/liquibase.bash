#!/bin/bash

# Database Migration Management Script

# Description:
# This script provides an interactive tool for managing database migrations using Liquibase and PostgreSQL.
# It automates common database migration tasks such as generating changelogs, executing diffs,
# and creating migration SQL scripts.

# Prerequisites:
# - Docker
# - Maven
# - PostgreSQL container running locally

# Configuration:
# Database settings are defined in the DB_CONFIGS array at the top of the script
# Customize these values according to your project's requirements

# Usage:
# ./liquibase.sh

# Menu Options:
# 1. Generate Initial Changelog: Creates a comprehensive YAML file of the current database structure
# 2. Execute Diff (Default): Compares current database with a reference database
# 3. Generate Pending Migrations SQL: Creates an SQL file with all pending database changes
# q. Exit: Terminates the script

# Key Functions:
# - log(): Provides timestamped logging with severity levels
# - safe_execute(): Executes commands with error handling
# - liquibase_command(): Safely runs Liquibase Maven commands
# - setup_container(): Creates a temporary PostgreSQL container
# - cleanup_container(): Removes the temporary container

# Error Handling:
# - Comprehensive error logging
# - Automatic container cleanup
# - Exits with appropriate status codes on failure

# Dependencies:
# - Docker
# - Maven
# - Liquibase Maven Plugin
# - PostgreSQL JDBC Driver

# Notes:
# - Requires a PostgreSQL container running on localhost
# - Default option is to execute diff (option 2)
# - Modify DB_CONFIGS to match your specific database configuration

# Recommended Workflow:
# 1. Ensure Docker and Maven are installed
# 2. Configure database settings
# 3. Run the script and select desired migration management option


# === Configuration ===
# Database Configuration
#readonly DB_CONFIGS=(
#    "CONTAINER_NAME=postgres-reference"
#    "DB_USER=myuser"
#    "DB_PASS=secret"
#    "DB_NAME=postgres-reference"
#    "DB_PORT=5433"
#    "DB_VERSION=18.0"
#    "REFERENCE_PORT=5432"
#)
DB_CONFIGS=(
    "CONTAINER_NAME=postgres-reference"
    "DB_USER=myuser"
    "DB_PASS=secret"
    "DB_NAME=postgres-reference"
    "DB_PORT=5433"
    "DB_VERSION=18.0"
    "REFERENCE_PORT=5432"
)

# Load configuration from array - CRITICAL FUNCTION
load_config() {
    for config in "${DB_CONFIGS[@]}"; do
        export "$config"
    done

    # Debug: Verify variables are loaded
    if [[ -z "$CONTAINER_NAME" ]]; then
        echo "[ERROR] CONTAINER_NAME is not set. Config loading failed!" >&2
        return 1
    fi
}


# === Utility Functions ===
# Log message with timestamp and severity
log() {
    local severity="${2:-INFO}"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [$severity] $1"
}

# Execute command with error handling
safe_execute() {
    local cmd="$1"
    local error_msg="${2:-Command failed}"
    
    if ! $cmd; then
        log "$error_msg" "ERROR"
        exit 1
    fi
}

# Show menu with improved formatting
show_menu() {
    echo "🔧 Database Migration Management"
    echo "----------------------------"
    echo "1) Generate Initial Changelog"
    echo "2) Execute Diff (Default)"
    echo "3) Generate Pending Migrations SQL"
    echo "q) Exit"
    
    read -p "Choose option [2]: " choice
    choice=${choice:-2}
}

# Execute Liquibase command safely
liquibase_command() {
    local command="$1"
    local extra_args="${2:-}"
    
    log "Executing Liquibase $command"
    safe_execute "mvn liquibase:$command $extra_args" "Liquibase $command failed"
}

# Main execution functions
run_updatesql() {
    liquibase_command "updateSQL"
    log "Migration SQL generated at: target/liquibase/migrate.sql"
}

run_update() {
    local db_url="jdbc:postgresql://localhost:$DB_PORT/$DB_NAME"
    liquibase_command "update" "-Dliquibase.url=$db_url -Dliquibase.username=$DB_USER -Dliquibase.password=$DB_PASS"
}

run_changelog() {
    liquibase_command "generateChangeLog"
}

#run_diff() {
#    local db_url="jdbc:postgresql://localhost:$DB_PORT/$DB_NAME"
#    local ref_url="jdbc:postgresql://localhost:$REFERENCE_PORT/postgres"
#
#    liquibase_command "diff" "-Dliquibase.url=$db_url -Dliquibase.referenceUrl=$ref_url"
#}

run_diff() {
    local db_url="jdbc:postgresql://localhost:$DB_PORT/$DB_NAME"
    local ref_url="jdbc:postgresql://localhost:$REFERENCE_PORT/postgres"

    liquibase_command "diff" "-Dliquibase.url=$db_url -Dliquibase.referenceUrl=$ref_url -Dliquibase.referenceUsername=$DB_USER -Dliquibase.referencePassword=$DB_PASS"
}

# Container management functions
cleanup_container() {
    if docker ps -aq -f name="$CONTAINER_NAME" | grep -q .; then
        log "Stopping and removing container: $CONTAINER_NAME"
        safe_execute "docker stop $CONTAINER_NAME" "Failed to stop container"
        safe_execute "docker rm $CONTAINER_NAME" "Failed to remove container"
    fi
}

setup_container() {
    cleanup_container
    
    log "Creating PostgreSQL container: ${DB_VERSION}"
    safe_execute "docker run --name $CONTAINER_NAME \
        -e POSTGRES_USER=$DB_USER \
        -e POSTGRES_PASSWORD=$DB_PASS \
        -e POSTGRES_DB=$DB_NAME \
        -p $DB_PORT:5432 \
        -d postgres:$DB_VERSION" "Container creation failed"
    
    log "Waiting for PostgreSQL to be ready"
    sleep 10
}

# Main script execution
main() {
    # CRITICAL: Load configuration FIRST
    if ! load_config; then
        log "Failed to load configuration" "ERROR"
        exit 1
    fi

    log "Configuration loaded successfully"
    log "Container name: $CONTAINER_NAME"
    log "Database port: $DB_PORT"

    show_menu
    
    case $choice in
        1)
            log "Starting Changelog Generation"
            run_changelog
            cleanup_container
            ;;
        2)
            log "Starting Diff Process"
            setup_container
            run_update
            run_diff
            cleanup_container
            ;;
        3)
            log "Generating Migration SQL"
            run_updatesql
            ;;
        q|Q)
            log "Exiting script"
            exit 0
            ;;
        *)
            log "Invalid option" "ERROR"
            exit 1
            ;;
    esac
}

# Run the main function
main

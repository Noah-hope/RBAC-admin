#!/bin/sh
set -eu
umask 077

# Explicit names only. Read values literally; never source or eval a secret.
read_secret() {
    name=$1
    file=$2
    if [ ! -r "$file" ] || [ ! -s "$file" ]; then
        printf '%s\n' "Required secret unavailable: $name" >&2
        exit 2
    fi
    value=$(cat "$file")
    if [ -z "$value" ]; then
        printf '%s\n' "Required secret empty: $name" >&2
        exit 2
    fi
    export "$name=$value"
    unset value
}

[ "$#" -eq 1 ] || { echo 'Expected server, migrate or bootstrap' >&2; exit 2; }
case "$1" in
    server)
        read_secret SPRING_DATASOURCE_PASSWORD "${SPRING_DATASOURCE_PASSWORD_FILE:-/run/secrets/app-password}"
        read_secret RBAC_IDEMPOTENCY_KEYS_JSON "${RBAC_IDEMPOTENCY_KEYS_JSON_FILE:-/run/secrets/idempotency-keys}"
        exec java -jar /app/app.jar
        ;;
    migrate)
        read_secret RBAC_MIGRATION_PASSWORD "${RBAC_MIGRATION_PASSWORD_FILE:-/run/secrets/migration-password}"
        exec java -Dloader.main=com.adgn.backend.migration.MigrationApplication -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
        ;;
    bootstrap)
        read_secret RBAC_BOOTSTRAP_PASSWORD "${RBAC_BOOTSTRAP_PASSWORD_FILE:-/run/secrets/app-password}"
        read_secret RBAC_BOOTSTRAP_ADMIN_PASSWORD "${RBAC_BOOTSTRAP_ADMIN_PASSWORD_FILE:-/run/secrets/bootstrap-password}"
        export RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD="$RBAC_BOOTSTRAP_ADMIN_PASSWORD"
        exec java -Dloader.main=com.adgn.backend.bootstrap.BootstrapAdminApplication -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
        ;;
    *) echo 'Expected server, migrate or bootstrap' >&2; exit 2 ;;
esac

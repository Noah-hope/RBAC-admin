# Sourced by the official MySQL entrypoint (Config mode 0444, not executable).
# Only runs for a fresh data directory. Credentials are read literally from Secrets.
rbac_app_password=$(cat /run/secrets/app-password)
rbac_migration_password=$(cat /run/secrets/migration-password)
for rbac_password in "$rbac_app_password" "$rbac_migration_password"; do
    case "$rbac_password" in
        ''|*[!A-Za-z0-9_-]*) echo 'DB passwords must use base64url characters only' >&2; exit 2 ;;
    esac
    [ "${#rbac_password}" -ge 24 ] || { echo 'DB passwords require at least 24 characters' >&2; exit 2; }
done
docker_process_sql <<SQL
CREATE USER 'rbac_app'@'%' IDENTIFIED BY '${rbac_app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON rbac_admin_server.* TO 'rbac_app'@'%';
CREATE USER 'rbac_migrator'@'%' IDENTIFIED BY '${rbac_migration_password}';
GRANT ALL PRIVILEGES ON rbac_admin_server.* TO 'rbac_migrator'@'%';
SQL
unset rbac_password rbac_app_password rbac_migration_password

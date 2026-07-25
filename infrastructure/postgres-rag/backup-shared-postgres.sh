#!/bin/sh
set -eu

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${BACKUP_DATABASES:=orgmemory openfga keycloak}"

# Retention is intentionally external to this transactional backup command.
# Follow docs/runbooks/production-zm-deployment.md and prune only after a
# restore-verification job has accepted a newer backup set.
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
destination="/backup/${timestamp}"
mkdir -p "${destination}"
chmod 0700 "${destination}"

pg_dumpall --globals-only | gzip -9 > "${destination}/globals.sql.gz"

for database_name in ${BACKUP_DATABASES}; do
    exists="$(
        psql \
            --no-psqlrc \
            --tuples-only \
            --no-align \
            --dbname postgres \
            --set database_name="${database_name}" \
            <<'SQL'
SELECT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = :'database_name'
);
SQL
    )"
    if [ "${exists}" = "t" ]; then
        pg_dump \
            --format=custom \
            --compress=9 \
            --no-owner \
            --no-privileges \
            --file "${destination}/${database_name}.dump" \
            "${database_name}"
    fi
done

(
    cd "${destination}"
    sha256sum ./* > SHA256SUMS
)

printf 'Backup completed: %s\n' "${destination}"

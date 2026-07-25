#!/bin/sh
set -eu

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${ORGMEMORY_DB_NAME:=orgmemory}"
: "${ORGMEMORY_DB_USER:=orgmemory}"
: "${ORGMEMORY_DB_PASSWORD:?ORGMEMORY_DB_PASSWORD is required}"
: "${OPENFGA_DB_NAME:=openfga}"
: "${OPENFGA_DB_USER:=openfga}"
: "${OPENFGA_DB_PASSWORD:?OPENFGA_DB_PASSWORD is required}"
: "${KEYCLOAK_DB_NAME:=keycloak}"
: "${KEYCLOAK_DB_USER:=keycloak}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is required}"

bootstrap_database() {
    database_name="$1"
    database_role="$2"
    database_password="$3"
    connection_limit="$4"

    psql \
        --no-psqlrc \
        --set ON_ERROR_STOP=1 \
        --set database_name="${database_name}" \
        --set database_role="${database_role}" \
        --set database_password="${database_password}" \
        --set connection_limit="${connection_limit}" \
        --file /usr/local/share/orgmemory/bootstrap-database.sql \
        postgres
}

bootstrap_database "${ORGMEMORY_DB_NAME}" "${ORGMEMORY_DB_USER}" "${ORGMEMORY_DB_PASSWORD}" 30
bootstrap_database "${OPENFGA_DB_NAME}" "${OPENFGA_DB_USER}" "${OPENFGA_DB_PASSWORD}" 20
bootstrap_database "${KEYCLOAK_DB_NAME}" "${KEYCLOAK_DB_USER}" "${KEYCLOAK_DB_PASSWORD}" 20

psql \
    --no-psqlrc \
    --set ON_ERROR_STOP=1 \
    --set database_role="${ORGMEMORY_DB_USER}" \
    --dbname "${ORGMEMORY_DB_NAME}" \
    <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age CASCADE;

SELECT format(
    'GRANT USAGE ON SCHEMA ag_catalog TO %I',
    :'database_role'
)
\gexec

SELECT format(
    'GRANT SELECT ON TABLE ag_catalog.ag_graph TO %I',
    :'database_role'
)
\gexec

SELECT format(
    'ALTER ROLE %I IN DATABASE %I SET session_preload_libraries = %L',
    :'database_role',
    current_database(),
    'age'
)
\gexec
SQL

psql \
    --no-psqlrc \
    --set ON_ERROR_STOP=1 \
    --tuples-only \
    --no-align \
    --dbname "${ORGMEMORY_DB_NAME}" \
    --command "SELECT extname || '=' || extversion FROM pg_extension WHERE extname IN ('age', 'vector') ORDER BY extname;"

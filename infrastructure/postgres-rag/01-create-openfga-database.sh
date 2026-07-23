#!/usr/bin/env bash
set -Eeuo pipefail

: "${OPENFGA_POSTGRES_DB:=openfga}"
: "${OPENFGA_POSTGRES_USER:=openfga}"
: "${OPENFGA_POSTGRES_PASSWORD:=openfga}"

connection_args=(
  --set=ON_ERROR_STOP=1
  --username "$POSTGRES_USER"
  --dbname "$POSTGRES_DB"
  --set=fga_db="$OPENFGA_POSTGRES_DB"
  --set=fga_user="$OPENFGA_POSTGRES_USER"
  --set=fga_password="$OPENFGA_POSTGRES_PASSWORD"
)
if [[ -n "${PGHOST:-}" ]]; then
  connection_args+=(--host "$PGHOST")
fi

psql "${connection_args[@]}" <<-'SQL'
SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L',
  :'fga_user',
  :'fga_password'
)
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_catalog.pg_roles
  WHERE rolname = :'fga_user'
)
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I',
  :'fga_db',
  :'fga_user'
)
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_catalog.pg_database
  WHERE datname = :'fga_db'
)
\gexec
SQL

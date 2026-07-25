# syntax=docker/dockerfile:1.7

ARG PGVECTOR_IMAGE=pgvector/pgvector:0.8.4-pg18-bookworm@sha256:212765b63c1462883de295c4c415edcd22191b8d8d46853e86ad05d4b577a4cb
ARG APACHE_AGE_COMMIT=e43dc1a12b78fba4acef9835b2b10379b8d243b4

FROM ${PGVECTOR_IMAGE} AS age-build

ARG APACHE_AGE_COMMIT

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       bison \
       build-essential \
       ca-certificates \
       flex \
       git \
       postgresql-server-dev-18 \
    && git init /usr/src/age \
    && git -C /usr/src/age remote add origin https://github.com/apache/age.git \
    && git -C /usr/src/age fetch --depth 1 origin "${APACHE_AGE_COMMIT}" \
    && git -C /usr/src/age checkout --detach FETCH_HEAD \
    && make -C /usr/src/age \
    && make -C /usr/src/age install \
    && rm -rf /var/lib/apt/lists/*

FROM ${PGVECTOR_IMAGE}

COPY --from=age-build /usr/lib/postgresql/18/lib/age.so /usr/lib/postgresql/18/lib/age.so
COPY --from=age-build /usr/share/postgresql/18/extension/age* /usr/share/postgresql/18/extension/
COPY infrastructure/postgres-rag/01-create-openfga-database.sh /docker-entrypoint-initdb.d/01-create-openfga-database.sh
COPY infrastructure/postgres-rag/bootstrap-database.sql /usr/local/share/orgmemory/bootstrap-database.sql
COPY infrastructure/postgres-rag/backup-shared-postgres.sh /usr/local/bin/backup-shared-postgres
COPY infrastructure/postgres-rag/bootstrap-shared-databases.sh /usr/local/bin/bootstrap-shared-databases

RUN printf '%s\n' \
    'CREATE EXTENSION IF NOT EXISTS vector;' \
    'CREATE EXTENSION IF NOT EXISTS age CASCADE;' \
    > /docker-entrypoint-initdb.d/00-create-rag-extensions.sql \
    && chmod 0755 \
       /docker-entrypoint-initdb.d/01-create-openfga-database.sh \
       /usr/local/bin/backup-shared-postgres \
       /usr/local/bin/bootstrap-shared-databases

CMD ["postgres", "-c", "shared_preload_libraries=pg_stat_statements,age"]

# syntax=docker/dockerfile:1.7

ARG PGVECTOR_IMAGE=pgvector/pgvector:0.8.2-pg18-trixie@sha256:b7337db8fe39d12fe8ecb0003c72680f24479813a744b43154eee6f2eab5a5f3
ARG APACHE_AGE_COMMIT=806fa2ebdb300b3e76ef30cdba61803babbf2683

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
COPY --from=age-build /usr/share/postgresql/18/extension/age--1.7.0.sql /usr/share/postgresql/18/extension/age--1.7.0.sql
COPY --from=age-build /usr/share/postgresql/18/extension/age.control /usr/share/postgresql/18/extension/age.control
COPY infrastructure/postgres-rag/01-create-openfga-database.sh /docker-entrypoint-initdb.d/01-create-openfga-database.sh

RUN printf '%s\n' \
    'CREATE EXTENSION IF NOT EXISTS vector;' \
    'CREATE EXTENSION IF NOT EXISTS age CASCADE;' \
    > /docker-entrypoint-initdb.d/00-create-rag-extensions.sql \
    && chmod 0755 /docker-entrypoint-initdb.d/01-create-openfga-database.sh

CMD ["postgres", "-c", "shared_preload_libraries=age"]

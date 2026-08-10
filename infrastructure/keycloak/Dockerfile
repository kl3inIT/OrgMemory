# syntax=docker/dockerfile:1.7

ARG KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.7.0@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13
ARG NODE_IMAGE=node:24-bookworm-slim@sha256:3638d9a6fe4030bd716be989438248074489337ba3275657f93595428be4fc03
ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237

FROM ${NODE_IMAGE} AS theme-node

FROM ${MAVEN_IMAGE} AS theme-build
COPY --from=theme-node /usr/local/ /usr/local/
WORKDIR /workspace

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY patches/ ./patches/
COPY infrastructure/keycloak/theme-ui/package.json ./infrastructure/keycloak/theme-ui/package.json
RUN corepack enable pnpm && \
    corepack pnpm install \
      --frozen-lockfile \
      --filter @orgmemory/keycloak-theme

COPY infrastructure/keycloak/theme-ui/ ./infrastructure/keycloak/theme-ui/
RUN corepack pnpm --filter @orgmemory/keycloak-theme build-keycloak-theme

FROM ${KEYCLOAK_IMAGE} AS build

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

COPY --from=theme-build --chmod=0444 \
  /workspace/infrastructure/keycloak/theme-ui/dist_keycloak/orgmemory-keycloak-theme.jar \
  /opt/keycloak/providers/orgmemory-keycloak-theme.jar
RUN /opt/keycloak/bin/kc.sh build

FROM ${KEYCLOAK_IMAGE}

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="OrgMemory Keycloak" \
      org.opencontainers.image.source="https://github.com/kl3inIT/OrgMemory" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

COPY --from=build /opt/keycloak/ /opt/keycloak/
COPY --chown=keycloak:keycloak infrastructure/keycloak/orgmemory-realm.prod.json /opt/keycloak/data/import/orgmemory-realm.json

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

USER keycloak

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]

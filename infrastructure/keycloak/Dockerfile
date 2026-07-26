# syntax=docker/dockerfile:1.7

ARG KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.7.0@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13

FROM ${KEYCLOAK_IMAGE} AS build

ENV KC_DB=postgres \
    KC_FEATURES=cimd \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

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
    KC_FEATURES=cimd \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

USER keycloak

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]

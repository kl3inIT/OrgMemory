# syntax=docker/dockerfile:1.7

FROM node:24.14.0-alpine@sha256:7fddd9ddeae8196abf4a3ef2de34e11f7b1a722119f91f28ddf1e99dcafdf114 AS build

WORKDIR /workspace

RUN corepack enable

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY patches patches
COPY apps/cli/package.json apps/cli/package.json
COPY apps/web/package.json apps/web/package.json
COPY apps/docs/package.json apps/docs/package.json

RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @orgmemory/web...

COPY contracts /workspace/contracts
COPY apps/web /workspace/apps/web

RUN pnpm --filter @orgmemory/web gen:api \
    && pnpm --filter @orgmemory/web build

FROM nginxinc/nginx-unprivileged:1.29.5-alpine@sha256:42a7d7f2ee23e9f5a1dcdf3647ba5c585bbd18f79e79cd817e70e8cd61c55779

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="OrgMemory web" \
      org.opencontainers.image.source="https://github.com/kl3inIT/OrgMemory" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=101:101 /workspace/apps/web/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

USER 101:101

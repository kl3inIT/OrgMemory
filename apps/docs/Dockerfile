# syntax=docker/dockerfile:1.7

FROM node:24.14.0-alpine@sha256:7fddd9ddeae8196abf4a3ef2de34e11f7b1a722119f91f28ddf1e99dcafdf114 AS dependencies

WORKDIR /workspace

RUN corepack enable

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/cli/package.json apps/cli/package.json
COPY apps/web/package.json apps/web/package.json
COPY apps/docs/package.json apps/docs/package.json

RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @orgmemory/docs...

FROM dependencies AS build

ARG DOCS_INCLUDE_DRAFTS=false
ENV DOCS_INCLUDE_DRAFTS=${DOCS_INCLUDE_DRAFTS}
ENV DOCS_DEPLOYMENT_MODE=production
ENV NEXT_TELEMETRY_DISABLED=1

COPY apps/docs apps/docs

RUN pnpm --filter @orgmemory/docs lint \
    && pnpm --filter @orgmemory/docs typecheck \
    && pnpm --filter @orgmemory/docs check:manifest \
    && pnpm --filter @orgmemory/docs check:publication \
    && pnpm --filter @orgmemory/docs build

FROM node:24.14.0-alpine@sha256:7fddd9ddeae8196abf4a3ef2de34e11f7b1a722119f91f28ddf1e99dcafdf114 AS runtime

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="OrgMemory docs" \
      org.opencontainers.image.source="https://github.com/kl3inIT/OrgMemory" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

ENV HOSTNAME=0.0.0.0
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

WORKDIR /app

RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

COPY --from=build --chown=nextjs:nodejs /workspace/apps/docs/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /workspace/apps/docs/.next/static ./apps/docs/.next/static

USER nextjs

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -q -O - http://127.0.0.1:3000/healthz | grep -qx ok

CMD ["node", "apps/docs/server.js"]

# Slack Connection Admin Plan

Four phases, each a commit that leaves the build green.

## Phase 1 — Ledger and encryption

- [ ] `V22__source_connection_configuration.sql`: crawl configuration on
  `source_connections`, and `source_connection_credentials` holding the ciphertext,
  key version, and who set it when.
- [ ] `SourceConnectionSecretCipher` over `Encryptors.stronger`, keyed from
  configuration, refusing to encrypt without a key rather than storing anything
  weaker.
- [ ] `SourceConnectionAdminService`: read the configuration, write it, set and
  forget the credential, resolve the credential for a crawl. Every mutation appends
  a permission audit event and none of them logs the token.

Gate: `.\gradlew.bat :core:test`.

## Phase 2 — Admin API

- [ ] `AdminSlackConnectorController` on `/api/admin/connectors/slack`: get, put,
  put credential, delete credential, test.
- [ ] `POST /test` calls Slack `auth.test` and returns the workspace it authenticated
  as, or the error code, and never the token.
- [ ] Regenerate `contracts/openapi.json` and `pnpm -C web gen:api`.

Gate: `.\gradlew.bat :apps:api:test`.

## Phase 3 — Adapter reads the ledger

- [ ] `SlackConnectorBatchSource` resolves its connection and credential per poll, so
  a change takes effect without a restart.
- [ ] `SlackConnectorProperties` stops being a configuration source; the adapter bean
  is present whenever the module is and produces nothing until a connection says
  otherwise.

Gate: `.\gradlew.bat :integrations:connectors:test :apps:worker:test`.

## Phase 4 — Web and proofs

- [ ] `/admin/connectors` page: configuration form, write-only credential field with
  its own state, and a test button reporting what Slack said.
- [ ] Integration proof: a non-admin gets 403 everywhere; a configured credential is
  never returned by any endpoint; a crawl picks up a configuration change without a
  restart.
- [ ] Consolidate into the specs, `ARCHITECTURE.md`, and docs/tests; move the
  increment to `completed`.

Gate: `.\gradlew.bat clean test`, then `pnpm -C web lint`, `typecheck`, `build`.

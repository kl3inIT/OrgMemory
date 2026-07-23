# Slack Connection Admin Plan

Four phases, each a commit that leaves the build green.

## Phase 1 — Ledger and encryption

- [x] `V22__source_connection_configuration.sql`: crawl configuration on
  `source_connections`, and `source_connection_credentials` holding the ciphertext,
  key version, and who set it when.
- [x] `SecretCipher` over `Encryptors.stronger`, keyed from configuration, refusing
  to encrypt without a key rather than storing anything weaker.
- [x] `SourceConnectionAdminService`: read the configuration, write it, set and
  forget the credential, resolve the credential for a crawl. Every mutation appends
  a permission audit event and none of them logs the token.

Gate: `.\gradlew.bat :core:test`. Done in `97bddfb`.

## Phase 2 — Admin API

- [x] `AdminSlackConnectorController` on `/api/admin/connectors/slack`: list,
  configure, put credential, delete credential, test a submitted token, test a
  stored one.
- [x] `POST /test` calls Slack `auth.test` and returns the workspace it authenticated
  as, or the error code, and never the token.
- [x] Regenerate `contracts/openapi.json` and `pnpm -C web gen:api`.

Two things came out of reading Onyx here rather than from the plan:

- Its `validate_connector_settings` follows `auth.test` with a one-channel
  `conversations.list`, because authentication cannot fail for a missing scope. A
  token installed without `channels:read` passes `auth.test` and then fails hours
  later as an indexing error. `SlackCredentialProbe` makes both calls and reports
  `canListChannels` separately from `authenticated`.
- Its admin API returns stored credentials masked to first and last four characters,
  with `MASK_CREDENTIAL_PREFIX=false` turning even that off. Nothing here returns the
  token in any form; the screen gets `credentialSet`, who set it, and when.

Gate: `.\gradlew.bat :apps:api:test`.

## Phase 3 — Adapter reads the ledger

- [x] `SlackConnectorBatchSource` resolves its connections and their credentials per
  poll through `ConnectorConnectionDirectory`, so a change takes effect without a
  restart. It now produces one batch per enabled connection rather than one for the
  single workspace a property named, and a connection that cannot produce — no
  token, rate limited, most channels unreadable — is skipped rather than allowed to
  end the poll for the others.
- [x] `SlackConnectorProperties`, `SlackCredentialProvider`, and
  `ConfiguredSlackCredentialProvider` deleted. The adapter bean is present whenever
  the module is and produces nothing until a connection says otherwise: consent to
  crawl is a row somebody wrote, not a flag on a host.

Gate: `.\gradlew.bat :integrations:connectors:test :apps:worker:test`.

`SourceIngestionPipelineIntegrationTests` re-arms a job with `now()` and the claim
compares that column against the JVM clock, so a container clock drifting slightly
ahead of the host leaves the job not yet due and the test asserts on a stale row.
Backdating the re-arm removes the dependence on the two clocks agreeing. The
diagnosis fits the evidence — fails only under a loaded suite, passes in isolation,
and fails exactly as a no-op second pass would — but one green run is not proof.

## Phase 4 — Web and proofs

- [ ] `/admin/connectors` page: configuration form, write-only credential field with
  its own state, and a test button reporting what Slack said.
- [ ] Integration proof: a non-admin gets 403 everywhere; a configured credential is
  never returned by any endpoint; a crawl picks up a configuration change without a
  restart.
- [ ] Consolidate into the specs, `ARCHITECTURE.md`, and docs/tests; move the
  increment to `completed`.

Gate: `.\gradlew.bat clean test`, then `pnpm -C web lint`, `typecheck`, `build`.

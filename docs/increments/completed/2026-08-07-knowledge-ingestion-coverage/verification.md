# Knowledge Ingestion Coverage Verification

Date: 2026-08-10

## Delivered behavior

- Knowledge ingestion now consumes a neutral canonical document and typed block
  contract from the reusable Spring AI parser adapter instead of owning parser
  implementation details.
- The default `structured-block-v1` policy dispatches by canonical block kind,
  persists requested and resolved processing snapshots, and keeps retries
  deterministic without mutating existing READY revisions.
- Admission, parsing, worker routing, and browser policy agree on sixteen file
  suffixes across fifteen format families. CSV uses its dedicated structured
  reader, HTML removes non-evidence boilerplate, and archives fail closed.
- Per-format upload and chunk limits replace the former global limits while
  retaining page provenance and table header/row boundaries.

## Delivery evidence

- Independent architecture challenge verdict: `REVISE`; the implementation
  incorporated its required safeguards and records the choice in decision
  0037.
- Pull request [#342](https://github.com/kl3inIT/OrgMemory/pull/342) merged with
  merge commit `e18352b5ecee487fb5b639db331327be284a0aa0`.
- Pull-request CI passed every required job. CodeRabbit retry was rate-limited
  and produced no review findings; the repository PR loop permits all-green CI
  as the fallback in that condition.
- Post-merge `main` CI run `31365327995` passed Backend, Web, CLI, public docs,
  OpenFGA, evaluation, PostgreSQL GraphRAG, OpenSearch, Neo4j, deployment
  contracts, product release, and the aggregate CI Gate for the merge SHA.
- Production image run `31365762115` published and scanned the complete
  immutable image set. Production deploy run `31366134939` then passed for the
  same SHA.

## Local and runtime gates

- Backend core, worker, API, and parser reports: 935 tests, zero failures and
  zero errors.
- Node `v24.15.0`: web lint, TypeScript, 31 unit-test files with 105 tests,
  production build, generated API check, and focused browser upload coverage
  for XLSX and HTML passed.
- JetBrains inspection was unavailable because no usable CLI/profile was
  present; Gradle compilation and tests provided the backend static-analysis
  fallback.
- The ZM runtime reported API, worker, MCP, and web images at the merge SHA;
  API, MCP, and web were healthy, and both JVM applications started cleanly.
- Flyway reported schema version 31 and no pending migration. The public web
  endpoint returned HTTP 200 and the public OIDC issuer matched the configured
  OrgMemory realm.

## Residual operational note

- The docs image built successfully, but docs deploy run `31365896179` was
  blocked because the server's operator checkout contains pre-existing local
  edits. No operator files were cleaned or overwritten. This does not affect
  the separately deployed application image set, but docs deployment remains a
  distinct unresolved operational gate.

# Independent Architecture Challenge Brief

Date: 2026-08-01

## Decision To Challenge

Extract the duplicated Slack, Google Drive, and GitHub connection polling
flow into `PollingConnectorBatchSource<C>` without changing the core
`ConnectorBatchSource` SPI or any persisted crawl-cursor bytes. Standardize a
50% mostly-failed abort policy and credential-fingerprinted derived-client
cache across all three adapters.

Read [design.md](design.md) as the proposal under challenge, not as an accepted
answer.

## Reviewed Baseline

- Branch baseline: `origin/main` at
  `ddda13598e762391d291c24539e7040ee5576e6f`.
- Comparable reference: Onyx at
  `D:/OrgMemory/tmp/onyx` commit
  `618b5031bf21463f44e3bed9eb9d5073b806fec0`.

## Repository Constraints

- Read root `AGENTS.md`, `docs/conventions.md`,
  `docs/guidelines/agent-safety.md`, and the knowledge-ingestion spec/test
  pair before judging.
- Do not alter `ConnectorBatchSource`, crawl-batch contracts, generated
  models, persisted cursor bytes, or connector source semantics outside the
  explicit GitHub failure-admission correction.
- Credentials must still be resolved every poll; cached clients must rebuild
  after credential or client-affecting configuration changes and retire when
  the connection disappears.
- A failed/rejected content attempt must not consume cadence.
- Source-specific calls, mapping, completeness evidence, and component cursor
  material stay adapter-local.

## Evidence To Inspect

- `integrations/connectors/src/main/java/com/orgmemory/connectors/ContentCadence.java`
- `integrations/connectors/src/main/java/com/orgmemory/connectors/slack/SlackConnectorBatchSource.java`
- `integrations/connectors/src/main/java/com/orgmemory/connectors/googledrive/GoogleDriveConnectorBatchSource.java`
- `integrations/connectors/src/main/java/com/orgmemory/connectors/github/GitHubConnectorBatchSource.java`
- the three matching `*ConnectorBatchSourceTests.java` files
- `core/src/main/java/com/orgmemory/core/knowledge/connector/ConnectorBatchSource.java`
- `contracts/connector/crawl-batch.schema.json` and fixtures
- `D:/OrgMemory/tmp/onyx/backend/onyx/connectors/connector_runner.py`
- `D:/OrgMemory/tmp/onyx/backend/onyx/connectors/factory.py`
- `D:/OrgMemory/tmp/onyx/backend/onyx/connectors/credentials_provider.py`

## Strongest Counterposition

Do not create an abstract driver. Keep the adapters independent and extract
only narrow utilities for cursor construction, cadence, and client caching.
The three sources have different failure denominators, credential/client
inputs, permission-crawl semantics, and completeness evidence. Forcing them
through one lifecycle risks an attractive abstraction whose callbacks hide
more policy than they standardize, and changes GitHub behavior merely to make
the abstraction uniform.

## Questions For The Reviewer

1. Does the proposed driver separate framework policy from source semantics,
   or does it disguise real adapter differences behind failure counts and
   callbacks?
2. Is GitHub's missing abort a reliability defect that should be corrected to
   50%, or a valid source-specific behavior that the driver must preserve?
   Evaluate exactly what should count as one failed GitHub unit.
3. Is resolving every credential, hashing it, and caching the derived client
   under `(organization, source system, connection key, credential revision,
   client-config revision)` sufficient for rotation, revocation, tenant
   isolation, and retirement? Identify secret-lifecycle or concurrency gaps.
4. Can the outer cursor builder be centralized while proving byte identity
   for Slack, Drive, and GitHub? Identify any ordering, enum rendering,
   encoding, or prefix trap the golden vectors must pin.
5. Does extending the abstract class preserve the current
   `ConnectorBatchSource`/Spring bean/contract-fixture compatibility, or is a
   composition-based driver safer?
6. What must-fix constraints and mechanical tests are required before this
   design is safe to implement?

## Required Verdict Format

Return a read-only, evidence-backed verdict with:

- `Decision`: approve, approve with corrections, or reject;
- `Strongest counterargument`;
- `Repository evidence` with concrete paths;
- `Must-fix before implementation` as a numbered binding list;
- `Rejected alternative` and why;
- explicit answers to all six questions.

Do not edit the repository. Be adversarial: if the proposal is too easy to
approve, identify the most likely production failure and attack it directly.

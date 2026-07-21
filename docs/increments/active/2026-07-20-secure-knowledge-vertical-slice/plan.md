# Secure Knowledge Vertical Slice Plan

## 1 — Freeze The Existing Slice

- [ ] Commit/isolate current permission and knowledge work.
- [x] Existing `ModulithVerificationTests` verifies current module boundaries.
- [ ] Add generated module documentation and fail CI on boundary drift.
- [ ] Add Java `-parameters` for Spring AI tool schemas and verify bounded test-JVM
  settings before adopting them.
- [ ] Remove the unused Modulith JPA event starter or replace it deliberately with
  JDBC/outbox support when the durable job transport lands.
- [ ] Add two-user golden fixtures for current list/detail/audit behavior.

## 2 — Source Revision And Blob

- [ ] Add `SourceObject`, `SourceRevision`, and `EvidenceBlob` schema/model
  alongside current tables; migrate without changing evidence identity.
- [ ] Add `BlobStorePort` plus local adapter and integrity metadata.
- [ ] Add direct-upload session, private default ACL, quarantine, and size/type
  checks. Keep malware/DLP as an explicit required gate interface.
- [ ] Define versioned Edge/upload/staging schemas under root `contracts/`; do not
  make contracts a Gradle module.

## 3 — Durable Worker Pipeline

- [ ] Define stage state, idempotency key, retry, tombstone, and failure contract.
- [ ] Move parse/normalize/chunk/embed work to worker; API only accepts commands
  and exposes state.
- [ ] Publish the Knowledge Asset head atomically after all required stages.

## 4 — OpenFGA And Source Principals

- [x] Add `integrations/authorization-openfga`, official Java SDK, a versioned
  model, executable model tests, and a provider-neutral Java adapter test.
- [x] Remove the local auth bypass and JWT-role/email bootstrap; enforce explicit
  issuer/subject identity binding and OpenFGA for control-plane and Capability
  Asset permissions.
- [x] Add checksum-verified repo-local CLI installation, compose runtime,
  reproducible store/model bootstrap, and demo relationship tuples.
- [ ] Add a live OpenFGA integration/contract test and runtime model-version
  convergence proof.
- [ ] Map external source users/groups to verified OrgMemory principals.
- [ ] Add outbox, tuple version, reconciliation, and fail-closed convergence.
- [ ] Prove Admin cannot broaden source ACL and unknown mappings deny.

## 5 — Hybrid Secure Retrieval

- [ ] Add PostgreSQL FTS + pgvector projection with ACL/model/version metadata.
- [ ] Implement `SecureKnowledgeRetrieval`: prefilter, OpenFGA check, rank,
  bounded context, citation recheck, and audit.
- [ ] Add revocation/stale/projection-mismatch negative tests.

## 6 — AI Adapter And In-App Agent

- [ ] Add provider-neutral task/route ports to core.
- [ ] Add `integrations/ai-openai-compatible` with its first real implementation
  and contract tests; app still boots without a key.
- [ ] Persist model, extractor, prompt, and route versions with derived evidence.
- [ ] Add read-only `search_knowledge`/`get_knowledge` in-app tools over the same
  retrieval use case and durable turn idempotency.

## 7 — Minimal New Web Flow

- [x] Add new shell and semantic light/dark tokens.
- [x] Export OpenAPI and generate typed fetch/Zod/TanStack clients; keep the AI
  streaming transport separate from ordinary REST contracts.
- [x] Add oxlint with React/TypeScript correctness rules and generated/registry
  exclusions.
- [ ] Add Vitest and one critical Playwright project before expanding UI.
- [ ] Build Ask with visible waiting/tool/evidence/citation/error states.
- [ ] Build Sources upload/status/privacy view and Review publication view.
- [ ] Run real-browser two-user upload, answer, deny, and revoke flow.

## Completion

- [ ] Run static, clean test, web typecheck/build, contract, E2E, and leak tests.
- [ ] Consolidate current facts/specs/tests/decisions and move this increment to
  `completed` before starting MCP or graph work.

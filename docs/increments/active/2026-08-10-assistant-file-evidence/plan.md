# Reusable Document Parsing and Assistant File Evidence Plan

## Status

Design and architecture challenge only. No Assistant upload behavior is
implemented by this increment yet.

## 1. Ground the decision

- [x] Inspect the current parser contracts, worker implementation, Source upload
  lifecycle, Assistant conversation/citation path, safety guideline, and domain
  specs/tests.
- [x] Study pinned Northstar and Onyx source and record mechanisms worth learning
  and authorization assumptions that OrgMemory must not inherit.
- [x] Complete the independent architecture challenge and record the verdict,
  strongest counterargument, evidence, final choice, and rejected alternative.
  Verdict: `REVISE`; the three proposed directions survive, with active-engine
  answerability and selection-constrained retrieval added as must-fix design
  gates.

## 2. Reusable parser adapter

- [ ] Create `integrations:document-parsing-spring-ai` and move only the concrete
  extraction adapter, readers, canonical block construction, capability
  descriptors, and adapter tests out of `apps:worker`.
- [ ] Add a channel-neutral parse failure taxonomy; keep Knowledge rejection
  mapping and ingestion terminal state in worker/core Source ownership. Prove a
  deterministic corrupt file does not enter the generic retryable
  `PARSING_FAILED` path.
- [ ] Make format capability queryable without making it an admission policy.
- [ ] Wire the worker through explicit configuration and prove unchanged parser
  component identity and byte-identical requested/resolved profile hashing for a
  behavior-preserving move; bump the identity only for extraction behavior
  changes.
- [ ] Implement ingestion-coverage item 3 readers/admission against this boundary:
  dedicated CSV, sanitized HTML, and the coordinated format gates.

## 3. Governed Assistant evidence

- [ ] Define the Assistant evidence-binding aggregate and Flyway migration with
  organization/conversation/creator/source/revision identity, ordering, and
  immutable turn association.
- [ ] Add a core use case that delegates bytes to `SourceUploadService`; do not
  duplicate object storage, content-type, Space, classification, or permission
  validation. Binding creation exists only inside this multipart use case; do
  not add a bind-existing-Source-by-ID endpoint.
- [ ] Add status projection derived from Source processing state and a turn-claim
  validator that rechecks organization, conversation, actor authorization,
  current revision, evidence integrity, and answerability under the configured
  engine. For GraphRAG, require the selected Asset's projection batch to be
  published, not merely `SourceRevision.READY`.
- [ ] Add internal `KnowledgeEvidenceSelection` to the configured retrieval
  engine. Make binding turns selection-only; intersect selected Asset/Source IDs
  before scoring, retain the ceiling through graph expansion and closure
  verification, reject non-selected citations, apply the global budget to the
  selected subset, and require usable evidence from every selected binding.
- [ ] Extend OpenAPI and the composer with upload progress, Space/classification
  choice, at-most-three selection, processing/failed states, retry-safe ordered
  binding submission, and the existing document viewer. Disclose that upload
  publishes to the selected Space audience and disable attachment for actors
  with no `can_create_asset` Space.
- [ ] Keep images, direct provider uploads, transient attachments, OCR, archives,
  MSG/EML recursion, and generated files closed.

## 4. Verification and consolidation

- [ ] Cover cross-organization ID probing, another conversation's binding,
  revoked access between upload and send, unready/failed/retired revisions,
  retry identity, selected-source retrieval, citation recheck, and prompt
  injection fixtures.
- [ ] Cover the READY/projection gap, graph expansion into a non-selected Asset,
  one selected file returning no usable evidence, no-Space UX, and the absence of
  any bind-existing-Source endpoint.
- [ ] Cover parser capability/admission mismatches and prove no parsing or
  embedding occurs on the API request thread.
- [ ] Reconcile `assistant-and-mcp` and `knowledge-ingestion` specs and test
  matrices, refresh `Source:`/`Reconciled:`, add a durable decision, complete
  static/full gates, and move this increment to completed.

## Sequencing

Finish Knowledge ingestion coverage item 3 and the reusable parser extraction
before wiring the composer. Assistant may depend on the resulting Source
pipeline; ingestion must never depend on Assistant.

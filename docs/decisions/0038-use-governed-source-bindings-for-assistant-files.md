# 0038 — Use governed Source bindings for Assistant files

Status: accepted
Date: 2026-08-10

## Context

Assistant needed file evidence in the composer after the reusable document
parser shipped. A direct-provider or transient attachment lane would require a
second authorization, retention, deletion, parsing, egress, replay, and citation
model. Treating `SourceRevision.READY` as sufficient also did not match the
default GraphRAG engine: a revision may be ready while its exact graph projection
is not yet published. Finally, ordinary permission filtering alone could allow a
graph neighbor or organizational search result outside the selected files to
enter a “summarize these files” turn.

## Decision

The first Assistant file path publishes durable governed Knowledge. A multipart
use case delegates bytes through the parent-owned `knowledge::evidence` port to
Source Ledger's canonical upload pipeline. Assistant persists only a binding to
the exact Source object and revision, scoped by organization, conversation, and
creator. It exposes upload, list, and get-status operations; it does not expose a
bind-existing-Source-by-ID operation.

Turn submission atomically persists at most three distinct ordered bindings
beside the initiating USER message. Claim time rechecks exact lifecycle,
authorization, integrity, and active-engine answerability. Canonical retrieval
requires current READY chunks. GraphRAG additionally requires the exact Asset
version's current-profile projection generation to be published.

A selected turn is selection-only. `KnowledgeEvidenceSelection` narrows the
already-authorized scope before ranking and remains a hard ceiling through graph
seed, expansion, closure verification, and citation output. Every selected
Source must yield usable final evidence before generation. Retry reuses the same
ordered binding identities.

## Independent challenge

The increment challenge returned `REVISE`. It accepted governed Source reuse and
the separate Assistant binding aggregate, but required active-engine readiness,
selection-constrained retrieval, and all-selected-files answerability. Those
conditions are part of this decision and the implementation.

## Rejected alternatives

- Send bytes or provider file handles directly to the model. This bypasses
  canonical authorization, citation, processing identity, and egress policy.
- Add a transient TTL attachment lane in the first delivery. It is a legitimate
  later product, but only with its own explicit retention, malware/DLP, replay,
  deletion, tool-access, and promotion contracts.
- Bind any existing Source ID supplied by the browser. Possession of an opaque ID
  is not authority, and the MVP needs no second association workflow.
- Treat a READY Source as GraphRAG-ready. This can enable submit before the exact
  projection serving the turn exists.
- Apply selection only after retrieval. That permits non-selected graph evidence
  to influence expansion, budgets, or the model before it is filtered.

## Consequences

- Assistant reuses ingestion without becoming a parser or Source lifecycle
  owner; Worker remains the sole production parser caller.
- Composer uploads are durable and visible under the selected Knowledge Space's
  audience and lifecycle, which the UI discloses before upload.
- Removing a chip removes only the pending turn selection, not the governed
  Source. Retirement and later access revocation use existing Knowledge rules.
- Images, OCR, archives, email recursion, direct provider uploads, generated
  files, and transient attachments remain closed.

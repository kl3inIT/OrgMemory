# Architecture Challenge Verdict: Assistant File Evidence

## Execution record

- Reviewer: Claude Fable 5, independent read-only Orca session
- Orca worktree:
  `f5c5bcc9-78f9-4956-9b62-91d4021ee102::C:/Users/admin/orca/workspaces/assistant-file-evidence-architecture/assistant-file-evidence-review`
- Terminal: `term_8c754642-8c07-4420-b0d8-f6d79c37b5ea`
- Reviewed commit: `a43614dad6e415755c66752d308a7ab8265373c4`
- Code baseline: `755aab1a9a86888fc547a7bf684421f4bb1a645b`
- Reviewer made no repository edits.

## Verdict

**REVISE.** All three proposed directions survive attack:

1. extract `integrations:document-parsing-spring-ai`;
2. deliver governed-only Assistant file evidence; and
3. reject the transient turn-local lane for the first delivery.

Implementation must not begin until the design closes two load-bearing gaps:
Source `READY` is not active-engine answerability, and current retrieval has no
selected Source/Asset ceiling.

## Strongest counterargument

The proposed status enabled Send when the Source revision became `READY`. Under
the default GraphRAG runtime, that revision may still be absent from the winning
projection batch because graph indexing is separately queued, LLM-bearing,
retryable, and independently failable. A selection-constrained query in that
window returns zero evidence and the existing Assistant correctly emits its
model-free not-found answer. The UI would call the file ready while the runtime
could not answer from it, silently violating the design's own no-omission rule.

The second gap is structural: `PermissionAwareKnowledgeSearch.search` accepts
actor, query, limit, and request identity, but not a selected Source/Asset set.
The design cannot claim attachment-constrained retrieval without defining how
that ceiling enters seed search, graph expansion, closure verification, budgets,
and citation hydration.

## Repository evidence

- `ARCHITECTURE.md` states that the graph-index job is enqueued in the same
  transaction that makes a revision ready, that readers validate the winning
  batch before scoring/traversal, that `SECURE_MIX` is the default internal plan,
  and that one budget spans the authorized Knowledge Spaces.
- `docs/specs/domains/assistant-and-mcp.md` makes GraphRAG the configured default
  engine and requires empty authorized evidence to remain model-free and
  citation-free.
- `PermissionAwareKnowledgeSearch` and the reconciled public contract contain no
  per-request selected Source/Asset allowlist.
- `SpringAiDocumentParser` is package-private under worker, owns a format
  allowlist, and throws worker-specific rejection exceptions. The parsing
  contracts in `components:graph-rag-core` remain framework-free.
- No second production parser caller exists. The honest module justification is
  the replaceable framework boundary and imminent CSV/HTML reader family, not a
  current Assistant caller.
- `SourceIngestionProcessor` sends an unclassified `DocumentParsingException`
  through the generic retryable `PARSING_FAILED` path. Deterministically corrupt
  input can therefore consume all five attempts.
- Parser identity is the stable
  `spring-ai-document-reader@2.1.0`; a package move can preserve pinned profile
  hashes, but unavailable pinned identity is a permanent processing failure.

## Failure scenario

An employee uploads a 40-page PDF. Source parsing finishes and the revision
becomes `READY`, so the initial design enables Send. Its graph-index job is still
queued or spending minutes in extraction. The user asks to summarize the file.
The winning batch does not contain the new Asset, retrieval produces no evidence,
and Assistant returns the correct bounded not-found response. Retrying repeats
the result; the attachment appears broken without ever presenting an error.

## Required revisions accepted by the owner design

1. Define presentation readiness as answerability under the configured engine.
   For GraphRAG, require the selected Asset's projection batch to be published.
2. Add an internal `KnowledgeEvidenceSelection` contract. The first delivery is
   selection-only; the selected set is a second ceiling after actor
   authorization and is reapplied through graph closure and citations. The
   normal global budget applies to that selected subset.
3. Require usable evidence from every selected binding before generation rather
   than letting another file hide a missing selected file.
4. Disclose that composer upload publishes to the selected Space audience. An
   actor with no `can_create_asset` Space has no attachment path in this phase.
5. Permit binding creation only as the result of the governed multipart upload;
   expose no bind-existing-Source-by-ID endpoint.
6. Preserve parser identity and byte-identical processing-profile hashes across
   a behavior-preserving module move.
7. Make deterministic parse failures non-retryable and keep channel admission
   outside the parser adapter.

## Rejected alternative

Do not add `AssistantTransientAttachment` in the first delivery. Although Onyx
proves the two-identity split can be legitimate, OrgMemory lacks the second
lane's retention, malware/DLP, egress, authorization, replay, deletion, and
disclosure model. It would invite request-thread parsing and an uncitable answer
path outside the existing citation-recheck discipline. The accepted residual
cost is explicit: all initial Assistant files are Space-visible governed
evidence, and users without Space upload permission cannot attach files.

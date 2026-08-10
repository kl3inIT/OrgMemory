# Architecture Challenge Brief: Assistant File Evidence

## Reviewer role

Act as an adversarial read-only OrgMemory architect. Your job is to find the
strongest concrete reason to reject the proposal below, not to polish it.

Read `AGENTS.md`, `ARCHITECTURE.md`, `docs/conventions.md`,
`docs/guidelines/agent-safety.md`, the `assistant-and-mcp` and
`knowledge-ingestion` domain spec/test pairs, this increment's `design.md` and
`reference-study.md`, and the current implementation paths named there. Do not
edit files, commit, push, or use Northstar.

## Commit under review

Reviewer checkout: `a43614dad6e415755c66752d308a7ab8265373c4`.

The code baseline beneath the design is
`755aab1a9a86888fc547a7bf684421f4bb1a645b`.

The branch is intentionally stacked on the completed typed-block and
`structured-block-v1` work of the Knowledge ingestion coverage increment.

## Proposed decision

1. Extract the concrete Tika/Spring AI parser from `apps:worker` into
   `integrations:document-parsing-spring-ai`; keep canonical contracts in
   `components:graph-rag-core`, channel admission in Knowledge, and processing,
   chunking, embedding, retry, and publication orchestration in worker.
2. Deliver Assistant composer upload only as governed durable evidence. It calls
   `SourceUploadService`, requires a Knowledge Space/classification, waits for
   the ordinary worker pipeline, persists a conversation binding to immutable
   Source/revision identity, rechecks authorization/readiness at turn claim, and
   retrieves only through the existing permission-scoped citable path.
3. Reject a transient turn-local file lane for the first delivery.

## Mandatory attack surface

- Is the new parser module a justified replaceable integration or premature
  modularization without a second direct parser caller?
- Can format capability remain separate from channel admission without recreating
  the current five-gate drift?
- Does a conversation binding to Source/revision identity create stale-reference,
  retirement, deletion, or revision-currentness contradictions?
- Is requiring Knowledge Space/classification too much friction for an Assistant
  attachment, and would async ingestion make the feature noncompetitive?
- Does constrained retrieval over selected sources actually preserve all current
  ACL, classification, publication, and citation checks?
- Is a dual-lane design safer or clearer if transient files can never become
  governed evidence implicitly?
- What exact invariant or test would catch possession-of-UUID authorization,
  revocation between upload and send, retry drift, and cross-tenant replay?

## Required response

Return a compact report with:

1. verdict: `ACCEPT`, `REVISE`, or `REJECT`;
2. strongest counterargument;
3. repository evidence with paths/symbols;
4. specific failure scenario;
5. required changes, if any; and
6. the rejected alternative after your verdict.

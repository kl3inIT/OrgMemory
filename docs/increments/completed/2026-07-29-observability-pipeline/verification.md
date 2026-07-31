# Observability Pipeline And Payload Boundary Verification

## Outcome

The increment opened on a production symptom: every application exported metrics
to `http://localhost:4318` — itself — once a minute and logged the failure, and
had done so since deployment. It closes with that silenced, the payload boundary
closed ahead of anything that could read telemetry, and the GraphRAG pipeline
emitting stage latency at full coverage.

Telemetry carries counts, durations and bounded enumerations. It never carries a
prompt, a completion, or an exception message. That is enforced by construction
rather than by review: `ExceptionSanitizingSpanExporter` is the last gate before
egress, `WholeExportAllowlistTests` walks every field of a real export through
it, and a companion case exports an unmodelled attribute and asserts the gate
rejects it — a gate that has never rejected anything is indistinguishable from
one that never looks.

## Delivery Evidence

The pipeline work merged across the increments recorded in
[decision 0018](../../../decisions/0018-telemetry-carries-counts-never-payload.md),
[0019](../../../decisions/0019-the-payload-boundary-is-its-own-module.md) and
[0020](../../../decisions/0020-assistant-generation-is-observed-on-its-own-payload-free-surface.md).
Phase 3 — collector and dashboards — was moved out and shipped inside
[the observability platform increment](../2026-07-30-observability-platform/verification.md).

## Production Evidence

Verified 2026-07-29 on ZM after deploying `88c35cc`: four minutes past restart,
`orgmemory-api-1` and `orgmemory-worker-1` each reported zero occurrences of
`Failed to publish metrics` and zero mentions of `4318`, against one per service
per minute before. No ERROR lines and no provider prompt-leak signatures, so the
startup boundary verifier passed against the real configuration rather than only
under test.

That check also found what the code change alone had missed. Neither
`ORGMEMORY_SERVICE_VERSION` nor `ORGMEMORY_DEPLOYMENT_ENVIRONMENT` was set on the
containers, so production labelled itself `deployment.environment.name=local`. A
misleading label is worse than an absent one. `deploy.sh` now pins the service
version to the released commit in the same rewrite that pins the image tags, so
the reported version cannot drift from the running image. Confirmed on
2026-07-30 against `b4ea630`.

## Two Questions Answered By Reading, Not Building

Both outlived the phases that raised them, and both answers were no.

**Deletion and rebuild.** The comparison that asked for a stage was reading
OrgMemory as if it deleted. It does not. `ConnectorReconciler.retire` calls
`SourceObject.archive()`, which sets `status = ARCHIVED` and returns; an update
writes a new revision and advances `current_revision_id`, leaving the superseded
chunks in place. So the question was whether five read paths honour two flags,
and they do without each having to remember: content, lexical, vector, graph and
citation all funnel through `SecureKnowledgeRetrievalStore.recheck`, assembled
from the shared `ELIGIBLE_FROM` fragment that carries both. A stage over this
would time a flag write and could never fail. The event worth emitting is the
`sameEvidence` mismatch that already diverts to `retryOrFail` when a retirement
or an edit lands against an in-flight answer, which nothing publishes.

**`finish_reason=length` counter.** The premise was wrong. `ChatModelPort` does
discard the `ChatResponse`, but Spring AI observes `ChatModel`, which sits below
the port, so production spans carry `gen_ai.response.finish_reasons` today. What
the port costs is the rate rather than the fact: API tracing samples at 0.1, so a
truncated answer is findable but its share is not measurable. Tempo held zero
non-`STOP` finish reasons over 48 hours and the assistant path sets no
`maxTokens` at all, so the change was not worth six port overloads, two
consumers, the adapter and their tests. Recorded with what would reopen it.

## Known Gaps

- The `sameEvidence` mismatch is detected and unpublished. It is the one event in
  this area with a real cause and a real failure mode.
- Superseded revisions are never reclaimed. Ten edits leave ten chunk and
  embedding sets, nine invisible and all stored, with no metric on the growth.
- The deletion finding is a reading, not the drill the hardening runbook asks
  for, and it covers the deployed classpath only — `apps/api` and `apps/worker`
  take `graph-rag-postgres` alone, so the OpenSearch and Neo4j adapters would
  need their own reading before deployment.

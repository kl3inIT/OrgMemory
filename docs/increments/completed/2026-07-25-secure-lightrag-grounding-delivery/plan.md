# Plan

- [x] Audit the LightRAG-to-Assistant handoff and obtain the independent Fable 5
  architecture review.
- [x] Add structured entity, relation, and chunk selections with
  contribution-level provenance to the pure-Java query result.
- [x] Extract one reusable pure-Java verified-context renderer.
- [x] Verify the complete selected evidence closure through OpenFGA and the
  canonical ledger before final context assembly.
- [x] Replace the chunk-only Assistant prompt and duplicate character budget
  with the verified LightRAG grounding bundle.
- [x] Add typed reranking policy, startup validation, bounded fallback, and
  sanitized telemetry.
- [x] Add focused security, parity, application-handoff, and configuration
  tests.
- [x] Run backend static analysis, focused tests, full terminating tests, and
  runtime smoke evidence.
- [x] Consolidate implemented facts into architecture/spec/test documentation
  and move this increment to `completed`.

## Required Evidence

- [x] A known entity description and relation reach the captured final
  `ChatGenerationRequest`.
- [x] Entity, relation, and chunk closure assets all appear in the final
  authorization check.
- [x] Revoked or model-mismatched contributions cannot reach context,
  citations, or the model.
- [x] Allow-all production rendering matches the pure-Java parity rendering.
- [x] The final prompt obeys the LightRAG token budget and contains no
  character-truncation marker path.
- [x] `MIX`/`CONTEXT` remains server-owned and the Assistant cannot invoke
  `BYPASS`.
- [x] Disabled reranking never invokes a provider; enabled-without-provider
  fails startup; transient provider failure preserves authorized ordering and
  emits a fallback event.
- [x] No Spring AI retrieval advisor or second vector retrieval is present in
  the Assistant path.

## Verification

- Focused graph-core, application, Assistant, and configuration tests passed.
- `.\gradlew.bat --no-daemon clean test` passed across every backend module.
- API booted from this worktree with the `dev` profile and
  `GET /api/health` returned `200 {"status":"ok","service":"orgmemory-api"}`.
- JetBrains inspection was unavailable for this worktree because the open IDE
  project is `D:\OrgMemory` and does not index the new worktree files. Gradle
  compile and the full terminating test suite were used as the static fallback.

# Adversarial Architecture Challenge - RAG Workload Routing And Luna

Attack this proposal; do not validate it by default. Work read-only: make no
edits, commits, database writes, deployments, or external mutations. Verify
claims in the repository and the pinned local references before giving a
verdict.

Read first:

- `AGENTS.md` and `CLAUDE.md`
- `docs/increments/active/2026-08-02-rag-workload-routing-luna/design.md`
- `docs/specs/domains/ai-model-control-plane.md`
- `docs/specs/domains/secure-graph-rag.md`
- `docs/decisions/0006-ai-tasks-route-through-provider-adapters.md`
- `docs/decisions/0013-full-lightrag-semantic-port.md`
- `docs/decisions/0014-lightrag-lifecycle-curation-and-cache.md`

## Product promise at stake

OrgMemory must use cheap, fast models for bounded planning/indexing work without
making graph generations non-reproducible, weakening provider isolation, or
presenting a misleading runtime control in the administration UI.

## Proposed rule

1. Add reasoning effort to chat route identity and OpenAI-compatible model
   construction; reject non-default reasoning for Anthropic instead of silently
   mapping it.
2. Default Answer to `gpt-5.6-sol`/provider-default, Keyword to
   `gpt-5.6-luna`/`none`, and candidate Graph to `gpt-5.6-luna`/`none`, with
   independent live quality gates before production activation.
3. Make Keyword organization-editable. Show Graph in the RAG pipeline UI but
   keep it deployment-managed because the current override schema cannot pin
   organization gateway-profile/credential lifecycle for queued jobs.
4. Advance GraphProcessingProfile to schema v2 so new canonical identity
   includes reasoning effort. Restore schema-v1 bytes and SHA exactly as
   provider-default.
5. Do not add reindex or runtime fallback.

## Exact questions to attack

- Is reasoning effort truly part of graph processing identity, or should it be
  an invocation-only concern?
- Can schema-v1 restoration be preserved without rewriting historical hashes?
- Is rejecting Anthropic non-default effort the correct provider-neutral
  boundary, or should effort be modeled as capabilities instead?
- Is Keyword safe to expose through the current organization route override?
- Is keeping Graph read-only justified, or can the current schema safely make
  it editable without stranding queued jobs?
- Are Luna/`none` defaults justified before the live eval, and are the proposed
  activation gates strong enough?
- Does the UI accurately distinguish immediate query changes from
  future-enqueue indexing changes?

## Evidence locations

- `core/src/main/java/com/orgmemory/core/ai`
- `integrations/ai-model-gateways/src/main/java`
- `core/src/main/java/com/orgmemory/core/knowledge/graph`
- `components/graph-rag-core/src/main/java/com/orgmemory/graphrag`
- `core/src/main/resources/db/migration/V14__ai_model_control_plane.sql`
- `apps/api/src/main/java/com/orgmemory/api/admin/AdminAiModelController.java`
- `apps/web/src/features/admin/components/admin-language-models-page.tsx`
- `D:/OrgMemory/tmp/upstream-lightrag-v1.5.4/docs/RoleSpecificLLMConfiguration.md`
- `D:/OrgMemory/tmp/onyx/backend/onyx/llm/models.py`
- `D:/OrgMemory/tmp/onyx/backend/onyx/secondary_llm_flows/query_expansion.py`
- `D:/OrgMemory/tmp/onyx/backend/onyx/indexing/indexing_pipeline.py`

## Required response

Return plain Markdown with:

1. `VERDICT`: accept, accept-with-changes, or reject.
2. `MUST-FIX`: concrete changes required before implementation.
3. `EVIDENCE`: path and line evidence for each factual claim.
4. `COUNTERARGUMENT`: the strongest architecture that should replace this one.
5. `RECOMMENDATION`: one unambiguous backend, persistence, UI, evaluation, and
   rollout boundary.

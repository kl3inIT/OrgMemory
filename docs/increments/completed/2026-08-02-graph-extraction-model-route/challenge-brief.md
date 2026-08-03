# Adversarial Architecture Challenge - Graph Extraction Model Route

Attack this proposal; do not validate it by default. Verify every claim in the
repository or pinned references. Work read-only: no edits, commits, database
writes, deployments, or plan mode. Read `CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/ai-model-control-plane.md`,
`docs/specs/domains/secure-graph-rag.md`, and scan decision filenames before
giving a verdict.

## Product promise at stake

OrgMemory is a governed organizational memory layer for enterprise AI. Its
graph must remain permission-safe, reproducible, and operationally usable:
each published graph generation must identify the exact extraction model and
prompt profile, while ingestion latency must remain practical for real
documents.

## Exact rule under review

> Default Graph Extraction to `gpt-5.4-mini`, preserve explicit deployment
> override through `ORGMEMORY_GRAPH_EXTRACTION_MODEL`, and keep Graph
> Extraction non-editable in the current AI gateway UI until changing it can
> create a new graph processing profile and present an explicit reindex scope.

Current enforcement points:

- `apps/api/src/main/resources/application.yml`
- `apps/worker/src/main/resources/application.yml`
- `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/AiGatewayProperties.java`
- `infrastructure/deployment/compose.production.yaml`
- `core/src/main/java/com/orgmemory/core/ai/AiGatewayAdministrationService.java`
- `apps/web/src/features/admin/components/admin-language-models-page.tsx`

## Comparable-system evidence

| System | Observed behavior | Source evidence |
| --- | --- | --- |
| LightRAG v1.5.4 pin (`9a45b64c`) | Default general/extraction model is `gpt-5.4-mini`; examples separate keyword (`gpt-5.4-nano`) and query (`gpt-5.4`). | `D:/OrgMemory/tmp/upstream-lightrag-v1.5.4/env.example:551`, `:578`, `:585`; `docs/RoleSpecificLLMConfiguration.md:262-274` |
| Onyx local pin | Index-affecting settings use an explicit reindex workflow; the UI warns that changes can take hours or days and offers switch/reindex strategies. Vision-model changes apply only to documents indexed going forward. | `D:/OrgMemory/tmp/onyx/web/src/views/admin/IndexSettingsPage/index.tsx:991-1051`, `:1595` |
| OrgMemory | Graph jobs pin an immutable processing profile; only Assistant Chat and Prompt Execution are editable through the current admin service/UI. | `docs/specs/domains/secure-graph-rag.md`; `docs/specs/domains/ai-model-control-plane.md`; `core/src/main/java/com/orgmemory/core/ai/AiGatewayAdministrationService.java` |

## Operational evidence

On ZM production on 2026-08-02, `gpt-5.6-sol` graph extraction took 39-261
seconds per chunk. Concurrency four produced transient `OpenAIIoException`
failures. Concurrency two completed all 46 graph stages without restart, but
the final eleven-chunk document still took roughly fourteen minutes. The live
gateway `/models` catalog advertises `gpt-5.4-mini`, `gpt-5-mini`,
`gpt-5.4-nano`, and `gpt-5.4`.

## Required verdict

Return plain Markdown with:

1. `VERDICT`: accept, accept-with-changes, or reject.
2. `MUST-FIX`: concrete items before implementation.
3. `EVIDENCE`: repository/reference paths for every factual claim.
4. `COUNTERARGUMENT`: the strongest case for `gpt-5-mini`, retaining
   `gpt-5.6-sol`, or making the route UI-editable now.
5. `RECOMMENDATION`: one unambiguous model and UI boundary.

# Graph Extraction Model Route

## Problem

Production graph extraction currently defaults to `gpt-5.6-sol`. During the
2026-08-02 ingestion incident, extraction calls took 39-261 seconds per chunk;
four concurrent calls also produced transient `OpenAIIoException` failures.
Reducing concurrency to two restored correctness, but an eleven-chunk document
still needed about fourteen minutes to finish graph extraction.

The model route is also easy to misunderstand in the admin UI. Assistant Chat
and Prompt Execution are runtime-editable, while Graph Extraction is
deployment-managed because every graph job pins an immutable processing
profile. A bare model dropdown would hide whether existing content is rebuilt.

## Proposal

Change only the Graph Extraction default to `gpt-5.4-mini`, while preserving
the configured Assistant and Keyword Planning routes. Keep Graph Extraction
read-only in the current AI gateway UI. A future editable surface must create
a new graph processing profile and make the reindex scope explicit.

This follows the pinned LightRAG v1.5.4 role split: a mini-class model for
extraction, a nano-class model for keyword planning, and a stronger model for
query response. It also keeps deployments free to override the default with
`ORGMEMORY_GRAPH_EXTRACTION_MODEL`.

## Strongest counterargument

Use `gpt-5-mini`, matching the role-specific example in current upstream
documentation, and expose Graph Extraction beside chat workloads so operators
can tune it without editing deployment configuration. This would be familiar
and immediately flexible.

## Rejected alternative

The UI change is rejected in this increment. OrgMemory graph generations pin
the extraction model and prompt profile; changing a dropdown without a
profile/reindex workflow would affect only later jobs and leave mixed graph
generations. Onyx likewise treats indexing-affecting settings as an explicit
reindex lifecycle rather than an ordinary chat-model preference.

`gpt-5-mini` is not selected as the default because the pinned parity oracle's
actual v1.5.4 environment default has already advanced to `gpt-5.4-mini`, and
the production gateway advertises that exact model. The deployment override
remains available if live quality evidence later favors another model.

## Scope

- Change graph-extraction defaults and production examples only.
- Add focused configuration coverage and consolidate current behavior docs.
- Update the ZM deployment override and verify a new graph job uses the route.
- Do not change prompts, graph schema, assistant models, keyword models, or
  editable-workload policy.

## Architecture challenge

Status: **accepted with changes**. The review required a genuinely independent
fallback, root environment example coverage, route-separation tests, and a
bounded canary before activation. See [challenge-verdict.md](challenge-verdict.md).

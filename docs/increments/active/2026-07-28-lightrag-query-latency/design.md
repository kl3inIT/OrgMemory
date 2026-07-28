# LightRAG multi-space query latency

Date: 2026-07-28

## Outcome

Restore the pinned LightRAG `v1.5.4` per-query execution invariants across
OrgMemory's permission-equivalent Knowledge Space snapshots, then remove the
application- and PostgreSQL-specific amplification that makes a small
permission-aware MCP search take about twenty seconds.

The increment keeps the existing fail-closed authorization sequence:

```text
organization search check
-> initial OpenFGA ListObjects and canonical scope
-> permission-filtered GraphRAG
-> current-scope comparison
-> final OpenFGA BatchCheck and canonical evidence recheck
-> audit and delivery
```

No latency change may weaken, sample, cache past, or omit either final
authorization boundary.

## Production evidence

Production runs main SHA `0c13551f29a2b83b29fb88e042cfe158e5712b73`
with seven eligible Knowledge Spaces for the observed actor.

One representative request recorded:

- initial OpenFGA `ListObjects`: 7 ms;
- current-scope `ListObjects`: 6 ms;
- final `BatchCheck`: 389 ms;
- elapsed time between the two scope resolutions: about 19.6 seconds.

The application calls `LightRagQueryEngine.execute` sequentially once per
Knowledge Space. Every call repeats keyword generation through the
`ASSISTANT_CHAT` route and repeats the query/low-keyword/high-keyword embedding
batch. Seven spaces therefore make seven keyword-model calls and seven
embedding batches before the application consolidates grounding.

The pinned upstream source and the completed semantic-port design instead
perform keyword planning once and pre-compute the distinct active query
embeddings in one provider batch for one logical query.

The production PostgreSQL projection contains 6,837 vector records and has a
1536-dimension HNSW index. The current SQL orders by
`1 - cosine_distance DESC`; pgvector requires the raw distance operator in
ascending order for an approximate index. A production `EXPLAIN ANALYZE`
therefore used the filtered B-tree path and explicit sort. It completed in
about 3.4 ms at the current size, so this is a scale defect rather than the
primary cause of the current twenty-second request.

## Decisions

### Prepare one logical query once

Split effectful query preparation from snapshot-scoped retrieval:

```text
query + options + embedding profile
  -> keyword plan
  -> one distinct embedding batch
  -> immutable prepared query

prepared query + authorized scope + pinned snapshot
  -> local/global/vector candidate retrieval
```

The existing one-shot `execute(request)` remains as the compatibility entry
point and delegates through the same preparation contract. The API application
shell prepares once, then reuses the immutable result for every eligible
snapshot.

The prepared query contains no authorization decision, evidence, or mutable
provider object. Reuse is allowed only when the normalized query, options,
embedding profile and dimensions are identical.

### Cache only exact keyword planning in this latency repair

Wire the existing exact `ModelInvocationCache` into keyword planning. The key
binds organization, normalized query, language, query strategy, keyword model
route and prompt-profile fingerprint. Keyword output does not depend on a
Knowledge Space publication generation, so it uses an organization-level
query namespace rather than an arbitrary first space.

Trusted caller keywords bypass both the provider and cache. Invalid, expired,
or malformed cached payloads are misses, never partial plans.

The existing permission-scoped retrieval-result cache remains outside this
increment. Its current key binds one snapshot; safely caching a composed
multi-snapshot result requires the explicit multi-snapshot cache contract
already required by ADR 0013. Preparation reuse removes the current dominant
provider amplification without inventing that contract.

### Add a dedicated keyword workload route

Add `KEYWORD_PLANNING` as a chat-capable AI workload and an independent route.
The default may inherit the configured assistant gateway and model for backward
compatibility, while production can select a smaller structured-output-capable
model without changing retrieval code.

The cache route fingerprint includes the resolved gateway and model identity,
so a route change cannot reuse stale keyword output.

### Preserve deterministic multi-space composition

Snapshot reads may run with bounded concurrency only after query preparation is
side-effect free and the outer long-lived database transaction is removed.
Results are associated with their namespace and sorted by namespace before
deterministic consolidation. Cancellation or failure of any required snapshot
fails the request closed; partial cross-space evidence is never delivered.

Reranking and the final context budget belong after global candidate
consolidation. Production reranking is currently disabled. If the candidate
contract cannot be safely lifted in this increment, bounded concurrency may
ship with multi-space reranking disabled by a fail-closed runtime invariant and
global rerank remains
an explicit incomplete exit item rather than being reported as fixed.

### Shorten database transaction boundaries

Remove the transaction around the complete provider-backed search. Repository
and projection operations retain their own bounded read transactions. The
application must not hold one Hikari connection while waiting on OpenFGA,
keyword generation, embeddings, or independent snapshot searches.

Bulk-load current ACL generations for all visible assets grouped by Knowledge
Space. The second scope resolution remains mandatory, but it must not issue one
ACL-generation query per space.

### Make vector ordering indexable

Order vector candidates by the raw cosine-distance operator ascending and
derive similarity only in the selected output. Apply the similarity threshold
without changing stable record-id tie breaking. PostgreSQL integration tests
must prove ranking parity and inspect an index-eligible SQL shape; production
`EXPLAIN` remains a release check because the planner may correctly prefer an
exact filtered scan for small batches.

### Observe the stages that decide latency

Emit payload-free timings for:

- initial authorization scope;
- keyword planning, including cache hit/miss;
- query embedding;
- each snapshot retrieval with only a hashed namespace identity;
- global consolidation;
- final authorization recheck.

Do not label the duration of a complete engine invocation as reranking. Prompt,
query, keyword, evidence text, titles and source identifiers remain excluded
from telemetry.

## Non-goals

- Removing or weakening OpenFGA or canonical evidence rechecks.
- Adding a semantic answer cache.
- Replacing the pinned LightRAG algorithms with heuristics.
- Changing the MCP timeout repair increment; its timeout chain remains a
  separate availability guard.
- Selecting OpenSearch or Neo4j for production before measured comparison.
- Adding a general distributed execution framework.

## Exit proof

- A multi-space regression test proves one keyword-model invocation and one
  embedding batch for any number of eligible snapshots.
- Repeating the same exact query proves a keyword cache hit; changing route,
  language, strategy, organization, or query proves a miss.
- Trusted keywords prove zero keyword-provider calls.
- Permission tests still prove denied, stale, changed and cross-tenant evidence
  never reaches grounding or citations.
- The complete search no longer holds one transaction across provider calls.
- ACL generation resolution is one bulk query per scope resolution.
- PostgreSQL vector ranking remains deterministic and uses an index-eligible
  distance ordering.
- Stage timings distinguish provider, snapshot-store and authorization time.
- Focused tests and the terminating `clean test` gate pass.
- The merged production image completes the same authenticated MCP search and
  captures a before/after latency trace without storing prompt or evidence
  content.

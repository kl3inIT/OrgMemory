# Independent Architecture Challenge: AGE Published-Batch Backfill

## Reviewer mandate

Review this proposal adversarially and read-only. Inspect the cited repository
evidence yourself. Do not edit files. Disagree wherever the evidence supports
it, identify the smallest safe contract, and return a structured verdict with:

1. `VERDICT`: accept, accept-with-mandatory-fixes, or reject;
2. `MUST-FIX`: numbered binding changes;
3. `STRONGEST COUNTERARGUMENT`;
4. `REPOSITORY EVIDENCE`: exact file paths and symbols;
5. `REJECTED ALTERNATIVE`: the best alternative and why it loses;
6. `TEST OBLIGATIONS`;
7. `RESIDUAL RISKS`.

## Incident

Production has a generation-7 `PUBLISHED` batch whose required projections
include `GRAPH`. Its relational canonical graph rows exist. Apache AGE 1.8.0 is
installed and preloaded, but `ag_catalog.ag_graph` has no organization graph.
AGE reads therefore fail closed with `Apache AGE graph is unavailable for
published batch ...`, breaking Graph explorer and Assistant retrieval.

## Proposal

Implement an explicitly enabled, bounded API startup reconciler for the
`APACHE_AGE` backend. It enumerates retained published `GRAPH` snapshots in
stable pages; fails startup when a configured maximum would be exceeded;
validates each snapshot against publication authority; skips an exact unique
ready marker; otherwise rebuilds only that batch from immutable relational
graph rows under the existing tenant advisory lock, writes the marker last,
and verifies it. It changes no head, generation, receipt, job, source, chunk,
embedding, or model route. Production enables it before the worker can start.

## Questions to attack

- Is startup reconciliation the right operational boundary, or must this be a
  dedicated one-shot command?
- Is scanning all retained published graph snapshots required, or only current
  namespace heads?
- Can this race a publication or expose a partially rebuilt published batch?
- Is a marker-only readiness proof sufficient, including marker mismatch or
  duplicate-marker cases?
- Does reconstructing `ProjectionBatch` from the publication/batch ledger
  preserve every invariant needed by `ApacheAgeBatchTopology.rebuild`?
- What must happen when the ceiling is reached, AGE is partially available, or
  a relational published batch is internally incomplete?
- Does running this in API startup violate deployment rollback or availability
  expectations more than a one-shot service would?

## Required repository reading

- `AGENTS.md`, `CLAUDE.md`, `docs/conventions.md`
- `docs/guidelines/agent-safety.md`
- `docs/decisions/0028-durable-cross-store-publication-permits.md`
- `docs/decisions/0030-explicit-apache-age-topology-backend.md`
- `docs/decisions/0031-least-privilege-apache-age-preload-probe.md`
- `docs/specs/domains/secure-graph-rag.md`
- `docs/tests/domains/secure-graph-rag.md`
- `integrations/graph-rag-postgres/.../PostgresProjectionPublicationStore.java`
- `integrations/graph-rag-postgres/.../ApacheAgeGraphStore.java`
- `integrations/graph-rag-postgres/.../ApacheAgeBatchTopology.java`
- `integrations/graph-rag-postgres/.../PostgresGraphRagAutoConfiguration.java`
- `infrastructure/deployment/compose.production.yaml`
- `infrastructure/deployment/scripts/deploy.sh`

## Constraints

- No silent topology fallback.
- No LLM extraction or embedding rebuild when exact relational topology exists.
- No read-triggered mutation.
- No unbounded corpus or publication scan.
- Preserve historical published-snapshot addressability.
- Default behavior outside production must remain non-mutating.

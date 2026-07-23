# Secure GraphRAG PostgreSQL projection

Status: completed on 2026-07-23.

## Outcome

Persist the framework-neutral graph contracts through a PostgreSQL all-in-one
backend inspired by LightRAG `v1.5.4`, without making any storage adapter an
authorization authority.

The implementation preserves all four LightRAG storage roles:

- relational PostgreSQL for source lifecycle, cache/status and contribution ledger;
- pgvector for chunk, entity and relation embeddings;
- Apache AGE for graph topology and Cypher traversal;
- published generations for document/projection status and recovery.

OrgMemory adds a canonical evidence contribution ledger and permission-scoped
queries that LightRAG does not provide. A future Neo4j adapter may implement the
same topology/read ports as another rebuildable projection.

## Invariants

- Canonical entity and relation rows contain identity only.
- Descriptions, keywords, confidence and extractor provenance remain on immutable
  evidence contributions.
- Every contribution references a real active ingestion tuple: organization,
  Knowledge Asset, source revision, chunk, ACL snapshot/generation and projection
  generation.
- A revision is visible only through its published graph head.
- Replacing a revision is atomic. Readers see the previous complete generation or
  the replacement complete generation.
- Projection generations cannot move backwards. Rebuilding the same generation is
  allowed for deterministic retries.
- Every read is tenant-scoped and filters by the caller's already-authorized
  Knowledge Asset IDs before aggregation, ranking and LIMIT.
- A relation is visible only when its contribution and both endpoint entities have
  visible evidence.
- Chunk, entity and relation vector stores use the same immutable organization
  embedding profile. Query/document embeddings cannot silently diverge.
- PostgreSQL FTS remains a deterministic fallback and lexical channel; pgvector
  supplies semantic graph seeds.
- Apache AGE stores topology identity only. Evidence content, ACL generations and
  authorization remain in the relational contribution ledger.
- AGE traversal may produce candidates only. Relational authorization filtering and
  canonical recheck happen before aggregation/citation.

## Storage boundary

The PostgreSQL integration contains the relational contribution store, pgvector
embedding index and AGE topology projection behind framework-neutral ports. Every
read receives an `AuthorizedGraphScope`; no adapter derives or expands permissions
from actor attributes. OpenFGA and the canonical ACL ledger remain upstream.

The local PostgreSQL image pins PostgreSQL 18, pgvector 0.8.2 and Apache AGE 1.7.0.
Vector index strategy is replaceable (exact, HNSW, halfvec HNSW, IVFFlat, and
VChordRQ when its extension is installed); HNSW cosine at 1536 dimensions is
the initial profile. Configuration is type-safe under
`orgmemory.graph-rag.postgres`.

Local Compose runs one PostgreSQL server and volume. OrgMemory and OpenFGA use
separate databases and logins on that server so operational consolidation does
not collapse schema ownership. The bootstrap is idempotent for existing volumes.

All writes are bounded by record count and estimated payload size. AGE traversal
is bounded to five hops and filters every edge against the authorized Knowledge
Asset set; a recursive relational query implements the same candidate port when
AGE is disabled. Candidate IDs are never evidence and must be reloaded through
the permission-scoped relational reader.

Neo4j, if later justified by measured multi-hop or graph-algorithm workloads, will
consume the same published projection and implement the same read ports. Neither AGE
nor Neo4j owns ACL, provenance or canonical source state.

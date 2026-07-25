# Secure Knowledge Vertical Slice Design

## Outcome

A user uploads one approved document, the worker produces permission-aware
knowledge, and two users with different source grants receive different grounded
answers through the in-app agent. Revocation closes access. The new minimal web
surface shows Ask, Sources, and Review without depending on old page parity.

## Scope

- Additive source object/revision/blob split and direct-upload contract.
- MinIO adapter first behind a provider-neutral object-storage port. Original
  evidence remains immutable and the database stores only its locator and
  integrity metadata.
- Worker-owned quarantine, validation, parse, normalize, chunk/embed, and publish.
- External source-principal mapping and OpenFGA tuple/index convergence.
- PostgreSQL FTS + pgvector permission-before-limit retrieval.
- Provider-neutral AI task plus OpenAI-compatible adapter.
- Read-only in-app agent with grounded citations.
- Minimal replacement UI for Ask, Sources, and Review in light/dark themes.

MCP publication, secure graph retrieval, connector runtime, mutation tools, and
pilot operations are explicitly outside this increment.

## Boundary

```mermaid
flowchart LR
    UI[Ask Sources Review] --> API[API commands and queries]
    API --> CORE[core use cases]
    CORE --> LEDGER[(PostgreSQL ledger)]
    API --> BLOB[MinIO object-storage adapter]
    WORKER[worker stages] --> CORE
    WORKER --> BLOB
    LEDGER --> OUTBOX[outbox]
    OUTBOX --> FGA[OpenFGA]
    OUTBOX --> IDX[FTS pgvector]
    FGA --> RETRIEVE[SecureKnowledgeRetrieval]
    IDX --> RETRIEVE
    RETRIEVE --> AGENT[in-app agent]
```

Each external adapter module is added only with its first real contract,
implementation, and contract test. No empty future modules are created.

The first embedding projection uses OpenAI `text-embedding-3-large` at 1536
dimensions so its PostgreSQL HNSW index remains within pgvector's index limits.
The canonical profile, generation, query-pinning, and migration rules live in
[ADR 0008](../../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
and the [knowledge-ingestion specification](../../../specs/domains/knowledge-ingestion.md).

Canonical evidence and rebuildable retrieval projections are separate:

- MinIO keeps immutable evidence bytes; `SourceObject`, `SourceRevision`,
  `EvidenceBlob`, and durable jobs are the canonical ingestion ledger.
- PostgreSQL keeps normalized full-document records and versioned chunk/vector
  projections now.
- Entity vectors, relationship vectors, and the permission-scoped graph are
  later projections of the same revisions, not new sources of truth.

## Exit Criteria

- Upload and processing are idempotent and observable.
- ACL ledger, OpenFGA tuple, and search-index versions converge before search.
- Permission filtering occurs before ranking/limit/model context.
- Missing/denied resource metadata does not leak and citations are rechecked.
- Two-user allow/deny and revocation E2E tests pass.
- The new UI completes the flow in both themes and old routes are not extended.

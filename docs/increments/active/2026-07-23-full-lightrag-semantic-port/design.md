# Full LightRAG Semantic Port Program

## Outcome

Port the complete LightRAG `v1.5.4` semantic engine and runtime capability set
to Java while preserving OrgMemory's stricter evidence-level authorization.

The twelve PRs integrate through the remote `light-rag` branch. No program PR
targets `main` directly. After all twelve PRs pass their completion audit, one
final `light-rag -> main` PR carries the complete program.

## Invariants

- `graph-rag-core` remains pure Java.
- `graph-rag-core` contains executable LightRAG semantics, not an
  interface-only abstraction layer.
- Spring AI and vendor SDKs are adapters; Spring Boot is the imperative
  runtime shell.
- PostgreSQL is the first production adapter, not a permanent core assumption.
- Every adapter implements a shared port and conformance suite.
- The full engine supports all upstream query strategies; product delivery
  applies a separate secure policy and default.
- Canonical evidence, ACL, provenance, lifecycle, and audit remain in
  PostgreSQL.
- Derived stores are rebuildable and cannot grant access.
- No PR closes a parity row using compilation alone.
- Core orchestration is synchronous and Reactor-free. The runtime may use
  bounded virtual threads for blocking adapters and Reactor at streaming
  delivery boundaries.
- Material architecture decisions use the repository's Fable 5 debate rule
  before code is committed.
- Publication retries bind batch identity and namespace-scoped idempotency to
  one canonical manifest fingerprint persisted on the visible snapshot.
- Durable preparation receipts live beside the publication head. Publication
  verifies every required kind; readers validate pinned snapshots against
  immutable publication history rather than trusting caller-created state.

## Branch And Review Flow

```text
origin/main
    |
    +-- light-rag
          |
          +-- PR 1 branch --review/CI--> merge to light-rag
          |
          +-- PR 2 branch --review/CI--> merge to light-rag
          |
          +-- ... PR 12
          |
          +-- final reviewed PR ------------------------> main
```

Each next branch starts from the latest remote `light-rag` after the preceding
merge. CodeRabbit findings are fixed when actionable. A PR merges only after all
required GitHub checks are green and no unresolved actionable review thread
remains.

## Scope Authority

[Decision 0013](../../../decisions/0013-full-lightrag-semantic-port.md) and the
[parity manifest](../../../research/lightrag-v1.5.4-parity-manifest.md) define
the program scope. Earlier completed increments are historical evidence, not
reasons to omit a manifest row.

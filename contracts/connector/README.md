# Connector Staging Contract

The versioned wire shape a connector produces and `ConnectorIngestionService`
consumes. It decouples *how* a source is crawled (Slack Web API, a fixture, some
future adapter) from *how* OrgMemory governs the result. A connector's only job is
to emit batches in this shape; everything downstream — identity mapping, sealed
ACL generations, retrieval — is source-agnostic.

- [`crawl-batch.schema.json`](crawl-batch.schema.json) — JSON Schema (draft
  2020-12) for a single crawl batch. The Java mirror is
  `com.orgmemory.core.knowledge.ConnectorCrawlBatch` and its records.

This is a wire contract, not a Gradle module. Fixture batches under
`demo/fixtures/connector/` validate against this schema.

## Shape

A batch is an envelope plus four independently-versioned payload kinds and
tombstones:

- **identity** (`identity/v2`) — users and groups the crawl observed, identified
  by a stable provider-native principal ID. Email and display name are mutable
  observations. Observation alone grants nothing.
- **membership** (`membership/v1`) — independently captured group membership.
  Only sealed `COMPLETE` evidence may advance the active group head;
  `INCOMPLETE` evidence is retained with a reason and cannot widen access.
- **content** (`content/v1`) — objects (Slack messages/threads rendered to text).
  `contentRevision` is the sole idempotency key on the content path.
- **permissions** (`permissions/v1`) — per-object grants (`ALLOW`/`DENY` for a
  source user or group). Sealed as source ACL evidence for one generation.
- **tombstones** — objects removed at the source; the matching `SourceObject` is
  retired out of retrieval while its evidence is retained.

Content, permissions, and membership carry independent cursor/completeness
states. A membership-only re-crawl advances the group membership head without
rotating a resource ACL, revising content, or rebuilding embeddings. A
truthfully `INCOMPLETE` component advances its observation for health reporting
but never its last-successful authorization state.

## Versioning — fail closed

Each payload kind is versioned separately (`versions.content`,
`versions.identity`, `versions.membership`, `versions.permission`). The build
understands exactly the versions in `ConnectorContractVersions.supported()`.
Any other value is rejected with `UnsupportedConnectorPayloadException` — an
unrecognized shape is never guessed at or partially applied. Bump one payload
version to evolve its shape without touching the others.

## Guarantees the connector cannot violate

- It only translates what the payload states; it can never widen access beyond the
  declared grants.
- Unmapped principals grant nothing.
- No admin path can broaden a sealed generation.
- Effective access stays the intersection of tenant, current sealed source-ACL
  generation, OpenFGA policy, classification, and lifecycle.

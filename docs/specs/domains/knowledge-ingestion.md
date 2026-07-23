# Knowledge Ingestion Spec

## Current Behavior

An authenticated user can upload PDF, DOCX, PPTX, TXT, or Markdown through the
Documents API and web view. The user must select a Knowledge Space returned by
OpenFGA `ListObjects(can_create_asset)`, and the mutation rechecks
`can_create_asset` before writing evidence. MinIO stores immutable evidence bytes
behind the provider-neutral object-storage contract. PostgreSQL persists canonical
`SourceObject`, `SourceRevision`, `EvidenceBlob`, and leased durable ingestion
jobs with the target Knowledge Space identity.

The worker validates content integrity, parses and normalizes text through the
Spring AI ETL readers, chunks it, and requests embeddings. Publication first
commits a `PENDING` Knowledge Asset, inactive pgvector chunks, and a publication
outbox row in one database transaction. It writes the target
`knowledge_space#space` and uploader `owner` relationships to OpenFGA in one
idempotent request with duplicate-ignore semantics against the pinned model, then atomically
marks the outbox `APPLIED`, activates the asset/chunks, and publishes the ready
revision. Unknown or unavailable authorization writes fail closed and use the
leased ingestion job's durable retry path. The first verified profile is OpenAI
`text-embedding-3-large` at 1536 dimensions with cosine distance.

Every vector references an immutable organization-scoped `EmbeddingProfile`.
The vector column supports multiple dimensions, while each search and index
route is profile-specific and every supported dimension receives its own
partial expression index. Canonical source and evidence records remain
independent from rebuildable full-document, chunk, entity, relationship, and
graph projections.

Public and internal upload ACL evidence grants the organization; confidential
upload ACL evidence grants the target Space's department and therefore requires
a department-bound Space. Restricted upload evidence remains organization-bound
while the independent classification gate limits retrieval to executives.
Effective retrieval remains the intersection of Space authorization,
immutable/current source ACL, classification, tenant, and lifecycle state.

External source principals observed from a source are recorded in a
`source_principals` registry (observation grants nothing) and resolved to active
internal users through a verified `source_principal_mappings` ledger. Automatic
matching runs a trusted issuer/subject IdP join first, then an email join that
requires the address to be vouched for by either signal: the principal's own
`sso_verified` flag as the crawl reported it, or an administrator's standing
`SSO_VERIFIED` decision for the connection in `source_connections` (`UNTRUSTED` by
default, and an absent row reads as untrusted). The connection decision widens the
tier rather than gating it, so a source that confirms address ownership before an
account can exist keeps matching without a manual step while a source that cannot
vouch needs an administrator to. The bind records which signal carried it.
Unverified tails use explicit self-claim or admin confirmation. Each
mutation keeps at most one active mapping per principal and appends a permission
audit event. A `SOURCE_USER` ACL entry grants only through an active mapping to
the querying user; a `SOURCE_GROUP` entry grants only through that snapshot's
sealed group membership joined to an active mapping. Any unmapped, revoked, or
inactive principal grants nothing. Per [ADR 0009](../../decisions/0009-dynamic-source-acl-ceiling.md),
live (non-upload) sources enforce the current sealed ACL generation as the
ceiling while upload sources keep the ingestion-current intersection.

A Slack connector ingests a versioned crawl contract
(`contracts/connector/`: three separately-versioned payload kinds — content,
identity, permissions — plus tombstones, an opaque crawl cursor, and a
completeness claim) through a
dedicated `ConnectorIngestionService`. It observes and matches external
principals, then seals ACL generations carrying `SOURCE_GROUP`/`SOURCE_USER`
evidence and channel membership through package-private connector-aware
seal/rotate methods on `KnowledgeIngestionService` that the public upload path
does not expose (the upload path still rejects external principals). A new object
materializes content by reusing the `normalize` and `publish` use cases, with
chunks embedded through a `ConnectorTextEmbedder` port; a membership re-crawl
appends a sealed generation and rotates the head without re-materializing content,
converging grants and revocations under the [ADR 0009](../../decisions/0009-dynamic-source-acl-ceiling.md)
live-source ceiling; a changed content revision materializes a new current source
revision on the same object, which leaves the superseded text unanswerable because
retrieval only serves chunks belonging to the current revision; a tombstone retires
the `SourceObject` from retrieval, and a retired object refuses a later content
revision rather than reviving itself. Each
object reconciles in its own transaction so a per-object failure is isolated, and
an unknown payload version fails closed. The driver consumes every
`ConnectorBatchSource` bean, so committed fixtures and a live workspace can both
feed it and a source that is rate limited or unreachable this poll does not stop
the others.

`integrations:connectors` holds the live adapters, one package per source, so a
source SDK or wire shape never reaches `core` or an app. Its Slack adapter crawls
`conversations.list`/`history`/`replies`/`members` and `users.list` through Spring
`RestClient`, keyed on `channelId__threadTs` with a rendered-text hash as the
content revision. Slack answers `200` for logical failures, so success is the
`ok` field rather than the status; a collection ends only on an empty
`next_cursor`; and a `429` is honoured as a wait recorded once and applied before
every subsequent request with jitter, because the limit belongs to the workspace
rather than the caller. A single worker holds that deadline in process; more than
one would need it shared. The bot token is resolved per connection from
configuration, travels in the `Authorization` header, and appears in no log,
message, or properties description. The adapter reports observed users as
SSO-verified because Slack confirms address ownership before an account can
exist. It withdraws its completeness claim whenever a channel filter is
configured, private channels prove out of scope, a channel cannot be read, or a
channel exceeds its thread bound — each of which is indistinguishable from a mass
deletion downstream. A slim ID+ACL-only pull and a whole-crawl failure threshold
are not implemented.

Deletions carry no tombstone of their own, so a batch may declare that its content
and permission payloads enumerate everything the connection currently has; only
such a batch retires the objects it omitted. The claim is absent-means-no. A
complete crawl that enumerated nothing at all while the connection has indexed
objects is refused and reported, because a revoked token is indistinguishable from
an emptied workspace and retiring a whole connection is the more expensive
mistake. Driver progress is checkpointed per connection in
`connector_crawl_checkpoints` so a restart resumes rather than replays; a batch
rejected for a reason retrying cannot change is checkpointed past, and any other
failure is retried a bounded number of times and then left for the next poll.

The current path does not yet implement incremental webhooks or the Events API,
a slim ID-and-ACL-only crawl, an encrypted per-connection credential store,
Airbyte staging, OCR, malware and DLP integrations, entity and relationship
extraction, graph publication, or hybrid retrieval extensions beyond the current
secure FTS + pgvector path.

## Source Modules

- `core.knowledge`
- `apps.api.source`
- `apps.worker.ingestion`
- `apps.worker.connector`
- `integrations.connectors` (live source adapters)
- `contracts/connector` (staging contract)
- `integrations.object-storage-minio`

## Related Decisions

- [0004](../../decisions/0004-manual-upload-is-a-first-class-source.md)
- [0005](../../decisions/0005-secure-java-graph-kernel.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
- [0009](../../decisions/0009-dynamic-source-acl-ceiling.md)

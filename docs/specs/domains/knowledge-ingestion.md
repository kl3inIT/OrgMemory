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
sources whose access rule the source itself owns enforce the current sealed ACL
generation as the ceiling, while sources OrgMemory holds the rule for keep the
ingestion-current intersection.

Which of the two an object follows is `source_objects.acl_authority`
(`SOURCE`/`ORGMEMORY`), and which system it came from is `source_objects.source_system`
(`slack`, `upload`). One column used to answer both, so every new connector needed
DDL to widen a check constraint guarding a distinction the source's name has
nothing to do with. The authority is recorded at ingestion and never updated: it
is what was true when the evidence entered, not a policy an administrator can
change afterwards. The system is governed by the connector registry rather than a
constraint — a `ConnectorSourceProfile` bean contributed by an adapter declares
the name, display name, classification, declared access, object type and media
type, `ConnectorSourceRegistry` refuses a name no adapter claimed and refuses two
adapters claiming one name, and nothing in `core` names a source.

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
one would need it shared. The bot token is resolved per connection from the
ledger, travels in the `Authorization` header, and appears in no log,
message, or exception. The adapter reports observed users as
SSO-verified because Slack confirms address ownership before an account can
exist. It withdraws its completeness claim whenever a channel filter is
configured, private channels prove out of scope, a channel cannot be read, or a
channel exceeds its thread bound — each of which is indistinguishable from a mass
deletion downstream, and it abandons a run in which most channels could not be
read rather than reporting it as a crawl. Slack markup — mentions, channel links,
group handles, escaped characters — is resolved to readable text before indexing,
because an opaque identifier cannot match the name a question would use, and a
thread is emitted once even when a reply broadcast back to its channel surfaces
it twice.

Between content crawls the adapter produces a permissions-only batch instead:
channels and their members from Slack, applied through `ConnectorObjectDirectory`
to the objects the ledger already holds. That costs a call per channel rather than
a call per thread, which matters because access changes daily and content rarely.
Such a batch never claims completeness — its object list is OrgMemory's own record
rather than the source's, so the claim would confirm itself — and it omits any
object whose channel the crawl could not see, since an empty grant list would
assert that nobody may read it.

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

What each pass did is a row in `connector_crawl_attempts` rather than a log line,
with an outcome, per-object counts, and an error code and message. The four
outcomes are kept apart because they call for different actions: `SUCCEEDED`
reconciled, `REJECTED` was checkpointed past and is not coming back, `FAILED` is
still queued, and `UNAVAILABLE` means the source produced no batch for that
connection at all — which is what a revoked or missing credential looks like, and
which needs `ConnectorBatchSource.pendingBatches` to return a `ConnectorPoll`
carrying the connections it could not read, because a driver that only sees
batches has nothing to record for the failure that produces none. Recording runs
in its own transaction, since the ingesting one is already marked for rollback
when it matters. The error message is a diagnostic: adapters authenticate through
a header and report the method and the source's error code, so no credential has
ever reached it.

Which connections are crawled is a ledger decision rather than a deployment one.
`source_connections` carries the configuration every source has — enabled, target
Knowledge Space, actor, content interval — as columns with check constraints, plus
`source_config jsonb` holding whatever only that source understands. The split is
by what the database can check: a crawl must have a Space and an actor, and an
interval must be positive, and those stay enforceable; Slack's channel list and
thread bound are opaque to the ledger and parsed by the adapter that defined them.
`source_connection_credentials` carries the token as AES-256-GCM ciphertext with
the key version that produced it. A row whose authentication tag does not verify
is refused rather than decrypted, and the application refuses to store a secret at
all when no encryption key is configured, rather than storing something weaker.

An administrator sets both through `/api/admin/connectors/{sourceSystem}`, one
endpoint for every source rather than one per source; a source system no adapter
contributed is a `400` rather than an empty list, so a typo does not read as "you
have no connections". `GET /api/admin/connectors/sources` reports what this
deployment can actually ingest, and
`GET /api/admin/connectors/{sourceSystem}/{connectionKey}/activity` reports what a
connection has done — objects retrievable and retired, last checkpoint, and recent
attempts. Checking a credential is a `ConnectorCredentialProbe` each adapter
contributes beside its profile, resolved by registry, so the API selects nothing
by name; probing is a separate registry from the source one because a source can
be ingestible without being checkable. The credential is write-only: it is
submitted, and no endpoint returns it in any form, masked or otherwise.
`POST /test` checks a credential before it is stored, reporting what the
connection will be keyed on — a Slack workspace id, a Google Workspace domain — so
an administrator is told the key rather than asked to find it. Every probe answers
"does it authenticate" and "can it read" separately, because the second cannot be
inferred from the first: Slack follows `auth.test` with a one-channel
`conversations.list`, and Drive follows its token exchange with a one-file
listing. A Slack app installed without `channels:read`, or a service account
nobody has shared anything with, authenticates perfectly and then indexes nothing
— hours later, as a failure nobody connects to the day it was configured. The
adapters read connections and credentials on
every poll through `ConnectorConnectionDirectory`, so enabling a workspace,
repointing it, or replacing its token takes effect on the next poll; the adapter
bean is present wherever the module is and produces nothing until a connection
says otherwise, and a connection that cannot produce is skipped — but reported,
not swallowed — rather than allowed to end the poll for the others.

The browser side is generic in the same way. A catalogue lists what OrgMemory
governs, grouped by whether the access rule comes from the source or from
OrgMemory, and a tile the deployment has no adapter for is shown unavailable in
different words from one the product has not built. A source's own settings are a
field descriptor — text, list, number, checkbox, select, split into ordinary and
advanced — rendered by one renderer and read back on the connection detail page
from the same descriptor, so a setting cannot appear on the form and be missing
from the summary. Adding a source is an adapter package with a
`ConnectorSourceProfile` bean, a catalogue entry, and a descriptor: no migration,
no new endpoint, no change to `core`.

A second adapter, Google Drive, exercises that shape rather than asserting it.
`core/src/main` names no source, `apps/api/src/main` imports nothing from the
connector module, no migration made room for it, and no endpoint was added: the
adapter contributes a profile, a batch source and a credential probe, and
`GET /api/admin/connectors/sources` reports two because two exist. It differs from
Slack on every axis the design abstracts over — a signed JWT exchange rather than
a bearer token, a file tree rather than a message stream, per-object ACLs rather
than channel membership, and content that has to be converted before it is text.

The Drive adapter reads files a service account can see, keyed on Drive's file id
with a hash of the extracted text as the content revision — `modifiedTime` moves
when sharing changes, and re-materializing a document because its permissions
moved would pay for chunking and embedding to arrive at identical text. One
listing carries every file's own sharing, so a permissions-only pass costs no
document read at all. Google's own formats are exported to text and textual files
are downloaded; everything else is skipped, because extracting text from a PDF is
a parser concern the ingestion pipeline already owns for uploads. A skipped file
was never in the adapter's universe and does not withdraw the completeness claim;
a folder filter, an unreadable file and a hit bound each do.

Drive omits inline `permissions` for an item in a shared drive and returns
`permissionIds` instead, so the adapter follows those ids through
`permissions.list`. A file whose sharing still cannot be read is left out of the
payload rather than sent with no grants: an empty grant list is the assertion
that the source says nobody may read the object, and the ledger would seal it as
one. Leaving the object out keeps whatever generation was last sealed for it and
withdraws the completeness claim. Three other things withdraw it — Google
reporting `incompleteSearch` for a combined-corpora listing, a folder scope
larger than the adapter walks, and a file past the configured size bound, which
is the adapter's own policy rather than a fact about the file.

A folder scope means the subtree: Drive reads `'X' in parents` as the immediate
parent only, so the configured folders are expanded breadth-first, visiting each
folder once, before any file is listed.

The crawl cursor is the batch's fingerprint, covering the sorted grants,
identities and their membership, content revisions and titles, and the
completeness flag. It has to name the grants and not count them — replacing one
reader with another leaves the count unchanged, and a cursor that only counted
would let the driver skip the batch as already ingested, leaving the removed
reader with access and the added one without.

Its permission mapping is defined by what it refuses. A `user` or `group`
permission grants to that address; a `domain` permission grants to a group keyed
on the domain whose membership is the users this crawl observed there, because the
Drive API cannot enumerate a domain — so it under-grants rather than inventing
members, and resolves as more of the Drive is crawled. An `anyone` permission
grants nothing: a public link is a statement about people outside the
organization, and translating it into an internal grant would widen access on the
strength of a setting that says nothing about who inside may read.

Drive rate limits and transient server errors are retried rather than surfaced: a
429, or a 403 whose reason names a rate limit, waits out `Retry-After`, and a 5xx
or dropped connection backs off. The attempts are bounded, so a quota that stays
exhausted becomes the connection's recorded failure instead of the worker's
stall.

The current path does not yet implement incremental webhooks or the Events API,
credential rotation, a run of either adapter against a real workspace, Airbyte
staging, OCR, malware and DLP integrations, entity and relationship extraction,
graph publication, or hybrid retrieval extensions beyond the current secure FTS +
pgvector path.

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

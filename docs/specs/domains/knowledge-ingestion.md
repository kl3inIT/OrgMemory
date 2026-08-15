# Knowledge Ingestion Spec

Source: `core/src/main/java/com/orgmemory/core/knowledge`,
`apps/api/src/main/java/com/orgmemory/api/knowledge`,
`apps/api/src/main/java/com/orgmemory/api/source`,
`apps/api/src/main/java/com/orgmemory/api/admin`,
`apps/web/src/features/sources`,
`apps/web/src/features/admin`,
`apps/worker/src/main/java/com/orgmemory/worker/ingestion`,
`apps/worker/src/main/java/com/orgmemory/worker/connector`,
`components/graph-rag-core/src/main/java/com/orgmemory/graphrag/parsing`,
`components/graph-rag-core/src/main/java/com/orgmemory/graphrag/chunking`,
`integrations/document-parsing-spring-ai/src/main`,
`integrations/connectors/src/main`, and `contracts/connector`.

Reconciled: `admin-safe-deletion-controls (fcde1113)`.

## Current Behavior

An authenticated user enters the visible Knowledge workspace and uses its
Documents or Knowledge graph surface without changing the established
`/sources` route. They can upload PDF, Word (`docx`/`doc`), PowerPoint
(`pptx`/`ppt`), Excel (`xlsx`/`xls`), CSV, HTML/HTM, RTF, OpenDocument
(`odt`/`ods`/`odp`), TXT, or Markdown through the Documents API and web view.
The same closed policy applies per extension in the API and browser: CSV,
HTML/HTM, RTF, TXT, and Markdown are bounded to 10 MB; spreadsheets to 15 MB;
and the remaining admitted formats to 25 MB. The servlet request limit is only
the 25 MB transport ceiling, not the product policy. The user must select a
Knowledge Space returned by
OpenFGA `ListObjects(can_create_asset)`, and the mutation rechecks
`can_create_asset` before writing evidence. MinIO stores immutable evidence bytes
behind the provider-neutral object-storage contract. PostgreSQL persists canonical
`SourceObject`, `SourceRevision`, `EvidenceBlob`, and leased durable ingestion
jobs with the target Knowledge Space identity.

Source Ledger also owns the citation evidence read boundary. It maps a
tenant-scoped ready revision and validated evidence blob into immutable citation
metadata, while revision/blob entities, repositories, and lifecycle enums remain
internal to the closed module.

Parent Knowledge exposes a general `knowledge::evidence` named interface for
product channels that need to register governed bytes or resolve an exact
Source/revision without importing Source Ledger internals. Source Ledger adapts
that interface to its existing upload validation, object storage, revision, and
job registration services and maps lifecycle state into a channel-neutral
reference. Assistant is its first consumer. It still does not call the parser or
processing engine: the worker remains the sole production parser caller, and
all heavy parsing, chunking, embedding, and projection publication stays on the
durable ingestion path.

The Documents list uses Source Object ids as its stable browser identity and
reads row state from each object's latest revision, including staged revisions
that have not been published. The permission-visible and owner-visible Source
Object id sets are each bounded and their union must remain within the secure
retrieval authorization ceiling; an indeterminate or oversized visibility set
fails unavailable rather than becoming an empty or organization-wide result.
The list applies optional Knowledge Space, classification, status, and text
filters only as additional restrictions on that authorized set. It walks latest
revision update time descending with Source Object id as an ascending keyset
tie-breaker and returns an opaque cursor envelope:
`{ items, nextCursor, pageSize, total, statusCounts }`. Page size is clamped to
1–60. `total` and the processing, ready, and attention counts describe the
whole authorized-and-filtered result rather than only the loaded page. A
selected status restricts the returned rows but is deliberately excluded from
all four counts, so `total` remains the unfiltered-by-status size and always
equals the three bucket counts summed; a caller reporting one status shows that
bucket rather than `total`. This keeps every status badge answerable from a
single page response.

A visible row identifies its Knowledge Space, owning department when present,
and uploader when the source resolves to an application user. It exposes
separate `publicationComplete`, `contentAvailable`, and `deletionAllowed` hints;
each action rechecks authorization server-side. Publication is complete only
for a READY revision linked to a Knowledge Asset, while original bytes
additionally require that active Asset to remain inside the caller's canonical
evidence scope. The governed viewer therefore keeps the publication-pending
explanation for unfinished work and gives an already-visible published row
neutral out-of-scope guidance, including the owning department as the
access-request target when available. Delivery verifies evidence hash and
length, audits allow/deny, uses `no-store` and `nosniff`, and applies a closed
filename allowlist: PDF,
plain text/Markdown/CSV-as-text, PNG, JPEG, GIF, and WebP may render inline.
HTML and RTF use a safe plain-text response type but remain download-only;
Office, OpenDocument, SVG, XML, JSON, and unknown types are also download-only.
On desktop the
right-side reader is a persistent master-detail surface: the document list
remains visible and interactive while the selected evidence is open. Smaller
viewports use an accessible modal Sheet. Both presentations use the safe
response type as their ceiling and may refine delivered `text/plain`
into Markdown only when the canonical source metadata declares `text/markdown`.
Markdown uses the shared restricted renderer with Rendered and Raw views: active
HTML, remote images, and unsafe URLs do not execute. Preview errors expose an
explicit retry, while PDF, raster image, text, and download-only presentations
fill the remaining responsive reader height.

Classification is displayed and selected as classification, not as an inferred
effective audience. The upload dialog and list explain that effective access
also follows the selected Knowledge Space and current organization policy,
because classification alone cannot prove who the current OpenFGA model admits.
File rows use concise format labels and keep embedding/index profile details
out of the employee-facing table.

FAILED and QUARANTINED revisions expose their already-bounded failure detail
inline rather than only through a hover tooltip. QUARANTINED bytes are never
retried unchanged; the user can open a fresh upload for corrected evidence.
There is no manual FAILED retry: Source Ingestion does not yet carry an exact
claim epoch through Asset publication, so a reset could race a stale producer.
The required fencing and recovery proof remain backlog work.

Delete is an idempotent governed retirement command, not physical erasure. It
is available only for fully published READY native uploads and rechecks
`knowledge_asset can_delete`; connector and pre-publication states stay
non-deletable. The command retires the active asset version, archives its stable
Knowledge Asset and Source Object, and invalidates model/retrieval caches.
Canonical retrieval and graph evidence scopes require the non-archived asset,
ACTIVE version, and ACTIVE source, so retained rows and tuples stop serving
immediately while physical evidence continues under organization retention
policy. There is no document Reindex action.

The ingestion and graph-indexing schedulers process a bounded burst of queued
jobs per fixed-delay tick — up to a configured per-queue job cap within a
wall-clock budget, stopping early on an empty queue, interrupt, or shutdown —
so one queue's backlog cannot monopolize the shared scheduling thread.
Postgres projection staging writes execute as bounded JDBC batches
(configurable batch size) with unchanged SQL, transactions, and whole-stage
failure behavior; graph replacement batches per dependency phase. The worker
validates content integrity and parses supported documents through the reusable
`integrations:document-parsing-spring-ai` adapter into canonical heading,
paragraph, and table blocks. The framework-neutral parser port publishes its
supported suffixes; Knowledge admission remains a separate product policy, and
the worker routes only formats present in both. CSV has a dedicated BOM-aware,
delimiter-sniffing, quote-aware table reader. HTML/HTM is sanitized before Tika
so navigation, script, and style content cannot become evidence. Declared
archives are refused, while OOXML and OpenDocument containers are bounded and
validated before parsing. A deterministic suffix/media mismatch is quarantined
without retry. A genuine Microsoft Word Heading 1 is preserved as a canonical
heading block. Its default
named policy is `structured-block-v1`: the paragraph-semantic composite
preserves headings, dispatches tables row-wise with repeated headers, and uses
recursive-character splitting internally for unstructured or oversized
content. Fixed-token, recursive-character, and semantic-vector behavior remain
explicit named operator policies rather than raw chunker ids. Semantic-vector
alone may resolve to its declared recursive-character fallback when semantic
embedding is unavailable.

The worker atomically pins the complete requested parser, policy, component,
tokenizer, embedding, normalization, and limit snapshot on the ingestion job at
first claim. The limit snapshot includes the entire per-format maximum-chunk
map: 300 for CSV, spreadsheets, and presentations, 400 for HTML/HTM, and 500
for the remaining admitted formats. Retries use that snapshot instead of current
configuration. After
parse/chunk succeeds, the job pins the resolved requested-versus-actual profile
and chunk-manifest hash before any raw-source, normalized-record, Asset, vector,
authorization, or READY publication side effect; a retry must reproduce it or
fails permanently. New or updated READY revisions require the canonical
processing profile and its SHA-256 at the database boundary. The constraint is
introduced without validating historical rows, so an existing READY revision
that predates the profile remains unchanged and cannot be silently reparsed or
rechunked when defaults change; a rebuild requires a new explicit revision
identity. The V31 cutover locks the ingestion-job table and refuses to migrate
while a legacy job is `PENDING` or `PROCESSING`; deployment must stop and drain
the old worker before migration because the new deployment cannot safely guess
the old job's complete policy. Publication first
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
`source_principals` registry by `(organization, source, connection, kind,
native_principal_id)` (observation grants nothing) and resolved to active
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
the querying user; a `SOURCE_GROUP` entry grants only through the group's active
sealed `COMPLETE` membership head joined to an active mapping. Resource ACL
snapshots contain principal grants only and never copy expanded membership.
`INCOMPLETE`, unsealed, missing, unmapped, revoked, or inactive evidence grants
nothing. Per [ADR 0009](../../decisions/0009-dynamic-source-acl-ceiling.md),
sources whose access rule the source itself owns enforce the current sealed ACL
generation as the ceiling, while sources OrgMemory holds the rule for keep the
ingestion-current intersection.

Which of the two an object follows is `source_objects.acl_authority`
(`SOURCE`/`ORGMEMORY`), and which system it came from is `source_objects.source_system`
(`slack`, `google_drive`, `github`, `upload`). One column used to answer both, so every new connector needed
DDL to widen a check constraint guarding a distinction the source's name has
nothing to do with. The authority is recorded at ingestion and never updated: it
is what was true when the evidence entered, not a policy an administrator can
change afterwards. The system is governed by the connector registry rather than a
constraint — a `ConnectorSourceProfile` bean contributed by an adapter declares
the name, display name, classification, declared access, object type and media
type, `ConnectorSourceRegistry` refuses a name no adapter claimed and refuses two
adapters claiming one name, and nothing in `core` names a source.

Source connectors ingest a versioned crawl contract
(`contracts/connector/`: four separately-versioned payload kinds — content,
identity, membership, and permissions — plus tombstones, an opaque batch cursor,
independent content/permission/membership cursors and capture status, and a
completeness claim) through a
dedicated `ConnectorIngestionService`. It observes and matches external
principals, reconciles group membership once per crawl, then seals resource ACL
generations carrying only `SOURCE_GROUP`/`SOURCE_USER` grants through
package-private connector-aware methods on `KnowledgeIngestionService` that the
public upload path does not expose (the upload path still rejects external
principals). A new object
materializes content by reusing the `normalize` and `publish` use cases, with
chunks embedded through a `ConnectorTextEmbedder` port; a membership re-crawl
appends and atomically activates a membership snapshot without rotating any
resource ACL or re-materializing content,
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

The three live adapters delegate their connection lifecycle to
`PollingConnectorBatchSource`. Its final poll loop enumerates enabled connections,
keys per-connection state by the typed organization/source/connection identity,
resolves the credential on every poll, decides whether content is due, isolates
known provider failures, and retires state for missing or disabled connections.
It caches only the derived provider client under credential and client-setting
revisions; those clients necessarily retain credential-derived material for their
cache lifetime. Rotation replaces the client on the next poll, while retirement
clears both the client and the cadence's due and served-crawl-now state. Cadence
advances only after an admitted content batch. Unexpected runtime failures still
escape instead of being disguised as connection activity.

Provider calls, eligible-unit counting, mappings, completeness evidence, and
component cursor material remain adapter-owned. The shared driver rejects a pass
when at least half of its eligible units failed provider requests, returns no
batch, and reports `mostly_failed`; the worker records that as recurring
`UNAVAILABLE` activity, so no checkpoint advances. Slack counts attempted
channels, Drive counts attempted indexable files, and GitHub counts a repository
at most once when its collaborator or content request throws. Configured
truncation, content skipped after the same repository's collaborator failure, and
incomplete source fields do not inflate GitHub's numerator. Whole-connection
rejection deliberately means healthy-unit membership revocations also wait when
the threshold is met; the recurring failure activity makes that fail-closed
operational tradeoff visible.

Outer crawl cursors preserve their historical bytes: component cursor pairs use
the existing natural ordering and digest material, while every adapter supplies
its literal prefix. In particular, Drive retains `google-drive-` even though its
source-system id is `google_drive`.

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
mistake. Driver progress is checkpointed per connection and component in one
`connector_crawl_checkpoints` table, so membership change does not replay content
or permissions. A `COMPLETE` component advances observed and last-successful
state; `INCOMPLETE` source evidence advances only observed state and reason. A
batch rejected for a reason retrying cannot change is observed past, while
technical or per-item failure leaves the affected component pending.

What each pass did is a row in `connector_crawl_attempts` rather than a log line,
with an outcome, per-object counts, and an error code and message. The four
outcomes are kept apart because they call for different actions: `SUCCEEDED`
handled every component, `PARTIAL` advanced some components while failed items
remain pending, `REJECTED` was observed past and is not coming back, `FAILED` is
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

Deleting a connection removes its crawl configuration and encrypted credential,
so the polling directory stops offering it on the next pass. It does not erase
already governed source objects, revisions, evidence, or projections: those
remain under their Knowledge Space and source lifecycle so connection removal
cannot bypass retention. An administrator can retire eligible native uploads
through the separate document command when that content should leave active
retrieval.

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
shared polling driver reads connections and credentials on every poll through
`ConnectorConnectionDirectory`, so enabling a workspace, repointing it, or
replacing its token takes effect on the next poll even when the derived client is
reused between unchanged polls. The adapter bean is present wherever the module
is and produces nothing until a connection says otherwise, and a connection that
cannot produce is skipped — but reported, not swallowed — rather than allowed to
end the poll for the others.

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
`GET /api/admin/connectors/sources` reports the installed adapters. Drive differs from
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
typed native identities, membership status and members, content revisions and titles, and the
completeness flag. It has to name the grants and not count them — replacing one
reader with another leaves the count unchanged, and a cursor that only counted
would let the driver skip the batch as already ingested, leaving the removed
reader with access and the added one without.

Its permission mapping is defined by what it refuses. A `user`, `group`, or
`domain` permission grants to Drive's stable permission/grantee ID; email and
domain text remain aliases. Drive cannot enumerate Google Group or domain
membership, so those groups emit `INCOMPLETE` membership and grant nobody until
an authoritative Directory integration captures a complete member set. An `anyone` permission
grants nothing: a public link is a statement about people outside the
organization, and translating it into an internal grant would widen access on the
strength of a setting that says nothing about who inside may read.

A third adapter, GitHub, proves the same kernel can mirror an effective
entitlement set rather than a provider's explicit group graph. For every private
organization repository with Issues enabled, the adapter creates one stable
`repository:{numericRepositoryId}:readers` source group and fills its membership
from GitHub's fully paginated effective-collaborator endpoint. That endpoint
already resolves direct grants, teams, organization defaults, owners, and
enterprise/custom roles; reconstructing those paths in OrgMemory would be a
second, incomplete GitHub authorization engine. Issues and pull requests grant
to the repository reader group. User and repository keys are immutable numeric
GitHub IDs, and GitHub email is absent, so an AppUser binding must be explicitly
confirmed or established by another trusted identity signal.

GitHub authenticates with a redacted GitHub App credential. A short RS256 app
JWT mints a cached installation token; the client follows `Link` pagination,
bounds response bodies, retries bounded rate-limit/transport/server failures,
and sends the pinned REST API version. The connector admits only organization
installations and private repositories with Issues enabled. A collaborator read
failure marks permission and membership incomplete and materializes no content,
never seals an empty ACL. Between content crawls it refreshes collaborators and
restates the stable repository grants for existing objects without reading issue
bodies. Removing a team-derived collaborator therefore advances only the
membership head and revokes retrieval without rotating the issue ACL, revising
content, rechunking, or re-embedding.

Drive rate limits and transient server errors are retried rather than surfaced: a
429, or a 403 whose reason names a rate limit, waits out `Retry-After`, and a 5xx
or dropped connection backs off. The attempts are bounded, so a quota that stays
exhausted becomes the connection's recorded failure instead of the worker's
stall.

The current path does not yet implement incremental webhooks or the Events API,
a run of either adapter against a real workspace, Airbyte
staging, OCR, or malware and DLP integrations. Entity/relation extraction,
profile-versioned graph publication and secure hybrid GraphRAG retrieval are
implemented as rebuildable projections over this canonical ledger.

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
- [0037](../../decisions/0037-separate-parser-capability-from-knowledge-admission.md)
- [0038](../../decisions/0038-use-governed-source-bindings-for-assistant-files.md)

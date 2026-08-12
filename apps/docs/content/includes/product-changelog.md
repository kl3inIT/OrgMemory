[//]: # (Generated from release/CHANGELOG.md by Tegami. Do not edit manually.)

## Organizational AI Memory v0.4.0

### Work with private files in Assistant

#### Features

Upload or reuse up to three private files directly from the Assistant
paperclip without publishing them to a Knowledge Space. Private files stay
visible only to their uploader, use the same structured document processing as
Knowledge, and expire automatically after 30 days. Publishing durable governed
Knowledge remains a separate explicit action.

#### Security

Private-file turns recheck the uploader, organization, lifecycle, exact
processing generation, and embedding profile before retrieval or download.
Delete and expiry deny access before extracted content and stored objects are
cleaned up; old answers retain only a non-clickable unavailable citation marker.

## Organizational AI Memory v0.3.0

### Keep Assistant evidence useful after reload

#### Features

Assistant citations now survive transcript reload while still rechecking each
user's current access. Source opening starts with the exact governed evidence
excerpt, supports safe Markdown, PDF, image, text, and download-only files, and
shows truthful retrieval and answer-preparation activity before the first token.

### Improve the Assistant conversation experience

#### Features

The Assistant now restores in-session conversation drafts, offers
server-curated starting prompts, retries completed answers with fresh governed
retrieval, and lets users save helpful or not-helpful feedback on an answer.

### Choose a governed model in Assistant conversations

#### Features

Choose an administrator-approved model directly from the Assistant composer.
The selected model stays with the conversation, remains bound to the exact
approved gateway route, and safely falls back to the deployment default when
no explicit choice is made.

### Use governed Skills in Assistant answers

#### Features

The Assistant can now discover an authorized Skill, load its exact released
instructions, and read bounded supporting text while preparing a grounded
answer. Skill content never grants tools or permissions, and OrgMemory does not
execute package scripts, binaries, or shell commands.

### Restore organization-wide document visibility

#### Fixes

All-employees demo documents now target the organization-wide Company
Knowledge space instead of inheriting a department-only audience. Department
and executive documents retain their existing restricted placement.

### Make Assistant no-answer guidance permission-safe

#### Improvements

The Assistant now responds in the user's language when accessible documents do
not answer a question, offers one concise next step, labels nearby information
instead of presenting it as the requested answer, and cites every source it
uses without citing unrelated documents. Assistant answers also show a short
reminder that they rely only on documents the user can access.

### Keep parallel knowledge graph relations visible

#### Fixes

The Knowledge graph no longer fails to load when two distinct semantic
relations connect the same directed pair of entities. Parallel relations remain
separate and visible in the graph and entity inspector.

### Keep Knowledge Graph controls from crushing the page title

#### Improvements

The Knowledge Graph workspace now keeps its page title readable while the
explorer controls wrap into the remaining desktop width. The same shared header
behavior prevents dense action groups from collapsing page identity elsewhere
in the product.

### Refine Knowledge document operations and graph inspection

#### Improvements

Documents now use a persistent desktop reader while keeping the list
interactive, show ingestion failures without relying on hover, and guide
quarantined evidence into a corrected upload. The graph inspector now presents
readable entity context, directional connections, and permission-verified
document evidence instead of generic numbered sources.

### Complete the governed document reader

#### Improvements

The employee workspace is now named Knowledge, with Documents and Knowledge
graph as its two clear surfaces. Documents open in a responsive right-side
reader with safe rendered or raw Markdown, inline PDF and image previews,
plain-text reading, explicit download-only fallbacks, and retry when governed
content cannot be loaded.

### Faster, fairer assistant retrieval under load

#### Improvements

Assistant knowledge retrieval now admits snapshot queries through one fair
process-wide limit instead of per-request batches, so concurrent
conversations can no longer exhaust the database connection pool and stall at
the turn timeout. The API connection pool is right-sized for the production
host, retrieval breadth returns to the upstream LightRAG default, and new
payload-free timing stages make the previously unattributed portion of
time-to-first-token observable.

### Verify retrieval recall before query cutover

#### Improvements

Operators can now capture and score authorization-preserving retrieval recall
against an explicitly restored projection copy without generating answers or
touching the live database. The recorded 43-case comparison confirms that the
raw-query bypass stays level with the current keyword-seeded path and preserves
the evidence needed to diagnose shared misses before any query-plane cutover.

### Unify governed document previews

#### Improvements

Knowledge documents and Assistant citations now open in one centered,
responsive viewer. Long PDF, image, Markdown, and text evidence gets the full
reading surface, inline citations open it directly, and the source sidebar
remains available for comparing cited and discovered evidence.

### Rebuild AI gateway consumers for production

#### Fixes

Production releases now rebuild the API and worker whenever their shared AI
gateway integration changes, so approved Assistant and model-routing fixes are
included in the immutable image set instead of being treated as deployment
no-ops.

### Keep Assistant tool calls compatible with OpenAI

#### Fixes

Fresh production deployments now set Answer reasoning to `none` so the
Assistant's governed Skill tools work with `gpt-5.6-sol` on OpenAI Chat
Completions without requiring an organization route workaround.

### Tell people what to do when an Assistant turn fails

#### Fixes

A failed Assistant turn now ends on a sentence naming what the person who hit it
can do next, instead of one generic message for every cause. An expired gateway
key, a rate limit, a model that is no longer offered, a gateway that did not
answer in time, and a busy assistant are now distinguishable and separately
actionable.

Every message remains a fixed sentence chosen from the failure's category, so a
misconfigured or unusually talkative AI gateway cannot surface its own text,
credentials, or prompt content in the browser. A failure that matches no known
category still ends on the previous generic message.

### Keep an Assistant conversation in one place

#### Improvements

An Assistant conversation is now stored once. The transcript that already served
history, replay, rename, and delete is also what the model reads back as prior
context, replacing a second copy that was kept in a table with no organization,
no owner, and no link to the conversation it belonged to.

Prior context is now read in whole question-and-answer turns rather than by
counting messages. The question of the turn currently being answered can no
longer be sent to the model twice, a turn that failed before answering no longer
occupies the window, and the window can no longer begin partway through an
exchange. Deleting a conversation removes its context in the same operation
instead of relying on a separate call.

### Make Assistant prompt controls consistent and accessible

#### Improvements

The Assistant composer now uses a consistent Prompt Input control set for its
model picker, status-aware submit behavior, keyboard composition, tooltips, and
future action menus. Text submission, stop controls, and input-method editing
remain predictable without enabling file, screenshot, voice, or source
attachment capabilities.

### Show Assistant Skill activity without a blank wait

#### Improvements

The Assistant now keeps its progress state visible until answer text appears
and shows a compact, current-turn receipt when it successfully activates a
governed Skill. Skill titles are bounded plain text, denied or failed Skills
remain unnamed, and the receipt clears safely when a turn ends without an
answer.

### Match Assistant requests against authorized Skills

#### Fixes

The Assistant now sees the current user's authorized Skill names and
descriptions before choosing a workflow, so natural-language requests can
activate the matching exact release without inventing catalog search terms.
Unavailable Skills remain hidden, and activation still rechecks access.

### Keep Assistant activity visible until the answer appears

#### Fixes

Assistant thinking and Skill activity now stay in one stable transcript
position until meaningful answer text appears. Skill receipts no longer expose
an empty disclosure when there is no resource detail to show.

### Stop losing an Assistant answer that was already on screen

#### Fixes

An Assistant answer could disappear from a conversation after being delivered.
When two turns of the same conversation finished at the same moment, only one of
them was saved; the other was rolled back and was gone on the next reload, even
though the person asking had watched it arrive. Both are now kept.

Long answers with many sources also render far more cheaply. Each arriving
source used to discard and rebuild the entire answer shown so far, which made a
long, heavily cited reply progressively slower to display and could leave the
page unresponsive while it finished. The answer is now updated in place as its
sources arrive.

### Separate data clearance from user roles

#### Improvements

The user "role" field is now a data clearance with two values, Standard and
Executive, matching what the system actually enforces: Executive widens
confidential and restricted document access, while action permissions stay
governed by organization roles. Administrators can now assign a user's
department (required for confidential document access), raising someone to
Executive asks for confirmation and states its reach, and every user can see
their own department and clearance in the account menu. Legacy titles such as
Team lead, Manager, Director, and the misleading Admin label are removed;
existing Executive users keep Executive and everyone else becomes Standard.

### Reuse exact query embeddings across retrieval requests

#### Improvements

GraphRAG and hybrid knowledge retrieval now reuse exact query embeddings within
an explicit projection namespace. Repeated requests avoid duplicate embedding
provider work while authorization, evidence selection, and citation verification
continue to run normally. Cached vectors remain isolated by embedding profile,
provider version, and dimensions, with bounded PostgreSQL retention and expiry.

### Keep synthetic redaction fixtures in secret scanning

#### Fixes

Secret scanning now narrowly recognizes the API-key-shaped value in the
assistant redaction regression as synthetic test data while continuing to scan
all other files and generic API-key findings.

### Keep GraphRAG degree ranking within its retrieval budget

#### Fixes

GraphRAG degree ranking now resolves authorized relation visibility once and
uses indexed source and target endpoint lookups. PostgreSQL also cancels an
abnormally slow degree query before the assistant retrieval deadline, avoiding
orphaned database work that could degrade later chat turns.

### Bound authorized GraphRAG relation loading

#### Fixes

GraphRAG now resolves authorized relation candidates with set-based entity and
relation visibility checks instead of repeating correlated ACL work for each
candidate. Relation reads also use the transaction-scoped PostgreSQL timeout so
a retrieval cancellation cannot leave database work running in the background.

### Restore fast authorized GraphRAG relation scoring

#### Fixes

GraphRAG now limits relation contribution and relation-weight authorization work
to the requested candidate relations before checking independently visible source
and target entities. These reads also use the transaction-scoped PostgreSQL
budget, preventing an expensive plan from continuing after retrieval is
cancelled.

#### Improvements

Snapshot retrieval now reports bounded, payload-free timings for each graph
storage operation under the existing retrieval operation identifier, making
future latency regressions attributable without recording prompts, answers, or
evidence identifiers.

### Filter and page the Documents list

#### Improvements

Documents can now be narrowed by Knowledge Space and classification alongside
the existing status tabs, so the Space shown on every row is finally something
you can filter by. Filtering and search run on the server and the list is paged,
so a large library no longer arrives in one response. Status tab counts describe
the whole filtered library rather than the page you happen to be on, and a
document still processing keeps its real status instead of disappearing while it
publishes. The knowledge graph search now runs as you type, matching the
Documents tab instead of waiting for a separate button.

### Keep the knowledge graph stable while its panel is sizing

#### Fixes

The knowledge graph now waits for a visible, positively sized canvas before
starting Sigma, and releases the renderer if the panel becomes size-less during
a layout transition. Opening the graph while its tab or flex layout is still
settling no longer crashes the page with a zero-height container error.

### Put production services on one shared Docker DNS fabric

#### Fixes

OrgMemory, documentation, and observability services now join the same external
Docker DNS network while retaining their existing private and proxy networks.
Cross-stack diagnostics and integrations can use stable service names instead of
container IP addresses without publishing additional host ports.

### Keep the document list working for organization-wide sources

#### Fixes

The Documents list no longer fails when a source belongs to an
organization-wide Knowledge Space. Those sources carry no owning department,
and the provenance lookup rejected the missing identifier.

### Clarify document provenance and content availability

#### Fixes

The Documents ledger now identifies each document's Knowledge Space, owning
department, and uploader. Published documents outside the current user's
content scope now show honest access guidance instead of being described as
still waiting for publication.

### Enforce the supported Assistant question length

#### Fixes

The Assistant composer now displays and enforces the 1,000-character question
limit before a turn starts. Questions at the boundary remain accepted, while
longer input is blocked instead of opening a stream that later fails.

### Brand the OrgMemory sign-in experience

#### Features

- Give OrgMemory sign-in and password recovery a responsive, accessible visual
  theme aligned with the product's light and dark design tokens.

#### Operations

- Package the Keycloak login theme in the immutable Keycloak image and reconcile
  existing realms with rollback-safe restoration of their prior theme.
- Deploy production images by their verified manifest digests. Keycloak rollback
  now fails closed if the previous realm theme cannot be restored and verified,
  rather than starting a potentially incompatible previous image.

### Make the Asset catalog easier to scan

#### Improvements

Asset cards now use distinct, consistent marks and prioritize the capability's
name, description, human owner, and sharing state. Technical coordinates and
shortened owner identifiers no longer compete with discovery, while Skill
activation remains available as a compact personal setting.

### Share and use Assets from one company library

#### Features

The Asset catalog now separates reusable releases available to you from Assets
you created, and shows each Asset's owner and sharing state. Owners can publish
immutable updates directly, share Viewer or Editor access, transfer ownership,
and withdraw an Asset without routing new contributions through review.
Historical review evidence remains available in the Asset workspace.

Skills now require a personal **Use in Assistant** opt-in in addition to live
access to a released version, so each person controls which shared Skills may
guide their Assistant sessions.

### Use governed files as Assistant evidence

#### Features

- Upload up to three supported documents from the Assistant composer, publish
  them to a chosen Knowledge Space, and wait for governed ingestion before use.
- Keep the exact ordered file selection across a failed retry and cite the same
  permission-verified evidence used for the answer.

#### Security

- Recheck conversation ownership, current Source revision, actor access, and
  active retrieval-engine readiness before each selected-file turn.
- Keep selected files as a hard retrieval ceiling through graph expansion and
  citation output; direct provider files and transient attachment bypasses
  remain unavailable.

### Deploy releases from a clean checkout

#### Fixes

Production deployment now executes the exact released commit from an
ephemeral clean linked worktree. Staged or local operator changes on the host
can no longer replace deployment scripts or image references during rollout.

### Ingest the document formats organizations already use

#### Features

Knowledge document upload now accepts CSV, Excel, legacy Word and PowerPoint,
HTML exports, RTF, and OpenDocument files alongside the existing PDF, modern
Office, Markdown, and text formats. Upload and processing limits adapt to each
format instead of applying one global ceiling.

Document processing now preserves headings, paragraphs, tables, spreadsheet
headers, and PDF page provenance through a reusable parser boundary. The
versioned structured policy keeps retries deterministic while table fragments
retain the header context needed for useful answers.

### Preserve rollback evidence after bootstrap cleanup

#### Operations

Production deployment now tolerates cleanup of the completed PostgreSQL
bootstrap container when the previous release is already pinned by exact
digest and that same image remains available locally for no-pull rollback.
Mutable, missing, or mismatched rollback images remain blocked before rollout.

## Organizational AI Memory v0.2.0

### Route ACL reads through an owned query boundary

#### Improvements

Retrieval and Graph now read source ACL facts through `SourceAclQuery` and
immutable ACL-owned references instead of consuming the ACL repository, JPA
entity, or a Space-owned projection type. This follows the independently
reviewed architecture direction to replace direct implementation edges before
closing nested modules.

### Remove the ACL-to-Source Ledger dependency

#### Improvements

ACL heads now consume an ACL-owned source target and preserve stable conflict
semantics without importing Source Ledger entities or exceptions.

### AI management permission visible in admin, leaner API surface

#### Improvements

The organization permission catalog now includes the AI management permission,
so admin screens can show and explain who may manage AI gateways. The
redundant identity endpoint was removed in favor of the session endpoint, and
request handling avoids repeated per-request work in SCIM limits and worker
scheduling configuration.

### Repair published graph snapshots after Apache AGE cutover

#### Fixes

Production deployment now reconstructs retained relational graph publications
in Apache AGE through a bounded, verified one-shot before the API and worker
start. Graph exploration and Assistant retrieval no longer remain unavailable
when published snapshots predate the AGE topology backend.

### Repair Apache AGE startup for least-privilege roles

#### Fixes

Apache AGE startup now verifies session preload through a bootstrap-owned
boolean probe instead of requiring the application to read all PostgreSQL
settings. Production-shaped conformance tests use a non-superuser role and keep
the broader `pg_read_all_settings` privilege denied.

### Refine Asset catalog layout balance

#### Improvements

Tighten the desktop Asset catalog with a compact ownership scope, trailing
result and layout controls, and clearer selected states while preserving the
existing responsive workflow.

### Move catalog and chunk values to Knowledge Asset

#### Improvements

Knowledge Asset now owns its catalog projection, normalized text-chunk value,
and PostgreSQL vector encoding, so Retrieval and other consumers depend on the
domain that persists and publishes those values.

### Add personal Asset ownership navigation

#### Improvements

Add an owner-scoped `My Assets` workspace alongside the authorized shared
catalog, with responsive search, scope, type, sort, and layout controls.

### Remove the Knowledge Asset dependency on Retrieval

#### Improvements

Knowledge Asset now owns its compact embedding-profile reference and projection
namespace identity. Connector and Worker translate richer Retrieval profiles at
the orchestration boundary, leaving Asset with no direct Retrieval dependency.

### Isolate Asset publication from Source Ledger persistence

#### Improvements

Knowledge Asset promotion now receives validated source facts through public
Source Ledger contracts, while publication advances the source revision through
an owner-defined transaction-aware service instead of cross-module repository
access.

### Unify authorized graph traversal

#### Fixes

Authorized graph expansion now follows one deterministic core policy across
PostgreSQL, Neo4j, and OpenSearch. Exact snapshot validation, permission-scoped
paging, canonical ordering, and global limits no longer vary by storage
backend, and incomplete native traversal prefixes can no longer become public
query results.

### Automate release note navigation

#### Documentation

Generate localized release-note navigation and an internal archive from the
reviewed product changelog so public history remains current without manual
sidebar edits or repository access.

### Author and revise Skill Drafts in the browser

#### Improvements

Create a private Skill Draft from scratch, a `SKILL.md`, ZIP, or local folder.
OrgMemory previews the validated package before creation, and authorized owners
can replace the mutable Draft package without changing any immutable Revision
or Release.

### Create Skill Drafts from the Assets catalog

#### Improvements

Open the new Add asset menu from the Assets catalog, choose Skill, and upload a
validated ZIP package into an authorized Knowledge Space. A successful import
creates a private Draft and opens its existing Governance workspace.

### Separate Changelog from documentation categories

#### Documentation

Keep Changelog as a standalone global documentation surface with focused,
automatically generated release navigation while limiting the documentation
category switcher to the four reader-oriented categories.

### Repair existing-version CLI publication verification

#### Fixes

Make the retry-safe npm publication path parse correctly when the exact CLI
version already exists, and prevent indented nested heredoc terminators from
reaching the workflow again.

### Correct the missing-version npm publication probe

#### Fixes

Allow a new exact CLI version to reach npm Trusted Publishing while retaining
the immutable-integrity comparison for versions that already exist.

### Make CLI publication verification retry-safe

#### Fixes

Recover a successful immutable CLI publication when npm provenance propagates
after the package manifest, while refusing any existing version whose registry
integrity differs from the reviewed tarball.

### Close the Knowledge ACL module boundary

#### Improvements

Knowledge ACL now enforces a closed public API with an explicit dependency
allowlist limited to `organization`, `permission`, `shared`, and
`shared::error`. This completes the independently reviewed ACL closure after
its sibling implementation edges were replaced with owned APIs.

### Close the Knowledge Connector module boundary

#### Improvements

Knowledge Connector now enforces a closed Spring Modulith boundary and an exact
outgoing dependency allowlist after its ACL, Source Ledger, storage, Asset, and
Retrieval interactions were reduced to intentional public contracts.

### Close the Knowledge Graph module boundary

#### Improvements

Knowledge Graph now enforces a closed public API and an exact outgoing
dependency allowlist after Asset, Source Ledger, ACL, Space, and embedding
profile persistence access was replaced with owned query and registry
contracts. This completes the independently reviewed Graph closure.

### Close the Source Ledger module boundary

#### Improvements

Source Ledger now enforces a closed public API and an explicit allowlist for
its ACL, storage, organization, permission, and shared dependencies.

This completes the mechanical closure gate required by the independent
architecture review.

### Close the Knowledge Space module boundary

#### Improvements

Knowledge Space now enforces a closed public API with an explicit dependency
allowlist limited to `authorization`, `knowledge.sourceledger`, `organization`,
`permission`, `shared`, and `shared::error`. This completes the independently
reviewed Space closure after sibling repository access was replaced with an
owned query API.

### Isolate connector read views from Source Ledger persistence

#### Improvements

Connector inventory and activity views now use a Source Ledger-owned read
boundary instead of consuming its repository, entity status, and aggregate
projection types directly. The immutable query result preserves active and
archived counts, latest activity time, and active external object ids.

### Make live connector polling consistent and rotation-aware

#### Improvements

Slack, Google Drive, and GitHub now run through one connector polling driver
for connection isolation, content cadence, failure activity, and
credential-derived client lifecycle. Clients retain safe token and rate-limit
state across polls, rebuild after credential or client-identity changes, and
retire when a connection disappears. A crawl in which at least half of the
eligible source units cannot be read is reported as `mostly_failed` instead of
advancing a broadly degraded checkpoint, while all existing crawl and
component cursor bytes remain unchanged.

### Isolate connector source lifecycle operations

#### Improvements

Connector reconciliation now resolves source identity, diffs active inventory,
and retires tombstoned sources through Source Ledger-owned query and lifecycle
APIs. Connector no longer consumes the Source Object repository, entity, or
status enum for these flows.

### Isolate connector source revision transactions

#### Improvements

Source Ledger now owns connector revision lookup, evidence staging, completion,
and atomic graph-job scheduling behind revision commands and immutable draft
facts. Connector no longer consumes Source Ledger repositories/entities or the
Graph queue, while independent transaction boundaries remain enforced.

### Safer, leaner knowledge publication on OpenSearch

#### Improvements

Publishing a new knowledge generation on OpenSearch now runs through one
coordinated copy-forward protocol with durable ownership: a publisher can no
longer take over a copy that another process is still performing, failed
copies leave an explicit durable failure state and clean up their partial
output, and the previous generation streams across in bounded pages instead
of being loaded into memory whole. Stored documents are preserved
byte-for-byte.

### Keep public docs hydration stable

#### Fixes

The public documentation now renders its category selector consistently during
static generation and browser hydration. English and Vietnamese documentation
routes no longer emit React hydration errors when opened from a fresh page.

### View and retire governed documents

#### Features

Let users inspect document metadata, safely view supported document content,
and retire eligible manual uploads directly from the Documents workspace while
preserving governed evidence and access-control checks.

### Recover graph publication safely across worker restarts

#### Fixes

Graph indexing now binds each cross-store publication to a durable commit
permit and claim epoch. Retries resume the exact permitted PostgreSQL and
OpenSearch attempt after a worker restart, never discard staging whose
visibility is uncertain, fence abandoned copy-forward work, invalidate graph
caches before completing the job, and require durable proof before cleaning up
a competing attempt.

### Explain effective document access

#### Fixes

Show administrators the OpenFGA relationship decision and the canonical
content-policy decision separately, while keeping denied resource metadata
behind audit-view permission and technical identifiers out of the primary UI.

### Activate evaluated production RAG routes

#### Improvements

Production now defaults Answer to `gpt-5.6-sol`, Keyword Planning to
`gpt-5.6-luna` with reasoning disabled, and Graph Extraction to
`gpt-5.4-mini`, preserving the independently evaluated quality and latency
split across deployments and shared ZM development.

### Select an explicit Apache AGE topology backend

#### Features

PostgreSQL GraphRAG now selects either Apache AGE or relational topology as an
explicit runtime backend. Apache AGE is the production default, fails startup
instead of silently falling back, and serves publication-batch-pinned,
authorization-filtered topology while PostgreSQL retains canonical evidence.

### Import governed Skills from GitHub

#### Improvements

Discover Skills in a public or administrator-approved private GitHub
repository, preview the exact commit and valid packages, then import selected
Skills as independent governed Drafts. Private access stays server-side through
an approved GitHub App connection, and every Draft retains immutable source
provenance.

### Accurate review actions in the governance workspace

#### Improvements

The governance workspace now shows exactly the review actions the server
permits: a revision author can request changes or reject, and only approval
is withheld, matching the enforcement rule. Action availability comes from
the server instead of being derived in the browser, so what you see always
matches what you may do. Authorization rechecks behind search, citations,
and graph answers now share one hardened implementation with their existing
per-surface behavior preserved.

### Remove Graph access to Asset persistence

#### Improvements

Knowledge Graph indexing and curation now consume immutable Asset-owned query
contracts instead of Asset repositories, JPA entities, and the chunk projection
store. This follows the independently reviewed architecture direction by
removing an implementation edge before the Graph module is closed.

### Use the LightRAG mini model for graph extraction

#### Fixes

Default graph extraction independently to `gpt-5.4-mini` so changing the
Assistant model no longer changes indexing behavior, while preserving a
dedicated deployment override and immutable processing profiles.

### Reliable graph indexing recovery and hardened graph export

#### Fixes

Cancelling a graph indexing job while it is processing no longer wedges the
indexing queue after its lease expires; cancelled jobs settle into a terminal
state and the queue keeps flowing. Connector documents whose embedding or
publication step failed mid-run now retry cleanly instead of leaving a staged
revision that references deleted content. Short knowledge queries with an
empty trusted keyword plan no longer fail with an internal error. Graph CSV
exports neutralize leading spreadsheet formula characters so exported cells
cannot execute as formulas when opened in Excel.

### Remove Graph access to embedding profile persistence

#### Improvements

Knowledge Graph indexing now resolves immutable embedding profiles through the
Retrieval-owned registry instead of consuming the profile repository and JPA
entity. This follows the independently reviewed architecture direction by
removing the final Graph persistence edge before its module-closing cycle.

### Remove Graph access to Source revision persistence

#### Improvements

Knowledge Graph indexing now resolves immutable source-revision state through
a Source Ledger-owned query instead of consuming revision repositories, JPA
entities, and persistence status directly. This follows the independently
reviewed architecture direction by removing another implementation edge before
the Graph module is closed.

### Faster document ingestion and graph indexing

#### Improvements

Staged projection writes now travel in bounded batches instead of one
statement per row, and the ingestion and graph-indexing workers process a
bounded burst of queued jobs per cycle instead of a single job, so backlogs
drain far sooner while maintenance jobs and the other queue keep running.
Failure behavior, publication atomicity, and stored data are unchanged.

### Faster, leaner storage and connector adapters

#### Improvements

Graph storage adapters now avoid repeated remote round trips on hot paths:
vector staging verifies each physical index once per batch, entity degrees are
computed in a single pass, and Slack crawls build their member directory once
per crawl instead of per thread. Duplicate and dead adapter code paths were
removed and model-key handling was hardened, with no change to stored data,
cursors, or fingerprints.

### Bind independent production AI model routes

#### Fixes

Production API and worker configuration now bind Keyword Planning independently
to `gpt-5.6-luna` with reasoning `none` instead of silently inheriting the
Answer model from a shared Java default.

### Break the ACL-to-Connector dependency

#### Improvements

ACL now owns its ingestion commands and membership evidence while Connector
maps crawl payloads at the boundary, removing the reciprocal module dependency
without changing connector or source-access behavior.

### Isolate Knowledge Graph lifecycle and processing boundaries

#### Improvements

Knowledge Graph indexing, processing profiles, lifecycle operations, curation,
exploration, and export now share an explicit module boundary, reducing coupling
and making future graph changes safer to verify and release.

### Isolate retrieval and embedding contracts

#### Knowledge retrieval contracts



#### Improvements

Query embedding contracts, embedding profiles, and projection namespaces now
share an explicit retrieval module boundary, making provider integration and
future retrieval changes easier to verify safely.

### Complete the Knowledge retrieval module move

#### Knowledge retrieval runtime



#### Improvements

Authorized search, evidence and citation assembly, catalog federation, and
retrieval persistence now share one explicit module boundary, making the
security-critical retrieval flow easier to inspect and evolve safely.

### Finish Knowledge root-package ownership

#### Knowledge root-package ownership



#### Improvements

The final source identity, group view, and failure-message types now live with
their owning Knowledge modules, leaving the parent package free of domain
types and ready for enforced boundary closing.

### Record the completed production proof for reliable MCP search

#### Documentation

The engineering roadmap now records MCP search reliability as shipped after
the timeout and result-schema repairs were deployed and verified through a
real Claude `search_knowledge` call.

### Restore production AI gateway configuration binding

#### Fixes

Production API and worker processes now retain the complete configured AI
gateway when profile-specific credentials are applied, preventing startup
failure after adding gateway capability flags.

### Publish the product changelog

#### Documentation

Publish a localized product changelog in the documentation, generated from the
same reviewed Tegami release history used for GitHub Releases.

#### Security

Harden documentation responses with a content security policy, HSTS, explicit
HTML revalidation, and removal of framework disclosure headers.

### Route RAG workloads by model role

#### Improvements

Administrators can now configure independent Answer and Keyword Planning model
routes, including an explicitly supported OpenAI reasoning effort. Graph
Extraction remains visible as a read-only pinned processing route so changing
interactive model settings never rewrites existing document graph history.

### Hand governed Skill work to local coding agents

#### Improvements

Copy bounded instructions into a local coding agent to validate and upload a
private Skill Draft, or install one exact released Skill into Claude Code or
Codex. Every handoff shows its confirmation boundary, uses the existing
OrgMemory CLI and current access, and keeps publication and sharing as separate
governed actions.

### Remove empty Skill authoring side cards

#### Improvements

Skill creation, upload, replacement, and GitHub import no longer reserve a
large side card for explanatory text. Package details appear only after a real
inspection, while the one relevant private-repository note stays beside its
input.

### Deliver the pinned public Skill CLI handoff

#### Features

Pin Skill authoring and installation handoffs to `@orgmemory/cli@0.1.1`, keep
the CLI's package, OAuth, and MCP identity versions aligned, and document the
same exact `npx` lifecycle in English and Vietnamese.

### Harden the first Skill CLI publication

#### Fixes

Verify the packed OrgMemory CLI executable before publication and bind npm
provenance to the exact repository, so the first public package cannot succeed
with a missing `orgmemory` command or an unrelated source identity.

### Complete the governed Skill CLI lifecycle

#### Improvements

Prepare the OrgMemory CLI for provenance-backed npm distribution and complete
the local Skill lifecycle with offline full-tree verification, exact-version
updates, verified-only removal, collision-safe target ownership, serialized
receipt writes, and crash recovery.

### Add target-specific Skill installers

#### Improvements

Released Skills now offer compact, exact-version installers for Claude Code
and Codex. The interface distinguishes verified package integrity and supported
installation from runtime behavior that OrgMemory has not certified.

### Consistent Skill package metadata validation across surfaces

#### Fixes

Skill package metadata keys are now validated identically by the CLI and the
server, closing a drift where whitespace-only keys could pass one surface and
fail another. MCP gateway error handling and CLI package safety helpers were
consolidated so the same rules live in one place per app.

### Route source ingestion through the ACL facade

#### Improvements

Source ingestion now uses an ACL-owned transactional facade instead of
coordinating ACL repositories and persistence entities directly.

### Remove the Source Ledger-to-Asset dependency

#### Improvements

Source Ledger now validates source provenance before calling its own asset
promotion port, while Asset owns promotion persistence and retirement. This
removes the reverse module edge without changing ingestion idempotency,
security lineage, or publication behavior.

### Remove the Source Ledger-to-Connector dependency

#### Improvements

The current source head projection now belongs to Source Ledger, allowing
Connector reconciliation to consume it without creating a reverse module
dependency.

### Remove the Source Ledger-to-Graph dependency

#### Improvements

Source publication now schedules graph indexing through a Source-Ledger-owned
port, while Graph keeps target validation, profile selection, idempotency, and
durable queue persistence behind its adapter.

### Remove the Source Ledger-to-Retrieval dependency

#### Improvements

Source Ledger now depends on its own visibility and embedding-profile
contracts while Retrieval implements the governed adapters, removing the
reverse module edge without weakening authorization or ingestion behavior.

### Remove the Source Ledger-to-Space dependency

#### Improvements

Source Ledger now uses its own compact Space target port for upload and
promotion validation, while Space retains authorization and active-directory
policy behind the adapter.

### Route Space reads through an owned query boundary

#### Improvements

Graph and Retrieval now resolve Knowledge Space existence and active status
through the Space-owned `KnowledgeSpaceQuery` API instead of consuming the
Space repository directly. This follows the independently reviewed architecture
direction to remove implementation edges before closing a nested module.

### Enforce typed Knowledge Space audiences

#### Features

Let administrators choose organization, department, or restricted custom
audiences for each Knowledge Space. Managed audiences cannot be silently
widened, custom viewers fail closed across PostgreSQL and OpenFGA, and the
administration UI explains policy drift without exposing internal identifiers.

### Clearer errors, consistent sizes, and a lighter web app

#### Improvements

Admin AI model screens now surface the real error details returned by the
server instead of a generic message. File sizes display with one consistent
unit convention across the app, and Skill upload limits read the same on
every surface. Source upload no longer applies the confidential-classification
department rule to unrelated classifications. The Sources screen loads the
knowledge-graph viewer only when its tab is opened, the assistant re-renders
far less while streaming, and several unused component kits and dependencies
were removed for a smaller bundle.

## Organizational AI Memory v0.1.1

### Update the Skill CLI command parser

#### Improvements

Update the OrgMemory Skill CLI to Commander 15 while retaining its existing
ESM and Node 24 runtime contract.

### Update graph database runtime dependencies

#### Improvements

Update the Neo4j Java Driver to 6.2 for the graph storage adapter and refresh
the JUnit platform used by the backend test suite.

### Refresh Node application runtime dependencies

#### Improvements

Update the MCP CLI, Assistant web client, and public documentation runtime
dependencies to their latest compatible minor and patch releases.

## Organizational AI Memory v0.1.0

### Changelog layout

#### Documentation

Keep the product changelog title first and render each reviewed release entry
with one clear subject heading.

### Release evidence secret scanning

#### Security

Keep full-history secret scanning enabled while recognizing immutable GHCR
references whose public image tags contain Git commit SHAs.

### Product release management

#### Operations

Add reviewed product changelogs, semantic versions, immutable artifact
manifests, and GitHub Releases without changing the SHA-addressed deployment
pipeline.

### Shared ZM team development

#### Operations

Let developers run local OrgMemory applications against the shared
non-production ZM services through private SSH tunnels, with automatic worker
coordination and guarded post-merge schema/model updates.

### Version pull request preflight

#### Fixes

Allow Tegami to validate a generated Version PR commit while retaining the
independent current-main checks around writable release operations.

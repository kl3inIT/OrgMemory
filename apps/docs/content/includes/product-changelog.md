[//]: # (Generated from release/CHANGELOG.md by Tegami. Do not edit manually.)

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

# Asset Registry Spec

Source: `core/src/main/java/com/orgmemory/core/assetregistry`,
`core/src/main/java/com/orgmemory/core/knowledge/catalog`,
`core/src/main/java/com/orgmemory/core/knowledge/search`,
`core/src/main/java/com/orgmemory/core/knowledge/asset/KnowledgeAssetRetrievalQuery.java`,
`core/src/main/java/com/orgmemory/core/knowledge/retrieval/KnowledgeCatalogService.java`,
`apps/api/src/main/java/com/orgmemory/api/assetregistry`,
`apps/api/src/main/java/com/orgmemory/api/knowledge`,
`apps/worker/src/main/java/com/orgmemory/worker/assetregistry`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, `apps/cli/src`,
`apps/cli/package.json`, `.github/workflows/publish-cli.yml`,
`apps/web/src/features/assets`, and
`integrations/object-storage-minio/src/main/java`.

Reconciled: `2026-08-16-mcp-asset-behavior-hardening (a8eec467)`.

## Current Behavior

The Asset Registry stores generic Asset identity, ownership, authorization,
drafts, immutable revisions, review decisions, and immutable releases.
`PROMPT_TEMPLATE`, `WORK_INSTRUCTION`, `CAPABILITY_PACK`, and `SKILL` are the
enabled payload profiles. Each profile validates its own versioned JSON
contract while the shared registry remains free of type-specific columns.

### Library lifecycle and access

Each new Asset has one canonical human owner and starts as a private mutable
working copy. The owner may publish the working copy directly for every enabled
profile. Publication creates a new immutable Revision and Release with
`publicationMode=DIRECT`; an existing Release is never edited. The first Viewer
share creates version `1.0.0` when no Release exists. Existing reviewed
Releases, review cases, and compatibility commands remain readable, but the
browser contribution path does not create new reviews.

Sharing is explicit Viewer or Editor intent for a user or group. Company-wide
sharing is Viewer-only. Viewers receive a released-only projection that omits
the Draft, unreleased revisions, review comments, and role principals. Editors
may update the working copy. Only the owner may share, publish directly,
transfer ownership, or ordinarily withdraw the Asset. Administrator ownership
recovery is allowed only when the canonical owner is vacant; emergency
withdrawal is a separate permission and does not grant ordinary authoring.

Canonical relationship changes increment an Asset generation, mark
authorization unready, and append versioned `WRITE` or `DELETE` outbox records.
Projection deletes stale tuples before writing replacements. Reads remain
fail-closed until the current generation converges. Group and organization
subjects are projected through their `#member` usersets.

Asset-level withdrawal appends `WITHDRAWN` evidence for every non-withdrawn
Release and retires the identity. Each visible Skill also has a per-user
Enabled preference, defaulting to disabled; Assistant discovery and activation
require both current visibility and Enabled state.

Cross-module Asset vocabulary and business errors are exposed through the
exact parent-owned `assetregistry::api` named interface. Nested implementation
modules implement parent-facing contracts instead of being imported directly
by unrelated top-level modules.

The closed `assetregistry.kernel` module owns the package-private canonical
Asset identity/portfolio entity, accountable roles, authorization outbox
leases, readiness state, and their repositories. Registration and role writes
join parent transactions and persist their authorization intent atomically;
portfolio transitions remain behind immutable parent-facing commands. The
parent retains Draft, revision, review, release, availability, audit, and
catalog read-model persistence. Skill package replacement, submission, and
direct publication serialize on the Draft row rather than exposing a Kernel
lock.

The closed `assetregistry.authorization` module owns only the external OpenFGA
projection and convergence edge. Parent orchestration invokes its projection
through `assetregistry::api`; Worker uses the public convergence entry point.
Queue claim/completion/failure use short independent transactions, while the
external OpenFGA write explicitly rejects an ambient database transaction.

Consumers always address an exact authorized release. A withdrawn release
cannot start new consumption. Forking creates a new Asset draft from an exact
release payload and does not copy reviews or approvals.

Governance views retain legacy ownership-health derivation from active role assignments:
`ownerPresent`, `backupOwnerPresent`, `orphaned`, and `continuityAtRisk`.
Missing ownership coverage is visible in the shared release header; it never
changes release bytes or grants authorization.

### Prompt Template

A Prompt Template release contains exactly one text template or ordered
system/user messages. It declares typed variables, required/default behavior,
sensitivity, optional regex and allowed values, output contract, data policy,
compatibility, Knowledge requirements, limitations, and at most ten bounded
evaluation cases.

Rendering is deterministic and rejects unknown, missing, or invalid variables
before a provider call. Inserted variables and retrieved Knowledge are marked
as untrusted data. Execution pins the exact release digest and resolved AI
route. Optional grounding crosses the parent `knowledge::search` interface into
the canonical permission-aware retrieval path, and the run stores only citation
identifiers plus a sanitized output digest by default. Raw sensitive variables
and raw output are not retained.

Catalog federation and permission-scope resolution read Asset existence,
active authorization scopes, and current active version projections through an
Asset-owned query. Retrieval cannot import Asset repositories or bypass the
query's tenant and lifecycle predicates.

Evaluation executes only the bounded cases embedded in a release. Release
comparison reports the two exact evaluation results; it does not change a
release or promote a mutable alias.

The browser authors schema-v1 text-template Prompts as one atomic private
Draft, including typed variables, up to ten persisted synthetic evaluation
fixtures, and either no Knowledge requirement or optional natural-language
requirements. Prompt authoring reuses the shared Asset identity vocabulary of
name and Description, then groups objective, intended users, use/do-not-use
guidance, and limitations under a Usage contract. Intended users is descriptive
payload metadata and does not grant access; sharing remains independent. The
optional JSON response shape is a separate Output contract. The Knowledge Space chosen during creation is governance
placement, not a grounding target. Ordered-message Drafts remain read-only in
the editor and can be published unchanged. Direct publication snapshots the
last saved Draft as an immutable exact release and does not change sharing.
Release tests run only from the selected released-detail view after explicit
confirmation and report aggregate plus case-level results. The browser exposes
creation-target loading and failure separately, offers an explicit retry for a
transient target failure, and focuses the accessible error before its retry
action. Governance save and direct publication distinguish a successful server
mutation from a later refresh failure: success remains visible, stale mutation
controls stay disabled, and refresh retries do not repeat the mutation.
Refetches preserve unsaved editor state, variable renames migrate persisted
fixture values, and selecting another exact release resets release-local
evaluation state.

### Work Instruction

A Work Instruction release declares purpose, audience, prerequisites,
completion outcome, responsible role, and ordered steps. Each step includes an
expected result, check, optional escalation, prohibited actions, and bounded
Asset or Knowledge references.

Following and acknowledging always use an exact authorized release.
Acknowledgement is actor-derived and idempotent. The parent-owned
`assetregistry::work-instruction` interface carries the operations contract and
the concrete Work Instruction wire/value records. A separate parent-owned
`assetregistry::work-instruction-relations` interface lets generic delivery
delegate profile-specific relation traversal without importing its
implementation. Relation targets are authorized independently; inaccessible
targets collapse into one opaque access-gap flag.

The closed `assetregistry.workinstruction` nested module owns parsing,
follow/acknowledge orchestration, actor-scoped acknowledgement persistence, and
relation traversal. It exposes no public top-level implementation type and
depends only on the exact Asset API/consumption/profile/Work Instruction
contracts, Knowledge catalog, Organization, and Shared entity base. Its move
changes neither the acknowledgement table nor its transaction boundary.

### Capability Pack

A Capability Pack release declares its purpose, audience, prerequisites,
expected outcome, owner, review date, completion criteria, and ordered
required/optional items. It must contain at least one required item. Each item
pins either an exact registry release or an exact Knowledge Asset version.

Assignment and progress are actor-scoped and idempotent. Every component is
authorized independently when the journey is read or updated. Accessible items
retain order; inaccessible components collapse into one opaque access-gap flag
without exposing denied titles, types, or counts. A replacement release never
rewrites an existing Pack pin.

### Skill Package

A Skill is imported through a dedicated multipart endpoint rather than the
generic JSON draft endpoint. The server accepts one bounded ZIP, requires
exactly one Agent Skills `SKILL.md` at the archive root or in one top-level
directory, validates its allowed frontmatter and directory-name identity, and
rejects unsafe paths, case collisions, symbolic links, encrypted or unsupported
entries, invalid UTF-8/YAML, and packages over the compressed, unpacked,
file-count, or `SKILL.md` limits. Package files are read for validation and
hashing but are never extracted or executed.
The experimental `allowed-tools` field is preserved as portability metadata;
it does not grant OrgMemory or an assistant permission to invoke a tool.

The original ZIP is stored behind the Asset Registry storage port. Portable
Skill metadata, SHA-256, size, media type, and file manifest form a
server-generated draft payload. Payload schema 2 may also carry server-derived
GitHub origin repository, full 40-character commit SHA, `SKILL.md` path, and
public/private visibility; schema 1 remains readable for existing releases.
The parent validates the canonical payload against the inspected artifact
before performing any storage write, then separately verifies the metadata
reported by storage before changing the Asset ledger.
The storage object key remains only in the
internal payload-reference ledger. The draft reference is created atomically
with the Asset. The canonical owner may publish that Draft through the same
profile-independent direct command: one transaction creates an immutable Revision and Release, verifies
and copies the exact blob reference through both records, and records
`publicationMode=DIRECT`. The optional reviewed path still copies the reference
on submission and publication and records `publicationMode=REVIEWED`. An active
review must be completed or cancelled before direct publication. Generic
create, draft update, and fork cannot manufacture a `SKILL` payload.

The Node CLI provides a folder-first authoring path. `skill validate` and
`skill publish --dry-run` inspect the local root `SKILL.md`, reject unsafe
filesystem entries, enforce package bounds, and build deterministic ZIP bytes
without authentication or network access. A real `skill publish` requests the
separate `assets:write` scope and sends those bytes to the bounded
`/skill-publications` HTTP companion. The MCP gateway exchanges the actor token
and delegates to the same canonical multipart endpoint above. Core repeats
validation and live `CAN_CREATE_ASSET` authorization and creates a Draft only.
A successful upload returns and prints the exact same-origin Governance URL.
From there the accountable author normally chooses a version and publishes the
Skill directly; an actor who lacks direct-publication authority may use the
reviewed workflow instead.

The browser may prepare a copy-only handoff for that same CLI workflow. The
handoff asks a local agent to validate and dry-run first, requires the actor to
provide the namespace, Knowledge Space UUID, and classification rather than
guessing them, and requires explicit confirmation before upload. It stops when
the private Draft and Governance link exist. The browser does not execute the
command, install the CLI, submit a review, publish a release, share the Asset,
or grant authorization.

The browser provides the same canonical ZIP-import path without introducing a
second lifecycle. The Assets header first chooses the Asset profile; Skill then
opens a creation-only surface. An author may write `SKILL.md` content from
scratch or choose an existing `SKILL.md`, ZIP, or folder. Browser packaging is
deterministic and applies cheap path, file-count, compressed-size, and
unpacked-size bounds before upload. A stateless inspection endpoint runs the
canonical server validator and returns only bounded portable metadata,
instructions, manifest, digest, and size; it neither stores the package nor
creates identity. Editing scratch content invalidates that inspection. Create
and replace always validate the submitted bytes again, so inspection is never
an authorization or integrity grant.

Both creation paths load only live authorized Knowledge Space targets, perform
cheap namespace usability checks, and submit the multipart package through the
generated same-origin CSRF-aware client. Core repeats authorization and
complete structural validation before object storage and Draft creation.
Success opens the ordinary Governance workspace. The browser never executes
package content, grants an owner from a session role, publishes the Skill, or
describes structural validation as content or malware review.

A released Skill detail also provides copy-only instructions and exact
`namespace/slug@version` commands for Claude Code and Codex. The CLI continues
to authenticate, authorize, verify the immutable package and file digests,
promote the staged installation atomically, and write a token-free schema-v2
receipt containing the exact regular-file manifest. Schema-v1 receipts remain
readable but are explicitly unverifiable.

Local lifecycle operations are serialized per project/global scope. The
filesystem lock covers target ownership checks, staging, a durable operation
journal, tree promotion or quarantine, receipt commit, and cleanup. A later
command recovers an interrupted operation before proceeding. One canonical
consumer target has at most one coordinate owner, so different namespaces with
the same slug cannot silently replace each other. Only a confirmed exited PID
is considered abandoned, and lock cleanup is bound to the acquiring operation's
unique owner token.

`skill verify` works offline and reports `verified`, `modified`, `missing`, or
`unverifiable` after comparing the canonical target and complete regular-file
tree. Extra entries, links, non-regular files, missing files, or changed bytes
do not verify. `skill update <coordinate> --to <exact-version>` keeps the same
coordinate, re-authenticates and authorizes the exact destination release, and
refuses a locally changed or legacy source. `skill remove` renames a verified
tree to quarantine, commits receipt removal, then deletes the quarantine. It
has no destructive `--force`; modified and legacy trees require manual cleanup
or a verified reinstall first.

`@orgmemory/cli` owns an independent package SemVer. Consumer version `0.1.0`
is public with registry integrity, repository-bound SLSA provenance, and an
executable `orgmemory` binary. Source version `0.1.1` is the selected activation
release, and browser and documentation handoffs pin its exact `npx` command.
The dedicated manual npm workflow accepts only
the exact current green `main` SHA and matching package version, runs Node 24
frozen-package gates, inspects the tarball, and publishes through a protected
environment with OIDC Trusted Publishing. It has no long-lived npm token or
dependency cache. A rerun may accept an existing immutable version only when
its registry integrity equals the reviewed tarball, then polls until integrity
and provenance are both visible before executing that exact registry version.
The public package includes a proprietary license that permits only authorized,
unmodified execution against accessible OrgMemory services. A selected version
is considered available only after the live registry, provenance, signature,
and exact-version execution proof passes.

GitHub preview, private-connection discovery, and import are server-side
operations gated by Skill-create permission on the selected Knowledge Space.
Preview accepts an
`owner/repository` identifier or GitHub HTTPS URL plus revision and optional
subpath, resolves the revision to a full commit SHA, and discovers at most 20
bounded `SKILL.md` roots without storing them. Public repositories are fetched
anonymously. Private access is available only through a configured GitHub App
connection whose administrator has enabled `allowPrivateSkillImports`; the
browser receives only eligible connection keys after that authorization check
and never receives or submits a
credential. Credential use writes a permission-audit event. The transport
allows HTTPS only at `api.github.com` and `codeload.github.com`, disables generic
redirect following, validates the single API-to-codeload archive redirect, and
never forwards Authorization to codeload.

Import requires the full SHA returned by preview and fetches that exact revision
again. The GitHub tarball has compressed-size, expanded-size, entry-count, path,
link, collision, individual package, and total Skill-count bounds. Files belong
to the nearest containing `SKILL.md` root and each selected package is passed
back through the canonical Skill ZIP inspector. Authorization is checked before
the batch. Each selected Skill then creates its own Asset in an independent
`REQUIRES_NEW` transaction, so duplicate or invalid items return stable per-item
failures without rolling back successful Drafts.
After import, the API resolves each successful Asset view independently. A
temporarily unavailable projection therefore leaves the persisted item marked
as imported with its path and stable read error while preserving every sibling
result, rather than failing the whole batch response.

An actor with live `can_edit` may replace the package attached to a mutable
Skill Draft. Core inspects and stores a fresh object before the transaction,
then repeats authorization and takes the Asset lock. The transaction requires
the expected Draft lock version, deletes only the old `DRAFT` payload
reference, inserts the fresh reference, updates the canonical Draft, records an
audit event, and writes a durable supersession row. Database guards continue to
reject payload-reference updates and all Revision or Release reference
deletion. After commit, cleanup deletes the superseded object only when an
exact organization/reference query proves that no Draft, Revision, or Release
still references it. Otherwise it is retained; transient storage failures stay
in the bounded retry queue. A published Revision or Release therefore keeps
its exact original bytes when the working Draft changes.

The parent exposes four exact Skill capabilities rather than one broad Skill
interface. Package creation/replacement receives a canonical upload through
`skill-package`; exact release delivery returns immutable release and artifact
facts plus content through `skill-delivery`; Worker sees only the bounded
`skill-cleanup` batch operation and summary; and only exact parent
persistence/delivery/cleanup classes plus MinIO may import `skill-storage`.
The parent owns the entire storage and supersession saga. Package semantics,
API results, manifests, audit values, logs, and exceptions do not receive the
persisted object key.

The closed `assetregistry.skill` nested module owns bounded package inspection
and validation, GitHub acquisition orchestration, API-facing Skill operations,
install-manifest construction, and the read-only runtime projection. Its exact
public top-level surface is
`SkillPackageOperations`, `SkillGitHubOperations`,
`SkillDistributionOperations`, `SkillGitHubSourcePort`,
`SkillPackageInspection`, `SkillInstallManifest`, and `SkillPackageContent`.
`SkillRuntimeOperations` is the eighth contract.
Implementations, the package profile and specification, the inspector, and the
validation exception remain package-private. The child imports only the
parent's `skill-package` and `skill-delivery` capabilities; it never imports
parent implementation, storage, cleanup, or a persisted object key. The API
and GitHub connector depend only on the child's operation/source contracts,
and the parent never depends on the child.

### Federated Knowledge

Knowledge remains owned by the canonical Knowledge ledger. The read-only
catalog lists only current active versions visible through the canonical
OpenFGA and ACL scope. Capability Packs reference exact Knowledge version IDs;
the registry does not create duplicate Asset rows or copy Knowledge tuples.
Asset Registry crosses the parent `knowledge::catalog` interface and does not
import the nested Asset or Retrieval catalog implementation. Version-only
resolution computes the actor's canonical scope before any version read, and
missing or denied versions both appear as absence. The API maps the parent
entry to its own response while retaining the public `KnowledgeCatalogItem`
schema and eight-field wire shape.

### Assistant And Web Consumption

The in-app Assistant exposes a closed action allowlist for discovery,
permission-aware Knowledge search, Prompt preparation/render/run, Work
Instruction guidance, Pack start/read/progress, explicit release fork, and
feedback. Recommendations are computed from live `CAN_USE` authorization and
contain an exact non-withdrawn release reference. External provider calls and
every state-changing action require an explicit confirmation flag.

For Agent Skills progressive disclosure, the same Asset Registry also exposes
an actor-scoped runtime view rather than a second registry. Search returns at
most ten live-`CAN_USE` Skill summaries with exact release identifiers.
Activation reopens that exact authorized release, verifies the stored package
and selected entry against the immutable manifest, and returns bounded
`SKILL.md` instructions plus declared resource paths. A resource read accepts
one safe relative path, caps the selected entry at 128 KiB, verifies its size
and SHA-256, and decodes strict UTF-8 without NUL. The runtime never extracts a
filesystem tree or executes package content. `allowed-tools` remains package
metadata and is absent from the runtime authority surface.

Each action appends a trace that pins the actor, action, exact release
references, authorization context, citation identifiers, model route when
applicable, and a sanitized input/output shape or digest. Traces do not retain
raw Prompt variables, provider output, or credentials. The allowlist has no
approval, publication, withdrawal, role/permission mutation, or arbitrary
execution action.

The authenticated web application provides four generic surfaces:

- **Assets** is one surface with `Available to me | Created by me` scope navigation.
  `Available to me` lists only the latest non-withdrawn exact release the current
  actor can use. `Created by me` is an owner workspace that includes Draft-only and
  released Assets whose active direct `OWNER` assignment belongs to the actor
  and whose live `can_view` decision still allows access; owned results link to
  the Asset workspace. The clean URL selects `Available to me`, while `scope=MINE` selects the
  owner workspace. Search and scope form the primary row; compact type, sort,
  layout, and result controls form a secondary row so additional Asset types do
  not expand the page horizontally. Search, scope, type, sort, layout, and page
  are URL state; the grid is the clean-URL default and the list remains
  available. Both server collections return bounded pages, authorized totals,
  and explicit stable orders. Grid cards are single click targets with compact
  type-specific SVG marks, human owner names when directory access permits,
  sharing state, and a subordinate per-user `Use in Assistant` switch for Skills;
  they never expose package coordinates or shortened owner identifiers. An
  `Add asset` menu preserves the shared profile
  taxonomy without a full-page catalog duplicate. Skill opens a creation-only
  route whose scratch, upload, and GitHub paths create governed private Drafts
  through canonical package validation. Prompt opens a responsive visual
  authoring route, presents the same name-and-Description identity as Skill,
  separates Prompt Usage and Output contracts, and creates the Asset plus
  populated private Draft atomically;
  Work Instruction and Capability Pack remain visible but non-interactive. The
  browser does not infer authorization or owner identity from the session.
- **Asset detail / use** shares identity, provenance, and release selection,
  then renders Prompt, Work Instruction, Capability Pack, or Skill profile
  actions. Consumption is primary; provenance is disclosed on demand and
  governance is shown only to accountable actors. A released Skill keeps
  package integrity separate from consumer behavior. **Install with...** lists
  only the Claude Code and Codex adapters implemented by the official CLI and
  opens one target-specific, exact-version handoff. **Verified package** means
  archive and file integrity; **Install supported** means a deterministic CLI
  adapter exists; **Runtime behavior not certified** means OrgMemory does not
  claim how either consumer interprets or executes the Skill. The web
  descriptors are a feature-local projection of CLI values, not persisted
  compatibility metadata or authorization.
- **Pack journey** preserves ordered exact pins, required/optional progress,
  opaque access gaps, and replacement-release impact.
- **Asset workspace** makes the working copy primary, then exposes sharing,
  ownership transfer, direct immutable publication, asset-level withdrawal,
  revision history, Release history, and read-only legacy review history.
  Skill working copies also disclose bounded package metadata and full digest.
  The confirmation states that structural validation is not an independent
  content review. Per-Release deprecation is not a primary browser action.

Before rendering mutation controls, the web application asks Core for the
current actor's live `can_edit_draft`, `can_manage_sharing`,
`can_transfer_ownership`, `can_publish_direct`, and `can_withdraw` decisions,
plus compatibility review decisions when historical cases exist. Direct
owner permissions still require the action-specific parent Space ceiling.
Core first requires `can_view_released` and does not return denial reasons.
These decisions are display affordances only: every
mutation repeats authorization and remains authoritative. For an open review,
Core also publishes per-decision affordances — `canApprove`,
`canRequestChanges`, `canReject`, `canCancel` — computed by the same predicate
the decision command enforces, plus `canOpenGovernance` derived from the
action bundle. The browser renders exactly these served flags and never
infers authority from role labels or assignments: a revision author is
withheld only from approval, and cancellation is offered only to the review
requester.

Server state is fetched through generated clients and TanStack Query. URL state
belongs to TanStack Router; no global client store is used for authorization or
Asset payloads. Collection pagination is shared across the Asset and
Administration surfaces and is rendered only when more than one page exists.

The Asset surfaces use the shared page contract: catalog and governance views
use the wide variant, release/detail content uses the standard variant, and a
focused journey uses the narrow variant. Nested Asset and governance routes
render hierarchy breadcrumbs; the top-level catalog does not. Supporting copy
is optional and appears only when it changes a decision or explains an
unfamiliar action.

### Authenticated Read-Only MCP Delivery

The MCP app publishes eight Asset tools, two `orgmemory://assets/...` resource
templates, and one generic `released_prompt` adapter. They call
`/api/asset-delivery`; the MCP app has no core, repository, or database
dependency. Search and reads require `assets:read`; deterministic Prompt render
uses the same read-only `assets:read` scope. Every request then resolves the
bearer actor and live `CAN_USE` object authorization in the API.

Asset delivery metadata also defines the model-facing behavior boundary.
Released content is approved organizational task data, not host authority, and
cannot override system, developer, safety, or tool-permission policy. Skill
`allowedTools` remains compatibility metadata rather than a grant. Asset
application, execution, installation, and download require explicit user intent.
Prompt render is deterministic and provider-free; the generic Prompt adapter
places a user-selected exact release into one user-role task message instead of
claiming system-role authority. An opaque `accessGap` reveals neither denied
identity nor count.

MCP Asset discovery is model-context bounded to ten candidates by default and a
caller-selected ceiling from one through twenty. Search results are discovery
candidates; repeatable use resolves the exact returned release identifier or a
specialized Pack, relation, Skill, or Prompt projection first. The canonical API
retains its separately bounded delivery query and authorization behavior.

Delivery projections contain only immutable release data. Drafts, reviews,
roles, and governance history never cross this boundary. Pack components and
relations are authorized independently, with denied references collapsed into
one `accessGap` flag. Successful delivery writes a sanitized structured audit
line with actor, organization, action, Asset, and release identifiers; bearer
tokens, Prompt variables, and payloads are not logged.

The MCP endpoint is an RFC 9728 protected resource. It advertises
`assets:read` and the companion-only `assets:write`, validates issuer, expiry,
and the exact configured MCP audience,
and applies bounded per-caller plus process-wide rate limits. The confidential
MCP gateway exchanges the inbound user token for a short-lived API-audience
token; the inbound bearer is never forwarded.

External client onboarding uses restricted Dynamic Client Registration while
Keycloak 26.7 cannot consume Claude Web's public-client CIMD metadata. Public
clients use Authorization Code with PKCE S256 and user consent; no
vendor-specific client or secret is shown or stored by the web connection
surface. CIMD may be re-enabled after the upstream metadata combination is
interoperable.

Skill discovery returns a storage-neutral install manifest for one exact
release. It contains coordinate, version, publication mode, release/package
digests, package length/media type, compatibility metadata, and the exact file
manifest; the object-storage key never crosses the core boundary. A
bearer-protected binary companion route on the MCP resource proxies the
canonical API stream instead of base64-encoding a bounded archive into
JSON-RPC.

The authenticated web Asset detail reads the same exact manifest through a
browser-session-only consumption endpoint. It still applies live object
authorization, while bearer clients must use the delivery endpoint with
`assets:read`; the browser route cannot bypass that coarse OAuth scope.

The Node CLI resolves and searches through MCP, downloads the exact package
through that companion route, verifies the archive and every file against the
manifest, then installs through an adjacent staging directory. Project-local
Claude Code and Codex targets come from a fixed mapping. The receipt is written
only after promotion and never contains OAuth credentials. OAuth
state is isolated by server and requested scope set, so read-only installation
does not silently gain the Draft-publication grant.

### Golden POC Fixture

`demo/fixtures/asset-registry` is the synthetic, deterministic L1 Support
fixture. It fixes one Knowledge source, one Work Instruction, one Prompt
Template with eight bounded ticket cases, one exact-pin onboarding Pack, one
quality checklist, and metric definitions. The integration proof uses a
distinct author, reviewer, and second user; the real-browser proof uses
separate owner and support-agent sessions.

## Source Modules

- `core.assetregistry`
- `core.assistant.AssistantAssetToolService`
- `core.knowledge.catalog`
- `core.knowledge.retrieval.KnowledgeCatalogService`
- `apps.api.assetregistry`
- `apps.api.assistant.AssistantAssetToolController`
- `apps.api.knowledge.KnowledgeCatalogController`
- `apps.mcp.AssetDeliveryTools`
- `apps.mcp.AssetDeliveryResources`
- `apps.mcp.ReleasedPromptAdapter`
- `apps.cli`
- `web.features.assets`

## Explicitly Deferred

- controlled SOP effectivity
- runtime compatibility policy enforcement beyond deterministic installation
- cross-company public marketplace, ratings, and social publishing

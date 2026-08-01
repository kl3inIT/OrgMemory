# Asset Registry Spec

Source: `core/src/main/java/com/orgmemory/core/assetregistry`,
`apps/api/src/main/java/com/orgmemory/api/assetregistry`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, `apps/cli/src`, and
`apps/web/src/features/assets`.

Reconciled: `2026-08-01-skill-cli-distribution-lifecycle (4100c772)`.

## Current Behavior

The Asset Registry stores generic Asset identity, ownership, authorization,
drafts, immutable revisions, review decisions, and immutable releases.
`PROMPT_TEMPLATE`, `WORK_INSTRUCTION`, `CAPABILITY_PACK`, and `SKILL` are the
enabled payload profiles. Each profile validates its own versioned JSON
contract while the shared registry remains free of type-specific columns.

Consumers always address an exact authorized release. A withdrawn release
cannot start new consumption. Forking creates a new Asset draft from an exact
release payload and does not copy reviews or approvals.

Every Asset view derives ownership health from active role assignments:
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
route. Optional grounding uses the canonical permission-aware retrieval path,
and the run stores only citation identifiers plus a sanitized output digest by
default. Raw sensitive variables and raw output are not retained.

Evaluation executes only the bounded cases embedded in a release. Release
comparison reports the two exact evaluation results; it does not change a
release or promote a mutable alias.

### Work Instruction

A Work Instruction release declares purpose, audience, prerequisites,
completion outcome, responsible role, and ordered steps. Each step includes an
expected result, check, optional escalation, prohibited actions, and bounded
Asset or Knowledge references.

Following and acknowledging always use an exact authorized release.
Acknowledgement is actor-derived and idempotent.

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
The storage object key remains only in the
internal payload-reference ledger. The draft reference is created atomically
with the Asset. An accountable owner-class actor may publish that Draft
directly: one transaction creates an immutable Revision and Release, verifies
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

`@orgmemory/cli` owns an independent package SemVer. The dedicated manual npm
workflow accepts only the exact current green `main` SHA and matching package
version, runs Node 24 frozen-package gates, inspects the tarball, publishes
through a protected environment with OIDC Trusted Publishing and provenance,
and verifies the registry package and executable. It has no long-lived npm
token or dependency cache. The public package includes a proprietary license
that permits only authorized, unmodified execution against accessible
OrgMemory services. The initial registry bootstrap remains an explicit owner
operation; product UI and public docs must not render a pinned `npx` command
until that exact version has been verified live.

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

### Federated Knowledge

Knowledge remains owned by the canonical Knowledge ledger. The read-only
catalog lists only current active versions visible through the canonical
OpenFGA and ACL scope. Capability Packs reference exact Knowledge version IDs;
the registry does not create duplicate Asset rows or copy Knowledge tuples.

### Assistant And Web Consumption

The in-app Assistant exposes a closed action allowlist for discovery,
permission-aware Knowledge search, Prompt preparation/render/run, Work
Instruction guidance, Pack start/read/progress, explicit release fork, and
feedback. Recommendations are computed from live `CAN_USE` authorization and
contain an exact non-withdrawn release reference. External provider calls and
every state-changing action require an explicit confirmation flag.

Each action appends a trace that pins the actor, action, exact release
references, authorization context, citation identifiers, model route when
applicable, and a sanitized input/output shape or digest. Traces do not retain
raw Prompt variables, provider output, or credentials. The allowlist has no
approval, publication, withdrawal, role/permission mutation, or arbitrary
execution action.

The authenticated web application provides four generic surfaces:

- **Assets** is one surface with `All Assets | My Assets` scope navigation.
  `All Assets` lists only the latest non-withdrawn exact release the current
  actor can use. `My Assets` is an owner workspace that includes Draft-only and
  released Assets whose active direct `OWNER` assignment belongs to the actor
  and whose live `can_view` decision still allows access; owned results link to
  Governance. The clean URL selects `All Assets`, while `scope=MINE` selects the
  owner workspace. Search and scope form the primary row; compact type, sort,
  layout, and result controls form a secondary row so additional Asset types do
  not expand the page horizontally. Search, scope, type, sort, layout, and page
  are URL state; the grid is the clean-URL default and the list remains
  available. Both server collections return bounded pages, authorized totals,
  and explicit stable orders. An `Add asset` menu preserves the shared profile
  taxonomy without a full-page catalog duplicate. Skill opens a creation-only
  route whose scratch, upload, and GitHub paths create governed private Drafts
  through canonical package validation; unsupported profiles remain visible but
  non-interactive. The browser does not infer
  authorization or owner identity from the session.
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
- **Governance workspace** exposes revision comparison, optional review,
  release history, deprecation, and withdrawal through the registry's existing
  authorization checks. A newly authored Draft opens in a dedicated Draft
  section. Skill Drafts disclose their bounded package metadata and full
  digest; an authorized owner-class actor chooses a version and publishes the
  package directly. The confirmation states that structural validation is not
  an independent content review. Historical review evidence remains readable.

Before rendering mutation controls, the web application asks Core for the
current actor's live `can_edit`, `can_submit_review`, `can_review`, `can_publish`,
`can_publish_skill`, and `can_withdraw` decisions on the Asset. The Skill-only
direct permission is owner-class and still requires `can_create_asset` on the
parent Space. Core first requires `can_view` and does not return denial reasons
or relationship data. These decisions are display affordances only: every
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
- `core.knowledge.KnowledgeCatalogService`
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
- first public npm publication and registry activation of the prepared CLI
- cross-company public marketplace, ratings, and social publishing

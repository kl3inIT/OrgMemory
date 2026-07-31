# Asset Registry Spec

Source: `core/src/main/java/com/orgmemory/core/assetregistry`,
`apps/api/src/main/java/com/orgmemory/api/assetregistry`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, `apps/cli/src`, and
`apps/web/src/features/assets`.

Reconciled: `2026-07-31-skill-direct-sharing`.

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
server-generated draft payload; the storage object key remains only in the
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

- **Assets** lists only the latest non-withdrawn exact release the current actor
  can use. A prominent controlled projection switches between the shared
  catalog and its Prompt Template, Work Instruction, Capability Pack, and Skill
  profiles. Search, type, sort, layout, and page are URL state; the grid is the
  clean-URL default and the list remains available. The server returns a bounded
  page plus the authorized total and applies an explicit stable order.
- **Asset detail / use** shares identity, provenance, and release selection,
  then renders Prompt, Work Instruction, Capability Pack, or Skill profile
  actions. Consumption is primary; provenance is disclosed on demand and
  governance is shown only to accountable actors.
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
current actor's live `can_submit_review`, `can_review`, `can_publish`,
`can_publish_skill`, and `can_withdraw` decisions on the Asset. The Skill-only
direct permission is owner-class and still requires `can_create_asset` on the
parent Space. Core first requires `can_view` and does not return denial reasons
or relationship data. These decisions are display affordances only: every
mutation repeats authorization and remains authoritative. The browser never
infers authority from role labels, and a revision author is not offered a
self-review decision.

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
Claude Code and Codex targets come from a fixed mapping. The atomic lock receipt
is written only after promotion and never contains OAuth credentials. OAuth
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
- Skill draft package replacement and orphan cleanup
- Skill update/remove commands and compatibility policy enforcement
- public npm publication and signed CLI release automation
- cross-company public marketplace, ratings, and social publishing

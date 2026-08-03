# Asset Registry Skill Boundary Challenge Verdict

Date: 2026-08-02  
Reviewed baseline: `6b36e1282dab70e4b224c17d4069e8749ad3edb7`

## Review Execution

Claude Fable 5 was invoked twice through Orca terminal
`term_d9a08544-7543-40bf-a4b4-bb09c58a0823`. Both the original
file-directed request and the required recovery request returned blank,
zero-token responses, so the configured reviewer was unavailable.

The repository-mandated fallback ran independently and read-only in Orca
terminal `term_c3c1d2b2-52f7-421a-a306-bfdd719d234a` with
`gpt-5.6-sol` at `ultra` reasoning. It inspected the governing documents,
the current implementation and tests, the exact external consumers, and the
pinned comparable sources. It then completed a second counterattack round
against its own first verdict. No repository edit or commit was made by the
reviewer.

## Final Verdict

Accept one immediately closed `assetregistry.skill` module, with mandatory
corrections. The first proposal is rejected because a single
`assetregistry::skill` interface would be a capability dumping ground and
because moving only cleanup orchestration would split the supersession retry
aggregate.

The counterattack further corrected the first-round verdict:

- remove `SkillPackageReferenceFacts` entirely;
- do not let `assetregistry.skill` consume `skill-storage` or orchestrate
  supersession cleanup;
- make the parent Asset Registry own the complete cross-store artifact saga,
  including storage write, compensation, Draft replacement, reference pinning,
  immediate cleanup, and scheduled retry;
- keep `objectKey` inside the storage capability and parent persistence only.

## Strongest Counterargument

Inspection, GitHub acquisition, authoring, delivery, storage, and cleanup have
different consumers and trust surfaces. Moving their current public types into
one child package could improve directory shape while making the real
capability boundary less precise. Conversely, making each concern a nested
module would introduce modules without independent aggregates or transaction
owners.

The selected answer is one Skill semantics module plus four exact parent-owned
capabilities. Skill decides what a valid package means. The parent decides how
validated bytes become, replace, pin, distribute, and eventually leave a
governed Asset.

## Binding Ownership

`assetregistry.skill` owns:

- bounded archive inspection and canonical Skill payload validation;
- Skill specification, schema parsing, and profile semantics;
- GitHub acquisition and partial-result orchestration;
- API-facing package, GitHub, and distribution operations;
- install-manifest construction and package-integrity interpretation.

The parent `assetregistry` module owns:

- authorization and governed Asset identity;
- object-storage writes and database-failure compensation;
- Draft, revision, release, and payload-reference persistence;
- the supersession row, lock, retry state, immediate cleanup, and scheduled
  cleanup;
- exact-release resolution and storage opening.

This keeps the indivisible `REQUIRES_NEW` Draft replacement in
`AssetRegistryCoordinator.replaceSkillDraft`, including the Draft lock,
reference mutation, Draft mutation, and supersession insert. Revision and
Release reference pinning also stay with the parent. The complete cleanup
aggregate remains parent-owned because it locks the supersession row, checks
all live references, deletes or retains the object, and either removes the row
or records bounded retry state.

## Exact Parent Capabilities

The four named interfaces have closed type and consumer sets:

| Named interface | Exact top-level types | Permitted consumers |
| --- | --- | --- |
| `assetregistry::skill-package` | `SkillPackageAssetCommand`, `SkillPackagePayloadPolicy`, `SkillPackageArtifact`, `SkillPackageUpload` | parent Asset implementation and `assetregistry.skill` only |
| `assetregistry::skill-delivery` | `SkillReleaseDeliveryQuery`, `SkillReleaseDescriptor`, `SkillReleaseContent` | `assetregistry.skill` only |
| `assetregistry::skill-cleanup` | `SkillPackageCleanupOperations`, `SkillPackageCleanupSummary` | Worker only |
| `assetregistry::skill-storage` | `SkillPackageStoragePort` and its nested write, stored-package, and content values | exact parent persistence/delivery/cleanup classes and MinIO only |

No capability may expose a JPA entity, repository, lock handle, transaction
status, supersession ID, retry entity, mutable state, `AssetView`, controller
DTO, or generic fact map. Only `skill-storage` may carry an object key.

`assetregistry.skill` may depend on `skill-package` and `skill-delivery`, but
not on `skill-storage` or `skill-cleanup`. API imports none of the four parent
capabilities. Worker imports only `skill-cleanup`. MinIO imports only
`skill-storage`. Exact named-interface membership and exact importer sets are
executable build gates.

## Closed Skill Surface

The child module's exact public top-level surface is:

- `SkillPackageOperations`
- `SkillGitHubOperations`
- `SkillDistributionOperations`
- `SkillGitHubSourcePort`
- `SkillPackageInspection`
- `SkillInstallManifest`
- `SkillPackageContent`

All Spring implementations, `SkillPackageSpec`, inspector, profile, parser,
and validation exception are package-private. GitHub visibility moves from the
internal package specification into `SkillGitHubSourcePort`.

The child module's exact dependency allowlist is:

- `assetregistry::api`
- `assetregistry::consumption`
- `assetregistry::profile`
- `assetregistry::skill-package`
- `assetregistry::skill-delivery`
- `organization`
- `permission`
- `shared::error`

It imports no parent default-package implementation, entity, repository,
Authorization implementation, Kernel implementation, storage contract, or
cleanup contract.

## Storage-Reference Constraint

`assetregistry.skill` submits a bounded `SkillPackageUpload` to the parent. The
parent writes storage, receives and persists the object key, and performs
compensation or cleanup. The child never receives the stored object key.

For delivery, the parent authorizes the exact release, resolves its reference,
opens storage internally, and returns immutable release and payload facts plus
digest, length, media type, and a content stream. It returns no storage
locator. `SkillInstallManifest`, REST, MCP, Assistant, audit values, logs, and
exception messages must never expose the key.

## Binding Delivery Sequence

### PR 1 — Parent-owned artifact lifecycle

Target fewer than 60 changed paths:

1. Add failing-first exact named-interface and consumer-isolation tests.
2. Add the four parent capabilities and parent adapters.
3. Move storage write, compensation, replacement cleanup, release/reference
   lookup, and storage opening behind the parent contracts.
4. Keep current Skill classes in the parent package temporarily and route them
   through the new capabilities.
5. Preserve transactions, schema, authorization, wire contracts, partial
   GitHub imports, and cleanup behavior.
6. Pass focused Core/API/OpenAPI/Worker/connector/MinIO/integration gates and a
   terminating clean repository test.

This PR is independently mergeable and introduces no nested Skill module.

### PR 2 — Move and immediately close `assetregistry.skill`

Target fewer than 70 changed paths:

1. Add failing-first closed-module, exact-public-surface,
   forbidden-parent-import, and external-consumer tests.
2. Move the package-semantic production types and focused tests.
3. Introduce the three public operation interfaces with package-private
   implementations.
4. Internalize `SkillPackageSpec` and move GitHub visibility to the source
   port.
5. Update API and connector imports while preserving every wire schema.
6. Add the child as `Type.CLOSED` immediately with the exact dependency
   allowlist.
7. Prove it has no dependency on the parent default package, repositories,
   entities, `skill-storage`, or `skill-cleanup`, then pass all focused and
   terminating clean gates.

No intermediate open Skill module, schema change, storage-reference exposure,
cleanup-protocol redesign, or product behavior change is permitted.

## Comparable Sources

- AgentRegistry pin: `d8d3f4ef1ebeed70d58adafd26590ead6198addf`
- Vercel Skills pin: `1164afa5f0e21ebd01e6fc11249759353f494ad1`

These sources inform package validation and distribution mechanics. They do
not override OrgMemory's governed Asset lifecycle, organization-scoped
authorization, immutable release pins, or cross-store retry requirements.

## Rejected Alternative

Rejected: one parent-owned `assetregistry::skill` interface containing every
API, Worker, connector, storage, lifecycle, and implementation contract, with
cleanup behavior moved into Skill. It broadens unrelated capabilities, leaks a
storage locator toward application consumers, weakens exact dependency
enforcement, and splits the supersession retry aggregate.

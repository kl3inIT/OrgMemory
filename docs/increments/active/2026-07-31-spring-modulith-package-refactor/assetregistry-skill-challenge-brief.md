# Asset Registry Skill Boundary Challenge Brief

Date: 2026-08-02  
Baseline: `6b36e1282dab70e4b224c17d4069e8749ad3edb7`

Reviewer availability: Claude Fable 5 was launched in fresh Orca terminal
`term_d9a08544-7543-40bf-a4b4-bb09c58a0823`. The original file-directed
request and the required plain-Markdown/no-tools recovery both returned blank
with zero tokens. Per the project challenge procedure, the review therefore
continues in a fresh external Codex `gpt-5.6-sol` session with `ultra`
reasoning; the Fable failure is not treated as a verdict.

## Reviewer Instructions

Act as an adversarial, read-only architecture reviewer. Attack the proposal;
do not validate it by default. Verify every claim in the repository itself.
Make no edits, mutations, commits, or plan changes. Read `CLAUDE.md`,
`docs/conventions.md`, `docs/specs/domains/asset-registry.md`,
`docs/tests/domains/asset-registry.md`, the active increment design/plan, and
the filenames under `docs/decisions` before judging.

Return plain Markdown with:

1. one explicit verdict: accept, accept with mandatory corrections, or reject;
2. the strongest counterargument;
3. a must-fix list with repository evidence for every item;
4. an exact ownership/API/dependency recommendation;
5. whether one code PR below 100 changed files is responsible or which
   independently mergeable sequence is required;
6. the rejected alternative and why it loses.

## Product Promise At Stake

OrgMemory is a governed organizational memory and reusable-capability layer
for enterprise AI. A Skill is not an executable shortcut: it is an immutable,
authorized, integrity-checked package published through the shared Asset
lifecycle and distributed only from an exact usable release. Refactoring must
reduce the 72-file Asset Registry root package without weakening tenant
isolation, authorization, atomic Draft/reference replacement, immutable
Revision/Release pins, bounded archive validation, cleanup durability, or the
existing REST/MCP/CLI wire contracts.

## Exact Proposal Under Review

> Introduce one immediately closed `assetregistry.skill` nested module for
> Skill-specific inspection, profile parsing, import, GitHub acquisition,
> distribution, and storage-cleanup orchestration. Put every contract consumed
> by API, Worker, connector, object-storage, parent Asset lifecycle, or the
> nested implementation into one exact parent-owned
> `assetregistry::skill` named interface. External top-level modules consume
> only that interface. The nested module exposes no public implementation type
> and imports no parent default-package class, entity, or repository.
>
> Keep Draft/revision/release/payload-reference writes and the
> `SkillPackageSupersession` row/repository in the parent Asset Registry because
> replacement creates the supersession row atomically with the locked Draft
> and payload-reference mutation. Expose narrow parent-owned lifecycle/query
> ports to the nested module for authorization, identity creation/replacement,
> exact-release resolution, reference integrity facts, and cleanup state. Keep
> object-storage calls behind `SkillPackageStoragePort`; do not expose storage
> keys in REST/MCP results. Close the module in the same code-bearing PR, keep
> it below 100 changed paths, and preserve every existing behavior and schema.

Today these rules are not enforced. The relevant implementation is spread
across:

- `core/src/main/java/com/orgmemory/core/assetregistry/Skill*.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryService.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryCoordinator.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetPayloadReference.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRelease.java`
- `apps/api/src/main/java/com/orgmemory/api/assetregistry`
- `apps/worker/src/main/java/com/orgmemory/worker/assetregistry`
- `integrations/connectors/src/main/java/com/orgmemory/connectors/github`
- `integrations/object-storage-minio/src/main/java/com/orgmemory/integrations/storage/minio`

The proposed boundary would be enforced in the new Skill `package-info.java`,
the exact parent named-interface `package-info.java`, and
`core/src/test/java/com/orgmemory/core/ModulithVerificationTests.java`.

## Repository Evidence And Known Tension

- The Asset Registry root contains 72 production Java files; 18 are named
  `Skill*`.
- Twelve Skill types are currently public. API, Worker, GitHub connector, and
  MinIO adapter import concrete services, result records, or ports directly.
- `SkillRegistryService`, `SkillGitHubImportService`, and
  `SkillDistributionService` call parent `AssetRegistryService` or consume
  parent entities/repositories.
- The reverse edge also exists: `AssetRegistryService`,
  `AssetRegistryCoordinator`, and `AssetPayloadReference` consume Skill package
  specs/storage values, while the coordinator writes
  `SkillPackageSupersession` in the same `REQUIRES_NEW` Draft replacement
  transaction.
- `SkillPackageSupersessionCleanupCoordinator` currently locks the cleanup row,
  checks all Draft/Revision/Release references, calls object storage, and
  records retry state inside one `REQUIRES_NEW` transaction.
- A direct file move therefore creates a parent/child module cycle or publishes
  JPA internals. The proposed lifecycle ports avoid both, but may become an
  oversized artificial API or split one consistency boundary incorrectly.

The reviewer must specifically decide:

1. whether one `skill` module is coherent or inspection/import, distribution,
   and cleanup should be separate modules;
2. whether the parent named interface is too broad and should be divided into
   exact `skill-package`, `skill-source`, `skill-distribution`, and/or internal
   lifecycle interfaces;
3. whether supersession persistence and cleanup belong entirely in the parent,
   entirely in Skill, or across a port, without weakening atomic replacement;
4. whether package-private Spring implementations behind parent interfaces are
   preferable to retaining selected public nested services for API/tests;
5. the smallest safe code PR sequence below 100 changed files.

## Comparable Source Evidence

| System and pin | Observed mechanism | File-level evidence | Relevance and limit |
| --- | --- | --- | --- |
| AgentRegistry `d8d3f4ef1ebeed70d58adafd26590ead6198addf` | The public Skill API is a compact typed envelope; a dedicated controller owns source resolution and depends on a narrow store interface. It records an immutable commit pin but deliberately stores no Skill content. | `D:/OrgMemory/tmp/upstream-agentregistry/pkg/api/v1alpha1/skill.go:3-53`; `D:/OrgMemory/tmp/upstream-agentregistry/internal/registry/controller/skill_controller.go:20-79` | Supports separating Skill-specific resolution from the generic registry store. It cannot justify moving OrgMemory's blob/reference transaction because AgentRegistry explicitly does not own stored Skill bytes. |
| Vercel Skills `1164afa5f0e21ebd01e6fc11249759353f494ad1` | Archive validation is isolated behind bounded entry/byte limits and safe-path normalization; download/extraction orchestration is separate; installation state pins a full folder hash in a distinct lock model. | `D:/OrgMemory/tmp/skill-registry-research/vercel-skills/src/archive.ts:21-30,150-161,265-346`; `D:/OrgMemory/tmp/skill-registry-research/vercel-skills/src/download-source.ts:134-157,259-335`; `D:/OrgMemory/tmp/skill-registry-research/vercel-skills/src/skill-lock.ts:14-42,209-226` | Supports keeping validation, acquisition, and consumer state as explicit contracts. It has no governed multi-tenant Asset lifecycle, so its local lock/store split cannot override OrgMemory authorization or database atomicity. |

## Operational Cost Motivating The Decision

The root package began this Asset phase with 119 production Java files and is
still at 72 after Kernel, Authorization, and Prompt extraction. Skill alone is
18 root files with consumers across four Gradle subprojects. The current
same-package access hides bidirectional coupling and makes the directory hard
to navigate; moving by filename would either fail Spring Modulith verification
or silently widen internal repositories and storage identifiers. This is the
next material slice, so the boundary must be settled before characterization
tests or production moves are committed.

## Suspected Contradictions For The Counterattack

After the first verdict, challenge it against all three:

1. A single parent `assetregistry::skill` interface may become a dumping ground
   that merely relocates twelve public types without reducing coupling.
2. Keeping supersession persistence in the parent while cleanup behavior sits
   in Skill may split one failure/retry aggregate across modules.
3. Returning storage-reference facts through a public named interface may make
   a secret implementation identifier broadly importable even if ArchUnit pins
   today's consumer.

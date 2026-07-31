# Architecture challenge: browser Skill authoring

Date: 2026-08-01

## Reviewer mandate

Act as an adversarial enterprise architect. Work read-only in
`D:\OrgMemory-worktrees\skill-browser-authoring`, inspect the current source,
and attack this proposal rather than agreeing with the requested UX.

Return exactly:

1. verdict: `ACCEPT`, `ACCEPT_WITH_MUST_FIX`, or `REJECT`;
2. repository evidence for every material claim;
3. strongest counterexample against your own verdict;
4. a bounded must-fix list assigned to PR 1, PR 2, or PR 3;
5. the exact Draft package replacement transaction and cleanup boundary;
6. the exact public/private GitHub credential and network boundary.

Do not edit any file. If your first verdict is accept without constraints,
counterattack it with archive bombs, stale optimistic locks, package reference
aliasing, partial object-store failure, SSRF/redirects, private-repository
credential leakage, branch movement, duplicate imports, and tenant crossover,
then reconsider.

Read `AGENTS.md`, `docs/conventions.md`,
`docs/guidelines/agent-safety.md`,
`docs/specs/domains/asset-registry.md`,
`docs/tests/domains/asset-registry.md`, and the relevant code paths named below.

## Product promise at stake

An employee can contribute a portable Skill without learning the CLI. The
result is a governed private Asset Draft. Browser convenience must not weaken
live organization/Knowledge Space authorization, bounded package validation,
secret handling, immutable release identity, or audit provenance.

## Exact proposal under review

### Navigation and UI

- `/assets` remains the sole catalog and owned workspace.
- `Add asset` becomes a category dropdown.
- Skill opens `/assets/new/skill`, a creation-only surface offering Start from
  scratch, Upload a skill, and Import from GitHub.
- no `/assets/skills` catalog is introduced.

### Draft package replacement

- scratch/upload first inspect bounded content, then create a private Skill
  Draft through Core authorization and the canonical validator;
- editing a Skill Draft replaces only the Draft's package reference under an
  expected lock version;
- replacement writes a new immutable object and never overwrites an existing
  object key;
- Revision and Release references remain pinned to their original object and
  digest;
- cleanup deletes only the superseded Draft object after proving that no
  Draft, Revision, or Release references it; cleanup failure is retained for
  retry rather than rolling back a successful database reference swap.

### GitHub import

- preview accepts a GitHub repository URL, optional revision, and optional
  subpath; it returns safe metadata for bounded discovered Skill roots;
- import resolves the revision to a commit SHA, re-fetches or verifies the same
  immutable content, and creates one Draft per selected Skill;
- public repositories use anonymous server-side fetch;
- private repositories may use an existing encrypted, administrator-managed
  GitHub App source-connection credential;
- no PAT or private key enters or returns to the browser;
- the server restricts hosts and redirects, bounds repository bytes/files and
  discovered Skills, rejects symlinks/traversal, and records repository, commit
  SHA, and path provenance;
- multi-import reports per-item success/failure and is idempotent against
  retries at the resolved coordinate.

## Current repository evidence to verify

| Concern | Current source |
| --- | --- |
| package import orchestration | `core/src/main/java/com/orgmemory/core/assetregistry/SkillRegistryService.java` |
| bounded package inspection | `core/src/main/java/com/orgmemory/core/assetregistry/SkillPackageInspector.java` |
| package references | `core/src/main/java/com/orgmemory/core/assetregistry/AssetPayloadReference.java` and repositories |
| Draft mutation and publication | `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryCoordinator.java` |
| object storage port | `core/src/main/java/com/orgmemory/core/assetregistry/SkillPackageStoragePort.java` |
| REST boundary | `apps/api/src/main/java/com/orgmemory/api/assetregistry/AssetRegistryController.java` |
| stored connector secrets | `apps/api/src/main/java/com/orgmemory/api/admin/AdminConnectorController.java` and Knowledge connector services |
| current Assets UI | `apps/web/src/features/assets/components/asset-catalog-page.tsx` |
| dead chooser | `apps/web/src/features/assets/components/asset-type-selection-page.tsx` |

## Comparable evidence

Onyx Craft exposes the three requested creation paths, inspects imported
bundles before editing, supports supporting files, and previews multiple GitHub
Skill roots. OrgMemory must adapt the interaction while retaining its stronger
governed Asset, Knowledge Space, immutable package, and server-held credential
boundaries.

## Suspected failure scenarios

1. Draft replacement deletes an object already pinned by a Revision or Release.
2. database swap succeeds but object cleanup fails, or object write succeeds
   while the database transaction fails.
3. two tabs replace the same Draft package and the last writer silently wins.
4. preview reads one branch state and import later stores another.
5. a GitHub redirect reaches an internal address or an unapproved host.
6. a private GitHub App credential crosses tenant, repository, browser, log, or
   exception boundaries.
7. retry creates duplicate Assets or leaves unbounded orphan blobs.
8. one selected Skill fails and a batch transaction hides successful siblings
   or creates an ambiguous result.

## Candidate alternatives

- omit package replacement and create a new Asset for every correction;
- keep ZIP upload only and defer scratch/folder support;
- support public GitHub repositories only;
- create a dedicated per-user GitHub OAuth/PAT integration;
- clone repositories into a worker filesystem;
- reuse the existing GitHub connector credential and fetch only through its
  provider adapter;
- reject multi-Skill repositories and import one explicit path at a time.


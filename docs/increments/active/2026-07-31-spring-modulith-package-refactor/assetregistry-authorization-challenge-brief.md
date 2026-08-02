# Asset Registry Authorization Boundary Architecture Challenge

Date: 2026-08-02

Baseline: `ac00f8545d567e9592c9a5f8ff9358172360b9bf`

You are an independent, skeptical architecture reviewer. Attack the proposal
instead of validating it. Work read-only: do not edit files, create commits,
push, or write Northstar/memory. Read `CLAUDE.md`, `docs/conventions.md`, the
Asset Registry spec/test pair, and relevant decision filenames before issuing
a verdict. Verify every repository claim against the code.

## OrgMemory Promise

OrgMemory is a governed organizational memory layer for enterprise AI work.
Authorization is not metadata: an Asset is usable only when tenant, lifecycle,
canonical PostgreSQL state, and the OpenFGA projection agree. Unknown or
indeterminate authorization fails closed, and denied resources remain opaque.

## Decision Required

Choose one exact, production-grade ownership boundary and delivery order for
the first Asset Registry nested module. The result must split the current flat
`core.assetregistry` package without creating parent-to-child-to-parent cycles,
publishing JPA entities/repositories as API, changing authorization behavior,
or exceeding 100 changed files in a code PR.

The contested rule is:

> Role-assignment state and its transactional authorization outbox are one
> authorization responsibility. Asset lifecycle owns whether an Asset is
> authorization-ready, but must not expose the `Asset` entity or repository to
> the authorization module. Dependencies between nested modules must point one
> way and be represented by an owner-defined contract, not a shared repository.

## Repository Facts To Verify

- `core.assetregistry` has 108 directly declared Java files and no nested
  application module yet. The selected target slices in the active design are
  `kernel`, `authorization`, `prompt`, `skill`, `pack`, and `workinstruction`.
- `AssetRegistryCoordinator` creates `AssetRoleAssignment` and the matching
  `AssetAuthorizationOutbox` records in the same transaction for Asset creation
  and role assignment. It also derives ownership health from role history.
- `AssetAuthorizationCoordinator` claims/retries outbox batches, locks `Asset`
  through `AssetRepository`, and marks the Asset authorization-ready after the
  last unresolved record is applied.
- `AssetAuthorizationProjectionService` is the only OpenFGA tuple-write
  adapter in this flow. `AssetAuthorizationConvergenceService` is the public
  worker-facing reconciliation entry point.
- `AssetRegistryService` performs canonical permission checks against an
  `AssetAuthorizationTarget` derived from the Asset aggregate before invoking
  mutations.
- `AssetRole` is also an HTTP request contract today. Package moves therefore
  require consumer import updates in the same PR; Modulith visibility does not
  preserve Java FQNs.
- `docs/decisions/0003-postgresql-ledger-openfga-authorization.md` requires the
  OpenFGA projection to be fed by a transactional outbox and fail closed while
  tuple/canonical state has not converged.

Inspect at least:

- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryCoordinator.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryService.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/Asset.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRepository.java`
- every `AssetAuthorization*.java` and `AssetRole*.java` file in that package
- `core/src/test/java/com/orgmemory/core/assetregistry/AssetAuthorizationOutboxTests.java`
- `core/src/test/java/com/orgmemory/core/ModulithVerificationTests.java`
- `docs/specs/domains/asset-registry.md`
- `docs/tests/domains/asset-registry.md`
- `docs/decisions/0003-postgresql-ledger-openfga-authorization.md`
- decision filenames under `docs/decisions`

## Comparable-System Evidence

These are pinned local references. Read the cited sources rather than relying
on this summary.

| System | Pinned SHA | Observed boundary | Source evidence |
|---|---:|---|---|
| AgentRegistry | `d8d3f4e` | Authorization vocabulary/provider and per-request hooks are separate from generic resource persistence. List authorization supplies a parameterized filter before the store query; the store does not own policy. | `D:/OrgMemory/tmp/upstream-agentregistry/pkg/registry/auth/auth.go:13-46`, `pkg/registry/auth/authz.go:17-61`, `pkg/registry/resource/handler.go:132-167`, `pkg/registry/resource/handler.go:699-755`, `pkg/registry/v1alpha1store/store.go:217-237`, `pkg/registry/v1alpha1store/store.go:1077-1088` |
| Onyx | `618b503` | Authorization is a distinct access model/query capability, but document-set ownership filtering is deliberately co-located with document-set persistence operations. Retrieval composes user ACL filters before querying. This is evidence against pretending all authorization state can be detached from its aggregate at no cost. | `D:/OrgMemory/tmp/onyx/backend/onyx/access/models.py:160-225`, `backend/onyx/access/access.py:114-135`, `backend/onyx/context/search/preprocessing/access_filters.py:8-22`, `backend/onyx/db/document_access.py:26-83`, `backend/onyx/db/document_set.py:37-84`, `backend/onyx/db/document_set.py:253-326` |

Neither reference is copied as product truth. AgentRegistry supports a narrow
authorization port around persistence; Onyx shows that ownership filtering can
remain aggregate-local when it is part of the aggregate query invariant.

## Options Under Review

### A — Move Authorization First Behind A Kernel-Owned Port

Move role assignments, outbox persistence, convergence, projection, and role
vocabulary to `assetregistry.authorization`. Introduce a root/kernel-owned
contract that supplies a compact Asset authorization target and marks readiness
without exposing `Asset` or `AssetRepository`. Root orchestration creates role
and outbox state through an authorization-owned command API. Start the nested
module open only if compiler repair requires it, then close it in the same PR
when the exact public surface and dependency allowlist can be proven.

Risk: the root coordinator still needs role history to construct `AssetView`
and atomically create Asset + owner + outbox. A facade may become a cosmetic
wrapper over a distributed aggregate or split one transaction across owners.

### B — Move Kernel First, Then Authorization

First extract Asset identity/lifecycle and a narrow authorization-state port to
`assetregistry.kernel`; keep role/outbox/convergence in the parent temporarily.
Then move the complete authorization responsibility so it depends one way on
the kernel port. Root/profile orchestration calls both modules through public
contracts.

Risk: kernel is the widest dependency hub and may force a larger first PR or an
over-broad API. A temporary parent authorization implementation can still
depend on the nested kernel, while the second move must not create a reverse
kernel-to-authorization dependency.

### C — Keep Role/Outbox Persistence In Kernel; Move Projection Only

Treat role assignments and the transactional outbox as part of the Asset
aggregate. Move only claim/retry/OpenFGA projection and worker convergence to
`assetregistry.authorization`, consuming an owner-defined batch/complete/fail
port.

Risk: this produces a small clean adapter boundary, but `authorization` may be
only an integration subpackage while the parent/kernel remains crowded with
the authorization domain state the target design intended to extract.

You may reject all three only if you provide a fourth option with exact type
ownership, dependency direction, and a delivery sequence below 100 files.

## Operational And Security Cost Of A Wrong Boundary

- Splitting Asset creation, owner assignment, and outbox insertion across
  transactions can persist an Asset that can never converge or can lose its
  only owner relationship.
- Marking `authorizationReady` without the final locked unresolved-outbox check
  can expose an Asset before every required tuple is applied.
- Publishing repositories/entities as module API allows profile code to bypass
  tenant locks, retry leases, opaque absence, and audit invariants.
- A circular allowlist or `OPEN` module can make the build green while leaving
  the hundreds-file package structurally unchanged and future coupling
  unconstrained.
- Over-centralizing policy in the Asset aggregate can create a second allow
  policy beside OpenFGA, violating ADR 0003.

## Strongest Counterargument To The Proposed Rule

The role assignment and outbox rows are not an independent aggregate: they are
atomic appendages of Asset creation, role mutation, ownership-health views, and
the Asset readiness flag. Forcing them into an `authorization` module may
replace one cohesive transaction with chatty facades and DTO duplication.
Option C could be more honest: keep canonical state with Asset/kernel and treat
authorization as an outbound OpenFGA projection adapter, even if the resulting
module is smaller than the target design anticipated.

## Required Verdict

Return `ACCEPT`, `ACCEPT WITH CHANGES`, or `REJECT`, then commit to exactly one
option and state:

1. exact ownership of every `AssetAuthorization*`, `AssetRole*`, `Asset`,
   `AssetRepository`, `AssetAuthorizationTarget`, and readiness mutation;
2. exact dependency direction and public contract type set;
3. how Asset creation and role assignment remain atomic with outbox insertion;
4. whether the first nested module can close immediately, and its exact allowed
   dependencies;
5. PR boundaries that each remain below 100 changed files;
6. strongest counterargument, concrete must-fixes, rejected alternative, and
   structural/transaction/security tests required.

State plainly which facts in this brief are wrong or overstated. A vague
hybrid or “either A or B” is a failed review.

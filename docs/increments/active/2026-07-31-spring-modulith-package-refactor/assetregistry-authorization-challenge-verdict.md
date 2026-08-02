# Asset Registry Authorization Boundary Challenge Verdict

## Review Availability

Claude Fable 5 was requested through a fresh Orca-managed session. It could
not run because the account returned the exact failure:

> You've hit your monthly spend limit. Run /usage-credits to manage your limit
> and keep using Fable 5 or switch models to continue this chat.

The required fallback was a fresh Codex `gpt-5.6-sol` reviewer at `ultra`
reasoning, session `019fc107-dbdc-7402-b905-b677ee9ef67c`. The reviewer read
the repository guidance, domain spec/test matrix, relevant decisions, current
code, and pinned references. It remained read-only. A second adversarial pass
was resumed in the same session after the first verdict agreed too readily
with the proposed split.

Reviewed baseline: `ac00f8545d567e9592c9a5f8ff9358172360b9bf`.

## Binding Verdict

**ACCEPT WITH CHANGES — Option C.**

`assetregistry.kernel` owns the complete transactional cluster: Asset identity
and portfolio state, accountable roles, authorization intent/outbox leases,
and fail-closed readiness. `assetregistry.authorization` owns only the external
projection and convergence entry points. Draft, revision, review, release,
availability, catalog, delivery, and parent orchestration stay in the parent
Asset Registry module.

The initial Option B verdict is withdrawn. Its two-step readiness lock API did
not enforce lock ownership at runtime, its facade mixed incompatible database
and external-call transaction semantics, and moving availability or the broad
repository would introduce kernel-to-parent dependencies.

## Exact Ownership

- `assetregistry.kernel` owns package-private `Asset`, the narrowed identity
  repository, `AssetRoleAssignment`, authorization outbox/status/coordinator,
  and their repositories.
- Parent-owned `assetregistry::api` contains `AssetType`,
  `AssetPortfolioState`, `AssetRole`, the three Asset business exceptions, and
  the parent-facing immutable command/query contracts. Kernel implements those
  contracts so parent orchestration and unrelated top-level modules never
  import a nested module directly.
- Kernel publicly exposes only its narrow projection queue to the sibling
  authorization module. No public JPA entity, repository, lock handle, or
  readiness mutator exists.
- `assetregistry.authorization` exposes only
  `AssetAuthorizationProjectionService`,
  `AssetAuthorizationConvergenceService`, and
  `AssetAuthorizationConvergenceReport`.
- `AssetAvailability` and all draft/revision/review/release persistence remain
  in the parent. Catalog queries remain a parent-owned read model because they
  join identity with parent-owned release and availability tables.

## Runtime Rules

- Registration joins the parent transaction with `MANDATORY` and atomically
  writes Asset, OWNER assignment, and all initial authorization outbox rows.
- Role assignment joins the parent transaction and atomically writes the role
  plus its outbox row while holding the Asset lock.
- Projection queue completion owns one `REQUIRES_NEW` transaction that locks
  Asset and claimed rows, validates the persisted lease token, applies outbox
  and role state, flushes, counts unresolved rows, and only then marks
  readiness.
- Projection and convergence entry points use `NEVER`; OpenFGA is never called
  from an active database transaction.
- Skill draft replacement, submission, and direct publication serialize on the
  parent-owned draft row instead of exposing a kernel identity lock.

## Dependency Policy

```text
assistant ------------------> assetregistry::api
assetregistry parent --------> assetregistry::api
assetregistry parent --------> assetregistry.authorization
assetregistry.kernel --------> assetregistry::api, authorization, shared
assetregistry.authorization -> assetregistry.kernel, assetregistry::api,
                               authorization
```

Both nested modules close when introduced. Their exact allowlists are:

- `assetregistry.kernel`: `assetregistry::api`, `authorization`, `shared`
- `assetregistry.authorization`: `assetregistry.kernel`,
  `assetregistry::api`, `authorization`

`Type.OPEN`, reverse dependencies, and a public facade combining reads,
canonical writes, queue transactions, and OpenFGA calls are forbidden.

## Binding Delivery Sequence

1. Parent API vocabulary: move `AssetType`, `AssetPortfolioState`, `AssetRole`,
   and the three Asset exceptions to the exact parent-owned
   `assetregistry::api` named interface. Kernel does not exist yet. Maximum 70
   changed paths.
2. Canonical kernel ledger: introduce and immediately close Kernel; move Asset,
   the narrowed identity repository, role and outbox state, coordinator, and
   projection queue; add parent-facing contracts to `assetregistry::api`;
   extract the parent catalog read model and use draft locking. Maximum 60
   changed paths.
3. Projection module: move projection and convergence entry points, enforce
   `NEVER`, close the module, and update worker wiring. Maximum 20 changed paths.

Every PR contains code, remains below both its slice cap and the repository
100-file ceiling, and may not weaken closure or expose persistence types.

## Executable Topology Correction — 2026-08-02

The full `modules.verify()` gate rejected the planned intermediate parent to
Kernel projection dependency as a real cycle: Kernel consumes the parent-owned
`assetregistry::api`, so a parent import of Kernel or Authorization cannot be
retained. The strongest alternative was to keep PR 2 and PR 3 separate by
leaving the outbox coordinator in the parent temporarily. That would contradict
the selected atomic ownership boundary and make Kernel incomplete on arrival.

The binding implementation therefore combines delivery steps 2 and 3 while
retaining their total 60-path ceiling. Parent orchestration depends only on the
new `AssetAuthorizationProjectionCommand` in `assetregistry::api`;
package-private Authorization projection implements it. Authorization publicly
exposes only convergence service/report for Worker, while its exact dependency
allowlist remains Kernel, `assetregistry::api`, and Authorization. This
supersedes the parent-to-Authorization arrow and the public projection-service
statement above; all aggregate ownership, transaction rules, and rejected
alternatives remain unchanged.

## Required Evidence

- Characterization tests fail first for module existence/closure, exact public
  API, exact allowlists, forbidden reverse edges, and transaction propagation.
- Registration, role assignment, completion, stale/replayed/cross-tenant batch,
  readiness, projection failure, and concurrent draft flows retain current
  atomic and fail-closed behavior.
- A fake tuple writer proves no Spring transaction is active during the
  external call.
- `modules.verify()`, focused Core/API/Worker tests, static analysis, docs and
  release-policy gates, and a terminating clean repository test pass.

## Comparable Source Evidence

- AgentRegistry `d8d3f4e`: authorization vocabulary and hooks are separated
  from generic persistence, and list authorization filtering is injected before
  store queries (`pkg/registry/auth/auth.go`, `pkg/registry/auth/authz.go`,
  `pkg/registry/resource/handler.go`, `pkg/registry/v1alpha1store/store.go`).
- Onyx `618b503`: the access model/query layer is separated, while ownership
  filtering and mutation remain beside document-set persistence where the
  consistency boundary requires it (`backend/onyx/access/models.py`,
  `backend/onyx/access/access.py`,
  `backend/onyx/context/search/preprocessing/access_filters.py`,
  `backend/onyx/db/document_access.py`, `backend/onyx/db/document_set.py`).

These references support separating the external policy edge, but not breaking
OrgMemory's atomic Asset/role/outbox/readiness cluster merely for package
symmetry.

## Executable Vocabulary Amendment

The first attempted implementation put the six public types in a closed
Kernel. Its focused closure and public-surface tests passed, but the full
`ApplicationModules.verify()` gate rejected
`assistant -> assetregistry.kernel` through `AssetType` as an invalid
sub-module reference. The reviewer therefore amended only the vocabulary and
contract placement: the six types live in the ordinary
`com.orgmemory.core.assetregistry.api` package annotated solely with
`@NamedInterface("api")`; Kernel begins with the actual ledger in PR 2.

This preserves the transaction-cluster decision while following the same
parent-interface pattern as `knowledge::catalog` and `knowledge::search`. The
exact six-type named-interface test prevents `assetregistry::api` from becoming
a dumping ground. See
[assetregistry-kernel-vocabulary-challenge-amendment.md](assetregistry-kernel-vocabulary-challenge-amendment.md)
for the failing evidence and the binding correction request.

The rejected fixes were leaving the types in the broad root package, opening
Kernel, declaring an unenforceable Assistant-to-Kernel dependency, duplicating
enums through a translation facade, or creating a new top-level module solely
for vocabulary.

## Rejected Alternative

Option B placed role/outbox state in authorization and attempted to expose
Asset locking/readiness through kernel contracts. It is rejected because no
two-call contract proves the same transaction still owns the lock, a callback
creates reciprocal orchestration, a conditional kernel update creates a
reverse persistence dependency, and asynchronous readiness loses the atomic
final unresolved-row check.

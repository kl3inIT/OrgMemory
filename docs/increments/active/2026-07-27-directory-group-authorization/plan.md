# Plan

## PR A1 — Typed Mapping Policy And Impact Preview

Scope:

- add mapping and preview persistence with organization-composite ownership;
- expose server-derived target/resource pickers and allowlisted relations;
- enforce organization `can_manage_members` and target-specific
  `can_manage_acl` before preview;
- implement a version-bound impact preview and approval digest;
- detect self-lockout, last recovery administrator, conflicting manual grants,
  tombstoned users/groups, and stale membership versions;
- add immutable audit records for create/update/preview attempts;
- permit only `DRAFT` and `PREVIEWED`; no approval/activation endpoint exists
  until A2's durable executor is ready.

Merge gate:

- [ ] an API caller cannot submit arbitrary OpenFGA types, relations, or foreign
  resource IDs;
- [ ] a member administrator without target ACL authority cannot map a
  Knowledge Space;
- [ ] group rename has no effect on mapping identity;
- [ ] changed membership or target invalidates the preview digest;
- [ ] Source Groups and departments cannot be selected;
- [ ] preview reports gain/loss/retain and fail-closed users deterministically;
- [ ] no mapping can become approved/active and no OpenFGA tuple changes.

## PR A2 — Owned Outbox And OpenFGA Convergence

Scope:

- add group-projection and mapping-grant ownership plus versioned outbox tables;
- add per-user access blockers and readiness aggregation;
- publish mapping and relevant membership desired-state events transactionally;
- add the worker lease/retry/convergence scheduler;
- write only allowlisted group-member and target grant tuples;
- add a provisioning-specific tuple scope instead of broadening interactive
  administration scope;
- reserve the Directory Group object-ID namespace behind a PostgreSQL ownership
  arbiter;
- store desired/applied authorization model IDs and verify readback before
  convergence;
- add the version-bound approve/activate transition only after outbox and worker
  wiring are available;
- implement fail-closed readiness ordering for reductions and reactivation;
- coordinate User/Group tombstone and Groups-capability disable with revocation;
- add drift detection, ownership-safe repair, and metrics;
- extend OpenFGA model tests without changing Source ACL semantics;
- add the repository-required OpenFGA model validation command to CI before
  model tests.

Failure matrix:

- OpenFGA unavailable before and after PostgreSQL commit;
- duplicate worker delivery;
- worker crash after tuple write but before acknowledgement;
- membership remove followed by immediate re-add;
- mapping target A to B with out-of-order retries;
- group or user tombstone during convergence;
- connection suspension during a batch;
- manual tuple identical to or adjacent to a managed tuple;
- a concurrent identical manual/managed write, not only a pre-existing tuple;
- stale model ID and authorization model repair;
- binary rollback with pending revocations.

Merge gate:

- [ ] every failure converges to the newest desired version;
- [ ] a stale retry cannot resurrect a removed membership or grant;
- [ ] reducing changes deny before OpenFGA cleanup;
- [ ] reactivation waits for required tuple verification;
- [ ] resolving one blocker cannot reactivate a user with another pending
  revocation;
- [ ] increasing changes grant only after verified convergence;
- [ ] repair touches only tuples proven by the managed ledger;
- [ ] removing one of multiple mappings retains the shared membership
  projection required by the others;
- [ ] an exact pre-existing unmanaged tuple is never claimed for deletion;
- [ ] a concurrent identical unmanaged/managed write is resolved by the
  ownership arbiter without deleting the unmanaged grant later;
- [ ] convergence records and verifies the intended authorization model ID;
- [ ] user/group tombstone and Groups disable revoke mapped access fail-closed;
- [ ] connection `SUSPENDED` blocks ingress but does not stop queued security
  revocations;
- [ ] manual tuples and sealed Source ACL data remain unchanged;
- [ ] worker restart, lease expiry, and retry tests pass on PostgreSQL/OpenFGA.
- [ ] OpenFGA model validation and model tests pass in CI.

## PR A3 — Mapping Administration, Explanation, And Recovery Proof

Scope:

- add mapping list/create/preview/approve/suspend/delete views;
- use resource pickers and resolved names rather than pasted UUIDs;
- show affected users, pending convergence, last error, retry age, and owned
  assignments;
- extend permission explanation with Directory Group and mapping provenance;
- add permission-audit events for role/mapping mutations;
- add browser and two-user authorization tests;
- rehearse rollback and drift repair.

Live proof:

1. prove two Directory Group members initially receive no access;
2. preview and approve one Knowledge Space mapping;
3. observe durable convergence and the full permission explanation;
4. remove one member and prove immediate fail-closed denial before tuple cleanup;
5. recover OpenFGA and prove the user returns only to remaining valid access;
6. rename the group and prove the mapping remains stable;
7. change the target and inject an out-of-order retry;
8. prove the old target is not resurrected;
9. remove the mapping and prove only directory-owned tuples disappear;
10. prove a manual grant and sealed Source ACL rows are untouched;
11. roll back the binary and complete reconciliation from the retained ledger.

Increment exit:

- [ ] A1, A2, and A3 are merged in order.
- [ ] Two-tenant negative, self-lockout, concurrency, outage, stale retry, and
  ownership-safe rollback evidence pass.
- [ ] A Directory Group still has no effect without an explicit active mapping.
- [ ] Source ACL remains the final visibility ceiling.

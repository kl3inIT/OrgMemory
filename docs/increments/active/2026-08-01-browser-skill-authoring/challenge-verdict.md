# Independent architecture challenge

Date: 2026-08-01
Reviewer: Fable 5, maximum effort, read-only
Verdict: `ACCEPT_WITH_MUST_FIX`

## Evidence that changed the design

- The database trigger created in
  `V2__asset_registry_foundation.sql` rejects both update and delete of every
  `asset_payload_references` row. The JPA mapping is also non-updatable.
  Replacing a Draft package therefore requires a migration that permits only
  deletion of a `DRAFT` reference; it must never permit reference updates or
  deletion of `REVISION` or `RELEASE` references.
- `AssetRepository.findForUpdate` and `BaseEntity.version` already provide the
  pessimistic asset lock and optimistic client precondition needed to serialize
  replacement against submit and publish.
- The storage adapter always writes a fresh UUID object and verifies its digest.
  Existing import code deletes the staged object when database creation fails.
- Current inspection bounds are 20 MiB compressed, 50 MiB unpacked, 300 files,
  and 512 KiB for `SKILL.md`, with traversal, symlink, collision, encoding, and
  manifest validation. Scratch, upload, and replacement must converge on this
  validator instead of growing separate rules.
- GitHub connector credentials are organization-scoped, encrypted at rest,
  resolved through one egress port, and never returned by the admin API. Content
  and archive fetching is new work and must preserve that boundary.

## Required Draft replacement boundary

1. Outside a transaction, inspect the candidate package and store it under a
   fresh key `K2`; the adapter verifies the digest.
2. In one `REQUIRES_NEW` transaction, lock the Asset, require `SKILL`, compare
   the expected Draft lock version, delete the old `DRAFT` reference `K1`, insert
   the new `DRAFT` reference `K2`, update Draft metadata, write the audit event,
   and add a durable supersession record for `K1`.
3. If the transaction fails, compensate by deleting `K2`.
4. After commit, prove that no row in the organization references `K1`, delete
   `K1`, and remove the supersession record. A cleanup failure retains the
   durable record for bounded retry and never rolls back the valid Draft swap.

Cleanup proof is by organization plus exact `referenceValue`, not content
digest. Submit and publish remain safe because they copy the current Draft
reference while holding the same Asset lock. A crash between object creation
and the database transaction can still leave an untracked staged object; that
existing storage-reconciliation risk must remain observable.

## Required GitHub boundary

- Public import performs anonymous server-side requests only.
- Private import resolves an organization-scoped, administrator-managed GitHub
  App connection. The browser may send a connection key but never a PAT,
  private key, installation token, signed archive URL, or Authorization value.
- Allow only HTTPS to `api.github.com` and `codeload.github.com`. Disable generic
  redirects. At most one explicitly validated archive redirect may cross from
  the API host to the codeload host, and Authorization must be stripped.
- Private repositories require an explicit per-connection admin opt-in, must be
  within the GitHub App installation selection, and must emit a credential-use
  audit record.
- Preview resolves the requested revision to a 40-character commit SHA. Import
  accepts only that SHA plus the selected path and records repository, SHA, and
  path as server-derived provenance.
- Archive retrieval has its own compressed, expanded, file-count, and discovered
  Skill limits; discovered Skills are capped at 20.
- Multi-import is one independent `importPackage` transaction per selected
  Skill, with per-item results. There is no outer batch transaction.

## Must-fix by delivery slice

### PR 1

- Record this verdict before opening the PR.
- Reconcile the Asset Registry specification and test matrix with the new menu
  and real browser ZIP import.

### PR 2

- Add the Draft-only reference-delete migration and prove immutable references
  still reject update and delete.
- Implement lock-versioned replacement, durable supersession cleanup, and an
  exact-reference proof query.
- Keep preview stateless and re-run the canonical validator at create and
  replace time.

### PR 3

- Enforce the credential, host, redirect, revision-pinning, provenance, archive
  bounds, and per-item transaction rules above.

## Strongest rejected alternative

Creating a new Asset for every correction avoids relaxing the append-only
reference trigger. It was rejected because Skill identity and namespace/slug
uniqueness would force renaming or abandoning the existing Asset, while Draft
is the intentionally mutable stage. The accepted design narrows mutation to
deleting only the Draft-owned pointer; immutable Revision and Release pointers
remain append-only and independently pin their bytes.

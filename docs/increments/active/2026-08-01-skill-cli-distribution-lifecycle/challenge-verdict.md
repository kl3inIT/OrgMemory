# Independent architecture verdict

Date: 2026-08-01
Reviewer: fresh Codex `gpt-5.6-sol ultra` session through Orca CLI
Result: accept with changes

The first reviewer session was interrupted when Orca upgraded itself from
1.4.159 to 1.4.163. A fresh read-only session repeated the repository and
pinned-reference audit and produced the final verdict below.

## 1. Verdict

Accept with changes. Independent CLI versioning, exact Skill references, npm
Trusted Publishing, receipt-v2 verification, and registry-before-UI activation
are sound. Implementation must first make target ownership, cross-process
serialization, crash recovery, and destructive-removal semantics explicit.

## 2. Strongest counterargument

Publishing through `npx --yes` expands a local consistency bug into a public
supply-chain and destructive-filesystem surface. Provenance identifies where a
package came from; it does not prove reviewed behavior is safe. Until local
installation is collision-free and crash-recoverable, a repository checkout is
less convenient but has a smaller blast radius.

## 3. Must-fix list

1. **Critical — cross-namespace same-slug collision.** Distinct coordinates
   currently share the same consumer target. Enforce one receipt owner per
   canonical target and refuse a collision. Do not assume consumers discover a
   namespace-scoped directory.
2. **Critical — concurrent receipt lost update.** Atomic replacement does not
   serialize a read-modify-write. Hold an exclusive cross-process lock for the
   complete lifecycle transaction in one project/global scope.
3. **Critical — interrupted tree/receipt transaction.** A process can terminate
   after tree promotion but before receipt commit. Persist an operation journal
   and recover it before later lifecycle commands.
4. **High — modified and legacy deletion.** Do not expose forceful recursive
   deletion. Remove only verified schema-v2 installations; legacy or modified
   trees require manual cleanup or a verified reinstall first.
5. **High — verification completeness and path identity.** Compare the complete
   regular-file tree. Extra entries, links, non-regular files, digest/size
   changes, missing files, and target mismatch must not verify.
6. **High — npm bootstrap or publication compromise.** Prove control of the
   `@orgmemory` npm organization, inspect the exact tarball, approve a protected
   workflow environment, require the exact green `main` SHA, verify published
   provenance/digest, and revoke bootstrap access immediately.
7. **Medium — CLI version drift.** Derive runtime version from package metadata
   rather than hardcoding it separately; the UI may only pin a registry-verified
   package version.

## 4. Repository evidence

- `apps/cli/src/install.ts` derives the target from `manifest.slug` but keys the
  receipt by agent plus the full coordinate, proving the collision.
- `apps/cli/src/install.ts` performs an unlocked receipt read-modify-write;
  `apps/cli/src/shared.ts` atomically replaces JSON but provides no mutual
  exclusion.
- Installer rollback exists only while the process remains alive; no durable
  journal or startup recovery exists.
- Receipt schema v1 stores no file manifest, so `skill list` cannot prove local
  integrity.
- Promotion replaces any existing target without receipt ownership or drift
  checks.
- `apps/cli/package.json` is private while `apps/cli/src/index.ts` hardcodes
  version `0.1.0`.
- The web handoff emits bare `orgmemory`, although no public npm package exists.
- Product releases and npm package releases have different compatibility
  cadences; the current release workflow contains no npm publication.

## 5. Reference evidence

| Reference | Valid lesson | Invalid comparison |
| --- | --- | --- |
| Vercel Skills | Deterministic folder hashing and explicit update metadata. | Its receipt write is unlocked and removal does not establish OrgMemory-grade safe deletion. |
| ClawHub | Complete-tree fingerprinting, owner-aware targets, collision detection, staged replacement. | Its lock write and tree/lock transaction do not solve crash recovery; its public marketplace trust model is out of scope. |
| Onyx | Built-in, shared, personal, and enabled Skills can coexist in one product surface. | Onyx owns the runtime and does not validate external local installations. |
| AgentRegistry | Registry, CLI, configuration, and runtime are separate responsibilities. | Its immutable Agent/Skill versioning remains incomplete and cannot justify mutable `latest`. |
| 9Router | A copyable `npx` command removes global-install friction. | Its command is unpinned and launches one public application, not an authorized immutable Skill lifecycle. |

## 6. Recommended final contract

- **Versioning:** version `@orgmemory/cli` independently with SemVer from one
  package-owned source. `release/product.json` never determines CLI version.
- **Receipts:** schema v2 stores agent, exact coordinate/version,
  release/package identities, canonical relative target, and every regular
  file's path, size, and SHA-256. It stores no token, URL, content, or storage
  key. Receipt keys and canonical targets are bijective. Schema v1 is readable
  but unverifiable.
- **Verify:** entirely offline, returning `verified`, `modified`, `missing`, or
  `unverifiable`. Only an exact regular-file set at the recomputed target is
  verified.
- **Update:** same coordinate, exact `--to <version>`, never `latest`.
  Re-authorize the destination release and refuse modified or unverifiable
  source state. Hold the scope lock through validation, staging, journaling,
  promotion, receipt commit, and cleanup.
- **Remove:** only a verified v2 installation. Rename to quarantine, commit
  receipt removal, then delete quarantine. Recover or roll back through the
  journal. There is no recursive `--force`.
- **Publishing:** dedicated manually approved workflow, exact current green
  `main` SHA, Node 24, npm 11.5.1+, OIDC Trusted Publishing, provenance, public
  access, no dependency cache or long-lived token, serialized publication,
  inspected tarball, and post-publish registry verification.
- **UI activation:** render a pinned command only after that exact CLI version
  is verified in the registry. Initial activation is therefore a second PR
  unless the version is already live before the first PR merges.

## 7. Rejected alternative

Reject coupling the CLI to `release/product.json` or publishing it from every
product GitHub Release. That conflates release cadences, expands the writable
whole-product workflow, publishes when the CLI has not changed, and leaves
local lifecycle hazards unsolved. Also reject the original `remove --force`
proposal and the assumption that atomic receipt replacement alone makes the
tree-plus-receipt operation recoverable.

## 8. Scope limits

- no mutable `latest`, automatic update, background mutation, or
  cross-coordinate update;
- no consumer beyond Claude Code and Codex;
- no claim that installation proves agent discovery, interpretation, or
  execution safety;
- no Skill execution, marketplace, rating, usage telemetry, or runtime
  certification;
- no secret or customer content in receipts;
- no MCP delivery to Claude web/Desktop;
- no signing system beyond npm provenance and existing exact Skill release and
  package digests;
- no promise that revoked server authorization retroactively removes an
  offline local copy.

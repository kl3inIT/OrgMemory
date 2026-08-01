# Skill CLI distribution and local lifecycle plan

## 0. Decision gate

- [x] Audit current CLI install, receipt, UI handoff, release workflow, specs,
  tests, and package availability.
- [x] Study pinned Vercel Skills, ClawHub, Onyx, AgentRegistry, and 9Router
  sources.
- [x] Run a fresh Codex `gpt-5.6-sol ultra` adversarial challenge because the
  configured Fable reviewer is quota-blocked.
- [x] Record the verdict, final contract, rejected alternative, and first
  publication bootstrap boundary.

## 1. Tests first and CLI lifecycle

- [x] Characterize schema-v1 receipts and current atomic installation.
- [x] Add failing tests for schema-v2 verification outcomes and path safety.
- [x] Add failing tests for exact same-coordinate update and verified-only
  removal.
- [x] Implement receipt-v2 read/write compatibility, `skill verify`,
  `skill update`, and `skill remove`.
- [x] Add one-owner target collision checks, a per-scope mutation lock, and
  durable journal recovery.
- [x] Preserve exact-version, authorization, package-integrity, rollback, and
  token-free receipt guarantees.

## 2. npm distribution

- [x] Make `@orgmemory/cli` a minimal public package and verify `npm pack`.
- [x] Add a dedicated Node 24 npm Trusted Publishing workflow with OIDC,
  provenance, no cache, exact-main validation, and an approval environment.
- [x] Add workflow-policy tests and a first-publication bootstrap runbook.
- [x] Correct the bootstrap for npm 11 pre-publication `npm trust github`, bind
  provenance to the monorepo package directory, and execute the packed CLI
  before publication.
- [x] Publish and verify `@orgmemory/cli@0.1.0` with registry integrity, SLSA
  provenance, exact-version execution, and a retry-safe post-publish gate.

## 3. Product handoff and documentation

- [ ] After registry proof, replace bare CLI handoffs with an exact pinned
  `npx --yes @orgmemory/cli@<version>` command.
- [ ] Update focused web unit/component/browser tests.
- [x] Reconcile `ARCHITECTURE.md`, the Asset Registry spec and mirrored test
  matrix, CLI README, and bilingual public Product Guide.
- [x] Add one product release entry.

## 4. Verification

- [x] Run Node 24 CLI typecheck, focused tests, full tests, build, and packed
  tarball smoke test in a clean temporary directory.
- [x] Run Node 24 web lint, typecheck, focused tests, full tests, production
  build, and browser verification for the changed handoff.
- [x] Run docs lint/typecheck/build and link/content gates.
- [x] Run release/workflow-policy checks and confirm no Java or Gradle source
  changed.
- [x] Confirm `git diff --check` and review the final changed-file count.

## 5. Delivery

- [ ] Merge current `origin/main` before opening each PR.
- [ ] Open conventional PR(s), each below 100 changed files and with a release
  entry unless exempted by repository policy.
- [ ] Resolve all actionable CodeRabbit findings and pass every required check.
- [ ] Merge with merge commits; never squash or rebase the reviewed commits.
- [ ] Verify post-merge CI, package publication, automatic app/docs deployment,
  and the live pinned handoff.
- [ ] Archive the increment, update the roadmap, and record the durable
  checkpoint in Northstar without secrets.

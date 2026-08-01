# Durable cross-store publication lifecycle plan

## 0. Decision gate

- [x] Re-audit the current publication contract, worker call order,
  PostgreSQL/OpenSearch implementations, conformance tests, prior decisions,
  and deferred review finding.
- [x] Inspect pinned LightRAG v1.5.4 flush and failure semantics without
  treating its process-local callback as a durable publication authority.
- [x] Run independent Codex `gpt-5.6-sol ultra` adversarial review and record
  its binding must-fix list, final contract, and rejected alternatives.

## 1. Characterization tests before production code

- [x] Characterize current deterministic visibility, exact idempotent replay,
  partial-preparation cleanup, PostgreSQL transaction behavior, and OpenSearch
  committing/abort boundary on unchanged production code.
- [x] Run the focused PostgreSQL and OpenSearch characterization gates.
- [x] Commit characterization tests separately before editing production code
  (`18deef93`).

## 2. Durable lifecycle and recovery

- [x] Add the challenged command/outcome lifecycle, logical operation identity,
  predecessor-bound attempts, claim epoch, exact commit permit, publication
  proof, and store-issued discard permit.
- [x] Make the in-memory and PostgreSQL stores resume exact partial batches
  without weakening identity conflicts or atomic publication.
- [x] Make OpenSearch reconcile every safe `COMMITTING` observation and fail
  closed on contradictory or foreign heads.
- [x] Preserve remove-before-unlock style safety: never discard staging that a
  visible, historical, or not-yet-classified head may reference.
- [x] Fence copy-forward staging runs by claim epoch so live runs are never
  stolen and abandoned lower-epoch writes cannot affect the selected run.
- [x] Keep graph-job ownership/manifest checks, cache invalidation, and durable
  completion in the challenged order.

## 3. Verification

- [x] Add shared conformance recovery tests plus adapter restart/crash-window
  tests and worker fencing/identity tests.
- [x] Run the narrowest useful Gradle module tests after each logical step.
- [x] Run the available backend static gate: IDE semantic inspection was not
  available in this terminal, so compilation plus the full clean test supplied
  the terminating static/runtime verification.
- [x] Run a terminating `gradlew clean test` from a clean integrated head.
- [x] Confirm `git diff --check`, no secret/customer data, and a clean worktree.

## 4. Consolidation and delivery

- [x] Record the durable lifecycle decision and rejected alternatives.
- [x] Reconcile `ARCHITECTURE.md`, Secure GraphRAG spec and mirrored test
  matrix, including refreshed `Source:` and `Reconciled:` lines.
- [x] Move this increment to `completed/`, mark the roadmap entry shipped, and
  retain active-plan history only as immutable evidence.
- [ ] Commit logical steps without squashing, merge current `origin/main`, and
  open a conventional PR below the repository reviewability ceiling.
- [ ] Resolve all actionable review findings and pass every required CI check.
- [ ] Merge with a merge commit, verify post-merge CI/deployment impact, and
  update Northstar with evidence and remaining risks.

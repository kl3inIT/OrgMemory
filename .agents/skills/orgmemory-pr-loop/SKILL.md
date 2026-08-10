---
name: orgmemory-pr-loop
description: Drive one OrgMemory change through branch, verification, UI approval, PR, CI, review, merge, exact-SHA deployment proof, and checkout cleanup ("the loop"). Use when the user says "loop", "theo loop", asks to open or merge a PR, or hands over an approved PR series. Do not start an unapproved next PR or use this as permission to expand scope.
---

# OrgMemory PR Loop

One approved scope at a time: branch, implement, verify, preview, open, review,
merge, prove the released SHA, and clean up. Only then start the next scope.
The repository and live runtime are the evidence; chat summaries are not.

## Trigger Conditions

- The user says "loop", "theo loop", "mở PR", or "merge khi xanh".
- An approved OrgMemory scope should be carried through the complete delivery
  lifecycle rather than stopped after local implementation.
- A standing directive covers a named PR series.

Finishing PR N is not permission to start PR N+1 unless the user explicitly
approved the series. Scope control is part of the loop.

## Required Context

- Read `AGENTS.md`, the active increment when one exists, and the affected
  domain spec/test pair before implementation.
- Select one checkout mode and record it:
  - **Single-session mode:** create a new branch in the current checkout from
    the current `origin/main`. Do not create an extra worktree. Preserve
    unrelated dirty/untracked paths and return this checkout to updated
    `main` after the iteration.
  - **Concurrent-session mode:** use a dedicated worktree from `origin/main`
    so sessions cannot mutate each other's checkout.
- `gh` must be authenticated. Prefer `gh --jq` so commands work on both POSIX
  and Windows; do not assume standalone `jq`, PowerShell, or a drive path.
- Follow commit and verification rules in `docs/conventions.md` and
  `docs/guidelines/testing-harness.md`.
- Preserve reviewed ancestry. Use GitHub merge commits; squash and rebase
  merges are prohibited for this project.

## Procedure

### 1. Preflight and scope

1. Run `git status --short --branch`, `git fetch origin`, and compare the
   current `HEAD` with `origin/main`.
2. Inventory dirty paths. Stop for unknown or overlapping changes. Explicitly
   identified unrelated files may remain untouched; do not stage, move, reset,
   or delete them.
3. State the exact PR scope and expected evidence. Obtain confirmation unless
   an existing user directive or active plan already fixes the scope.
4. Create or select the feature branch using the recorded checkout mode. The
   branch must start from current `origin/main` before production edits begin.

**Completion:** branch, base SHA, checkout mode, approved paths, excluded dirty
paths, and acceptance evidence are known.

### 2. Implement and verify locally

1. Use focused tests while iterating, then run the diff-derived completion
   gates from the testing harness.
2. For changed backend Java, run the repository's backend inspection gate. For
   frontend changes, run typecheck, unit tests, production build, and browser
   verification when the flow matters.
3. Add one `.tegami/*.md` entry for user, administrator, operator, API,
   compatibility, or security impact. For a genuinely internal-only change,
   record `skip-release` and its reason in the PR body. Run
   `corepack pnpm release:check` for product or operator changes.
4. Review `git status --short`, `git diff --stat`, `git diff`,
   `git diff --cached`, `git diff --check`, and `git diff --cached --check`.
   Do not use `origin/main...HEAD` as the only pre-commit scope view because it
   omits working-tree and index changes. Account for every path and scan for
   credentials, tokens, cookies, customer data, generated junk, and artifacts.
5. Consolidate durable behavior into the correct spec/test/guideline/decision
   homes before calling the branch ready.

**Completion:** all intended behavior has evidence, all changed paths are in
scope, and all required local gates are green.

### 3. User-visible approval gate

For UI/UX work, produce the real review surface before merge:

1. Run the actual application/provider artifact, not a detached mockup.
2. Capture sanitized desktop/mobile evidence and relevant states.
3. For authentication UI, show the live login flow with real Keycloak form
   actions and local theme assets; do not expose credentials, cookies, reset
   links, authorization codes, or tokens.
4. Stage exactly the verified candidate paths, rerun staged-diff checks, and
   record the immutable candidate tree with `git write-tree`. Record the
   approved UI path set and hashes of the sanitized screenshots. This does not
   create a commit and preserves a pre-commit approval gate.
5. Report the candidate tree, implementation, known limitations, and
   verification results to the user. Wait for explicit approval before commit
   or merge. Feedback returns to step 2 and requires a new tree and evidence.

**Completion:** the user explicitly approved evidence bound to an immutable
candidate tree and UI path set, or the gate is recorded as not applicable.

### 4. Synchronize, commit, and publish the branch

1. Create the conventional implementation commit directly from the approved
   staged candidate and verify `git rev-parse HEAD^{tree}` equals the approved
   candidate tree before changing the index or ancestry.
2. Fetch again and merge current `origin/main` into the feature branch with
   `git merge origin/main --no-edit`. Resolve conflicts on the branch; never
   merge the feature branch into local `main` by hand.
3. Re-run the narrowest gates affected by the sync. Compare the approved tree
   with `HEAD` for every approved UI path. Any difference invalidates UI
   approval and returns to step 3; unrelated base changes do not.
4. Push the branch and verify the remote head SHA equals local `HEAD`.
5. Open the PR with a conventional `type(scope): subject` title and an evidence
   summary that includes scope, tests, the approved candidate tree/UI paths,
   approval, risks, and any internal-only `skip-release` rationale.

**Completion:** the PR points to the intended remote SHA and contains no
unpublished local change.

### 5. CI and review convergence

Set deterministic bounds before the first watch: at most three review/fix rounds,
at most two latest-head CI reruns that do not include a code fix, and at most 60
polls per head. Count every pushed fix as one review/fix round even when it
addresses several findings. If any bound is exhausted, or two consecutive rounds
make no measurable progress, stop without merging and escalate the remaining
state to the user.

1. Watch checks with a hard wall-clock bound: `timeout --signal=TERM --kill-after=10s 600s gh pr checks <n> --watch --interval 10` or `timeout --signal=TERM --kill-after=10s 600s gh run watch <id> --exit-status --interval 10`. Treat exit 124 as poll-budget exhaustion and escalate; never rerun the same watch automatically. Never report done while a required check is pending.
2. Inspect both review summaries and inline comments:
   `gh pr view <n> --json headRefOid,mergeable,reviewDecision,statusCheckRollup,reviews,comments`
   and `gh api repos/<owner>/OrgMemory/pulls/<n>/comments --paginate`.
3. Address every actionable finding with a fix commit or reasoned reply. After
   pushing, wait for checks against the new `headRefOid`; old green runs do not
   prove the new head.
4. Track repeated findings. If the same concern returns twice without new
   evidence, conflicts with a repository contract, or requires scope expansion,
   stop and surface the disagreement instead of looping indefinitely.
5. If CodeRabbit is rate-limited or silent after CI is green, the documented
   green-CI fallback applies; record that condition.

**Completion:** latest-head required checks are green and every actionable
review finding is fixed, answered, or explicitly escalated.

### 6. Final freshness and merge gate

1. Fetch `origin/main` immediately before merge. If main advanced, merge it into
   the branch, rerun affected gates, push, and repeat CI/review convergence for
   the new head.
2. Verify the PR is mergeable, required checks belong to the current head, UI
   approval still corresponds to that head by proving the approved UI paths are
   unchanged from the approved tree, and no unresolved finding remains.
3. Ask for explicit merge approval when the user has reserved merging or when
   the change affects login/authentication UI. Do not infer approval from green
   CI alone.
4. Record the immutable reviewed head and merge with an exact-head guard. In a
   POSIX shell:
   `reviewed_head_sha="$(gh pr view <n> --json headRefOid --jq .headRefOid)"`
   followed by
   `gh pr merge <n> --merge --delete-branch --match-head-commit "$reviewed_head_sha"`.
   In PowerShell:
   `$reviewedHeadSha = gh pr view <n> --json headRefOid --jq .headRefOid`
   followed by
   `gh pr merge <n> --merge --delete-branch --match-head-commit $reviewedHeadSha`.
   Never use `--squash`, `--rebase`, force-push, or history reconstruction.

**Completion:** GitHub reports the PR merged with merge commit `M`, and every
reviewed branch commit is an ancestor of `M`.

### 7. Exact-SHA release verification

When deployment applies:

1. Fetch and prove merge commit `M` is on `origin/main`.
2. Wait for main CI and immutable image workflows for `M`, not an adjacent SHA.
3. Verify the deployed image/revision is derived from `M` through the normal
   release path.
4. Run the applicable production smoke from
   `docs/runbooks/production-zm-deployment.md`. For login UI, use a fresh
   browser session and verify theme assets, form behavior, redirect/callback,
   mobile layout, and no external or failed assets.
5. Redact all secrets and authentication artifacts from logs and screenshots.

If the change does not deploy, record why exact-SHA runtime proof is not
applicable. A local screenshot alone never proves a deployed fix.

**Completion:** exact merged SHA, exact deployed artifact, and live behavior are
linked by evidence, or deployment is explicitly not applicable.

### 8. Return the checkout and stop

Only after post-merge verification:

1. In single-session mode, switch this checkout to `main`, fast-forward it to
   `origin/main`, and remove the merged local feature branch. Preserve unrelated
   untracked files.
2. In concurrent-session mode, update the main checkout and remove the completed
   worktree/branch after verifying no uncommitted evidence remains.
3. Report merge commit, checks, review resolution, deployment proof, cleanup,
   and the next candidate scope.
4. Wait. Start the next scope only when an explicit standing directive covers
   it or the user says to continue.

## Failure Handling

- **Red CI:** inspect `gh run view <id> --log-failed`; fix the cause, push, and
  resume against the new head. Never merge around a red required check.
- **Conflict:** merge fresh `origin/main` into the feature branch and resolve
  there. Expect roadmap conflicts; never overwrite concurrent intent blindly.
- **Review stalls:** establish whether the reviewer is pending, rate-limited, or
  repeating a resolved concern. Apply only the documented fallback.
- **Preview rejected:** keep the branch, incorporate feedback, rerun relevant
  tests, and present refreshed UI evidence. Do not open or merge a replacement
  PR behind the user's review.
- **Deploy fails after merge:** do not clean the checkout yet. Follow the
  rollback/runbook path, report the exact failing and restored revisions, and
  create a separately scoped fix only with authorization.
- **Scope drift:** stop and ask. Unauthorized extra PRs are not part of the loop.

## Verification Checklist

- [ ] Branch began from current `origin/main` in the recorded checkout mode.
- [ ] Unrelated dirty paths were preserved and excluded.
- [ ] Diff-derived local gates passed and every changed file is accounted for.
- [ ] UI approval is bound to an immutable candidate tree and unchanged UI paths.
- [ ] PR remote head equals the verified local head.
- [ ] Latest-head CI is green and review findings converged.
- [ ] Final base freshness check passed.
- [ ] Merge commit preserves reviewed branch ancestry.
- [ ] Exact merged SHA reached the expected deployed artifact and live smoke.
- [ ] Checkout returned to updated `main` only after verification.
- [ ] No next PR started without authorization.

---
name: orgmemory-pr-loop
description: Drive one OrgMemory branch through PR, CI, CodeRabbit review, and merge, one PR at a time ("the loop"). Use when the user says "loop", asks to open/merge a PR, or hands over an increment split into a PR series. Do not use for repos other than OrgMemory, for work the user has not scoped into a PR, or as permission to start the next PR without an explicit go-ahead or an active loop directive.
---

# OrgMemory PR Loop

One PR at a time: open, watch CI, address review, merge, verify, clean up —
only then start the next. The user calls this "the loop" and expects it to be
followed whenever a PR is in flight.

## Purpose

Ship an approved change to `main` through the repository's real CI/review
pipeline without conflicts, unreviewed findings, or parallel half-finished PRs.

## Trigger Conditions

- The user says "loop", "theo loop", "mở PR", "merge khi xanh", or dispatches
  an increment planned as a PR series.
- A locally verified change is ready and the user has approved turning it into
  a PR.

Not a trigger: finishing PR N. Starting PR N+1 needs the user's go-ahead unless
a standing loop directive explicitly covers the whole series. A PR created
outside the approved scope has been reverted before; scope control is part of
the workflow.

## Required Context

- Work happens in a dedicated worktree taken from `origin/main`. Run
  `git worktree list` first — this repo has worktrees under several roots.
- `gh` CLI is authenticated. `jq` is not on PATH; use `gh --jq`, never `| jq`.
- Commit rules and verification gates: `docs/conventions.md` (Commits,
  Verification). Batch related items; under ~100 changed files per PR is the
  accepted ceiling.
- CodeRabbit reviews PRs automatically and can hit rate limits.

## Procedure

1. **Confirm scope.** State what goes into this PR and get confirmation unless
   the scope was already dictated. Batch related items rather than opening many
   micro-PRs.
2. **Merge main first.** `git fetch origin && git merge origin/main --no-edit`
   on the branch *before* opening the PR — `docs/roadmap.md` conflicts almost
   every time otherwise. Re-run the narrowest affected gates after the merge.
3. **Open the PR** with a conventional `type(scope): subject` title.
4. **Watch CI to completion.** Either
   `gh pr checks <n> --watch --interval 10`, or poll with
   `Start-Sleep -Seconds 45; gh pr checks <n>`; for a specific run,
   `gh run watch <id> --exit-status --interval 10`. Do not report done while
   checks are pending.
5. **Monitor review, not just checks.**
   `gh pr view <n> --json reviewDecision,statusCheckRollup,reviews,comments`
   and `gh api repos/<owner>/OrgMemory/pulls/<n>/comments --paginate`.
   Address every actionable CodeRabbit finding with a fix commit or a reasoned
   reply. If CodeRabbit is rate-limited or silent, all-green CI is sufficient
   to merge.
6. **Merge on origin, then pull.** Merge the PR remotely (or hand the merge to
   the user when they've said they merge), then update local `main` and remove
   the worktree/branch. Never merge the branch into local main by hand.
7. **Verify post-merge when deployment applies.** CI/CD deploys on merge:
   check the deployed behavior through the server API, a Playwright pass, or
   on the ZM host — `ssh zm`, then `docker logs` on `orgmemory-api-1` /
   `orgmemory-worker-1` grepping for the WARN/ERROR signatures the change
   should have removed (or must not have introduced). Deployment mechanics
   live in `docs/runbooks/production-zm-deployment.md`; do not restate them.
8. **Report, then wait.** Summarize what merged and what the next candidate PR
   is; start it only on go-ahead or an explicit series directive.

## Verification

The iteration is done when: PR merged into `origin/main`, local main updated,
worktree cleaned, CI green on main, review findings addressed or answered, and
deployed behavior spot-checked when the change ships to the server.

## Failure Handling

- **Red CI:** read the failing job (`gh run view <id> --log-failed`); for
  Gradle test failures the fastest signal is grepping the JUnit XML report for
  `<failure message=` under `*/build/test-results/`. Fix, push, resume
  watching. Do not merge around a red required check.
- **Merge conflict on PR:** merge `origin/main` into the branch again and
  resolve there — expect `docs/roadmap.md`.
- **Review still pending after CI is green:** wait for CodeRabbit or confirm it
  is rate-limited before applying the green-CI rule.
- **Scope drift discovered mid-PR:** stop, surface it, and let the user decide;
  unauthorized extra PRs get reverted.

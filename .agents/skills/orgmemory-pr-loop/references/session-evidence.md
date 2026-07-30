# Session Evidence — orgmemory-pr-loop

Sanitized summaries only. No transcripts, credentials, or personal data.

- **codex 12ef8462 (2026-07-25 → 07-28, D:\OrgMemory).** Asset-registry PR
  series. Standing objective: "loop until done; when a PR is created check
  CodeRabbit review and fix; if CodeRabbit is limited, all-green is enough to
  merge." Commands show `Start-Sleep 45; gh pr checks <n>`,
  `gh pr checks <n> --watch --interval 10`, `gh run watch <id> --exit-status`,
  `gh pr view <n> --json mergeable,reviewDecision,...`. User corrected: "merge
  at origin then we pull" and capped batches at "under 100 changed files per
  PR". PRs 4–5 started without approval were reverted (branch
  `revert-unauthorized-pr4-pr5` still exists).
- **claude bfc22d2b (2026-07-29 → 07-30, observability increment).** User
  corrections: "always merge main before creating the PR — it conflicted
  again", "there's a review — how are you monitoring?", "not following the
  process? loop until merged into main before continuing." Worktree
  `OrgMemory-worktrees/observability-pipeline` taken from main; post-merge
  verification over `ssh zm`.
- **codex 4003dcf7 (2026-07-27 → 07-29).** User restated the loop verbatim:
  "one PR done → PR onto main → merge newer main if any → check CodeRabbit,
  fix if needed; if CodeRabbit rate-limits, all-green CI means merge; repeat
  until done."
- **codex 147d5354 (2026-07-15 → 07-26).** Heavy use of the same watch loop
  (`gh pr checks 22 --watch`, `Start-Sleep 55; gh pr checks 35`, commit-status
  and PR-comment polling via `gh api`).
- **codex c4544da8, codex d016c3f6, claude 78fca769.** Same cadence on other
  increments: "merge main that has new code, then continue", "there's a
  CodeRabbit review — aren't you monitoring?", "push + create PR, wait for
  CodeRabbit, fix, but merge main first", post-merge UI checks via Playwright
  or the deployed server.

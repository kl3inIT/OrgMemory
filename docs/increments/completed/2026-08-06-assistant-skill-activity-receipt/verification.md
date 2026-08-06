# Assistant Skill Activity Receipt Verification

Date: 2026-08-06

## Delivered behavior

- The waiting row now ends only after rendered Assistant text. A source frame
  and transport completion cannot create a blank pre-answer handoff.
- Successful actor-authorized Skill activation emits a server-sanitized title
  and turn-local ordinal. Discovery and every failure stay unnamed.
- Resource activity is correlated only with an exact release activated earlier
  in the same turn.
- The browser renders a plain-text `Using <title> skill` receipt before the
  current answer, keeps it open while active, collapses it at visible output,
  and never reconstructs it from transcript history.
- Abort, error, stop, context change, and finish-without-output terminate the
  waiting state; empty finish produces a retryable visible error.

## Gates

- Fable 5 independent read-only challenge: `REVISE`, all required safeguards
  incorporated in the implementation and decision 0033.
- `./gradlew.bat --no-daemon compileJava`: passed.
- `./gradlew.bat :core:test --rerun-tasks`: passed.
- Focused core, AI gateway, and API streaming tests: passed.
- Node `v24.15.0` with the frozen pnpm lockfile.
- Web Oxlint: passed with no findings.
- Web TypeScript project build: passed.
- Web unit suite: 23 files, 75 tests passed.
- Web production build: passed.
- Playwright browser suite: 32 tests passed.
- `git diff --check`: passed.
- JetBrains MCP inspection was unavailable; Gradle compilation and tests were
  used as the backend static-analysis fallback.

## Residual scope

- Skill receipts remain intentionally current-turn-only and disappear on
  reload. Durable replay needs a separate retention, revocation, and schema
  decision.
- The receipt proves successful tool activation, not answer correctness or any
  additional authority.

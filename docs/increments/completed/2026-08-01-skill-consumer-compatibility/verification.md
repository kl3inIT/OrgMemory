# Skill consumer compatibility verification

Date: 2026-08-01

## Architecture gate

- Fable 5 was unavailable because the project owner reported quota exhaustion.
- A fresh Codex `gpt-5.6-sol ultra` adversarial challenge accepted the proposal
  with changes before implementation.
- The final design keeps the Agent Skills package canonical, makes the CLI the
  installation authority, and limits the web descriptors to feature-local
  consumer projections.
- The UI says `Install with...`, `Verified package`, `Install supported`, and
  `Runtime behavior not certified`; it does not claim consumer runtime parity.

## Local gates

Run on Node `v24.15.0` from a clean physical worktree after merging the current
`origin/main`:

- focused Vitest suites passed seven tests covering consumer descriptors,
  target-specific handoff construction, the installer, and the shared handoff
  panel;
- the complete web unit suite passed 56 tests across 16 files;
- web OpenAPI drift, Oxlint, TypeScript, and production Vite build gates passed;
- the released-Skill Playwright path passed;
- docs checks and the production Fumadocs build passed for 147 pages;
- `python scripts/check_docs.py`, `pnpm release:check`, and
  `git diff --check` passed.

JetBrains inspection and Gradle were not applicable because no backend Java,
configuration, persistence, migration, authorization, or API contract changed.

## Visual QA

- Desktop light and dark released-Skill views keep the verified package card
  compact and expose exactly one `Install with...` action.
- The narrow dark dialog retains the exact project-local target, CLI command,
  supported-installation statement, and runtime non-certification without
  document-level horizontal overflow.
- The bilingual Product Guide uses a browser-harness screenshot with synthetic
  documentation data rather than production or customer data.

## Delivery evidence

PR #234 changed 22 files and merged with its full commit history as merge commit
`cc818583f2023a52d218fd2a8eaeb21db15e90ca`. Required CI passed. CodeRabbit's
first attempt was rate-limited; after the quota reset, the requested review
finished successfully with no review or inline finding.

Main CI run `30704552171` passed on the exact merge commit. The automatic
delivery chain completed for the same SHA:

| Workflow | Run | Result |
| --- | --- | --- |
| Release OrgMemory | `30704663875` | passed |
| Build production images | `30704663877` | passed |
| Build docs image | `30704663888` | passed |
| Deploy docs | `30704775967` | passed |
| Deploy production | `30704763179` | passed on attempt 2 after an initial SSH connection timeout |

The initial production attempt timed out during the ten-second SSH connection
window before reaching the host. Re-running only the failed job deployed and
verified the same immutable image set; no rebuild or source change was needed.

After deployment, these public checks succeeded:

- `https://om.kl3in.tech` returned HTTP 200;
- `https://om.kl3in.tech/healthz` returned `ok`;
- `https://om.kl3in.tech/api/health` returned the healthy API payload;
- the OrgMemory OIDC discovery endpoint returned HTTP 200;
- `https://docs.kl3in.tech/healthz` returned HTTP 200;
- the English and Vietnamese governed-Assets guides returned HTTP 200 and
  contained the new consumer guarantee language;
- `https://docs.kl3in.tech/images/product-guides/skill-consumer-install.png`
  returned HTTP 200.

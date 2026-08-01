# Adversarial architecture challenge: Skill CLI distribution and lifecycle

You are an independent, read-only architecture reviewer. Attack the proposal;
do not validate it by default. Verify every claim in the repository and pinned
references. Do not edit files, create commits, or mutate runtime state.

Read first:

- `AGENTS.md`
- `ARCHITECTURE.md`
- `docs/conventions.md`
- `docs/guidelines/agent-safety.md`
- `docs/guidelines/product-releases.md`
- `docs/specs/domains/asset-registry.md`
- `docs/tests/domains/asset-registry.md`
- decision filenames under `docs/decisions`
- `docs/increments/active/2026-08-01-skill-cli-distribution-lifecycle/design.md`

## Product promise at stake

OrgMemory must distribute an authorized immutable Skill into supported external
agents without pretending to be their runtime. A local lifecycle must detect
drift and avoid silently deleting user changes. Public CLI distribution must
not introduce a long-lived npm credential or an unpinned supply-chain path.

## Exact proposal under review

1. Publish `@orgmemory/cli` as a public Node 24 package with independent CLI
   versioning. Use a dedicated GitHub Actions workflow, npm Trusted Publishing
   OIDC, public provenance, an approval environment, and no dependency cache.
2. Bootstrap the first publish only after the project owner controls the
   `@orgmemory` npm organization; subsequent publishes are workflow-only.
3. Upgrade receipts to schema v2 with the verified file manifest.
4. Add offline `verify`, exact `update --to`, and safe `remove`; refuse modified
   or legacy-unverifiable deletion unless `--force` is explicit.
5. Only after the package is live, render a pinned `npx --yes
   @orgmemory/cli@<cli-version>` command in the existing released-Skill dialog.
6. Keep exact Skill release references and Claude Code/Codex as the only
   supported local adapters.

## Repository evidence

- `apps/cli/package.json`
- `apps/cli/src/index.ts`
- `apps/cli/src/install.ts`
- `apps/cli/src/install.test.ts`
- `apps/web/src/features/assets/agent-handoff/skill-agent-handoffs.ts`
- `.github/workflows/release.yml`
- `.github/workflows/ci.yml`
- `scripts/release-workflow-policy.mjs`
- `docs/specs/domains/asset-registry.md`

## Pinned comparison sources

- Vercel Skills: `D:/OrgMemory/tmp/skill-registry-research/vercel-skills`
  at `1164afa5f0e21ebd01e6fc11249759353f494ad1`.
- ClawHub: `D:/OrgMemory/tmp/skill-registry-research/clawhub`
  at `a643b75eca24d2180d7c6819d17476f278ad5e00`.
- Onyx: `D:/OrgMemory/tmp/onyx`
  at `618b5031bf21463f44e3bed9eb9d5073b806fec0`.
- AgentRegistry: `D:/OrgMemory/tmp/upstream-agentregistry`
  at `d8d3f4ef1ebeed70d58adafd26590ead6198addf`.
- 9Router: `D:/OrgMemory/tmp/upstream-9router`
  at `6fcd27337a7893642c7fe630840d0a641743f28f`.

Treat current npm documentation as authoritative over reference repositories.
The package currently returns npm `E404`, and this machine returns
`ENEEDAUTH`; do not assume the scope exists or that credentials are available.

## Questions to decide

1. Is independent CLI versioning safer than coupling it to
   `release/product.json`?
2. Is an approved manual workflow the right publishing boundary, or should it
   be driven automatically from product GitHub releases?
3. Does storing the release manifest in a token-free receipt adequately support
   offline verification without leaking sensitive data?
4. Should `remove --force` exist, or should legacy/modified installs require
   manual cleanup or a verified reinstall?
5. What must be atomic when updating both an installed tree and its lock entry?
6. Is two-PR activation necessary to avoid rendering an npm command before the
   package exists?

## Required verdict

Return plain Markdown with:

1. **Verdict**: accept, accept with changes, or reject.
2. **Strongest counterargument**.
3. **Must-fix list**, ordered by severity.
4. **Repository evidence** for every claim, with file paths.
5. **Reference evidence**, including invalid comparisons.
6. **Recommended final contract** for versioning, receipts, verify, update,
   remove, publishing, and UI activation.
7. **Rejected alternative** and why.
8. **Scope limits** that must remain non-goals.

Challenge your own verdict with at least five concrete failure scenarios,
including npm scope takeover/misconfiguration, compromised package
publication, modified local files, interrupted update/receipt failure, and a
legacy schema-v1 receipt.

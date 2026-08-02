# Typed Knowledge Space audience verification

Date: 2026-08-02

## Architecture gate

- The material authorization decision reuses the completed two-architect
  challenge and fresh-judge verdict recorded by the effective-access inspector
  increment.
- [ADR 0029](../../../decisions/0029-typed-knowledge-space-audiences.md)
  consolidates the binding mode, cross-store fail-closed, operational-role, and
  governed-transition decisions.
- Backend characterization tests failed before implementation on missing typed
  modes and invariants. The OpenFGA characterization initially proved that an
  operational administrator still inherited `can_view`; the versioned model
  now separates administration, authoring, publication, and viewing.

## Backend and authorization gates

- Focused migration, Space administration, canonical retrieval, and API
  integration tests passed.
- The worker fixtures found by the first full clean run were upgraded to the
  same deterministic department-mode shape as migration V19; all five affected
  worker suites then passed.
- `fga model validate` passed.
- `fga model test` passed 9/9 cases, 96/96 checks, and 31/31 `ListObjects`
  assertions.
- `gradlew.bat --no-daemon clean test` terminated successfully in 2m32s. The
  generated XML evidence contains 1,167 tests across 244 suites with zero
  failures or errors.
- `git diff --check` passed.

JetBrains semantic inspection was unavailable in this session because no
JetBrains MCP tool was connected. The documented fallback was used: focused
compilation/integration tests followed by the terminating clean multi-module
suite.

## Frontend and contract gates

Run with Node `v24.15.0`:

- web OpenAPI drift check passed;
- Oxlint, TypeScript, and the production Vite build passed;
- all 62 web unit tests across 18 files passed;
- all 17 Playwright browser flows passed, including the typed Space audience
  flow with no organization, department, Space, or model identifier rendered;
- docs OpenAPI, lint, type generation, MDX, content, manifest, publication,
  route, and link checks passed;
- the production docs build generated all 147 static pages;
- `python scripts/check_docs.py` passed for 504 Markdown files and eight
  mirrored domain pairs;
- `pnpm release:check` passed.

## Visual and interaction verification

The current dark desktop browser capture was inspected after the final UI
changes. Creation presents three consequence-oriented modes, defaults to the
safer department choice, hides the department control for other modes, and
states that Source ACL remains the document ceiling. Space rows use business
names, distinguish policy-managed viewers from ordinary operational grants,
and render contradictory tuples as `Audience policy drift` plus `Not effective
· remove drift` with an explicit repair action.

The Product Design audit workflow could not attach to the in-app Browser in
this session, so no formal plugin audit is claimed. The repository-owned
Playwright flow supplied the current screenshot, interaction, console-error,
and internal-identifier evidence instead.

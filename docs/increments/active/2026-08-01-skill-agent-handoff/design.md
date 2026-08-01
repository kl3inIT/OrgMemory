# Skill agent handoff

Date: 2026-08-01

## Outcome

Let an employee hand a local Skill task to Claude Code, Codex, or another
terminal-capable agent without turning OrgMemory into an agent runner:

```text
Create Skill -> copy bounded prompt or CLI command -> local agent validates
             -> explicit user confirmation -> private OrgMemory Draft

Released Skill -> copy bounded prompt or exact CLI command -> explicit target
               -> authenticated exact-version installation
```

The web application only prepares and copies instructions. The official CLI,
MCP gateway, API, Core authorization, and package verification remain the
enforcement path. No browser button executes a local command.

## Product decision

Keep `/assets/new/skill` as the creation hub that was shipped by the Browser
Skill authoring increment. Compact its three browser-authoring choices and add
one agent handoff surface with `Use your agent` and `Use CLI` tabs. The released
Skill detail uses the same presentation for exact-version installation.

Introduce one feature-scoped, display-only `AgentHandoff` descriptor under the
Assets frontend. Its required confirmation boundary and completion note make
the safety copy structurally non-optional. Skill-specific builders own the
actual Draft and installation semantics. The descriptor is not a backend
domain type and is not promoted to other Asset profiles until a second profile
proves the same workflow.

The Draft prompt must ask for a missing namespace, Knowledge Space UUID, and
classification and stop rather than guess. It validates and dry-runs before
asking for confirmation. A successful CLI upload creates a private Draft only;
submission, approval, release publication, sharing, access changes, and
deletion remain separate product actions.

The installation prompt and commands pin the exact released
`namespace/slug@version`. They do not install or upgrade the OrgMemory CLI,
request broader access, or bypass the browser-based OAuth flow.

## Independent challenge

Codex argued for Skill-local builders and a shared loose-props panel. Fable 5
argued for a typed view descriptor. After two rounds, Fable conceded that the
contract must remain inside the Assets frontend and must not claim generic
Connector or future Asset semantics. Codex conceded the shared panel and two
real Skill consumers.

An independent record-only Fable 5 judge selected the narrowed typed design.
The decisive reason was that a required confirmation boundary provides a
compile-time presence guarantee at negligible cost, while correctness still
remains covered by focused tests. The rejected loose-props alternative had
nearly identical code but left safety-copy presence to reviewer memory.

## Scope

- typed, copy-only `AgentHandoff` descriptor and Skill builders;
- one shared presentation composed from current OrgMemory primitives;
- compact browser creation cards;
- Draft upload prompt and CLI command on the creation hub;
- exact-version install prompt and Claude Code/Codex commands on Skill detail;
- focused unit, component, browser, specification, and test-matrix coverage.

## Non-goals

- no backend, API, OpenAPI, MCP, CLI, persistence, or authorization change;
- no command execution from the browser;
- no CLI installation or package-manager distribution claim;
- no cross-Asset registry of actions or definitions for unshipped profiles;
- no destination form duplicated on the hub;
- no automatic submit, review, release publication, sharing, or deletion;
- no claim that displayed OAuth scopes grant authorization.

## Delivery

One merge-commit PR below 100 changed files. Commits are preserved and the PR
must complete typecheck, unit, production build, browser QA, CI, CodeRabbit,
merge, and post-merge verification.

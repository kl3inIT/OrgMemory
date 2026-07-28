# Repository Operating Model Refresh

## Context

OrgMemory already treats the repository as the engineering system of record,
but the harness predates the current revision of
[`glebfox/playbook`](https://github.com/glebfox/playbook) reviewed at commit
`26f719a68774573b65a427cbc57d136549c1f32c`.

The existing layout is broadly correct. The gaps are operational:

- the root map does not define a deterministic reading order or the full
  consolidation rules;
- specs and test-coverage documents do not identify their source paths or the
  commit against which they were reconciled;
- spec/test filenames are not one-to-one and two older graph documents overlap
  the current secure GraphRAG source of truth;
- documentation structure and local links have no automated repository gate;
- `ARCHITECTURE.md`, `docs/vision.md`, the roadmap, and several active plans
  contain stale dates, versions, status statements, or facts in the wrong
  document category.

## Decision

Adopt the playbook's repository-as-system-of-record model, adapted to
OrgMemory's polyglot monorepo:

1. `CLAUDE.md` remains the root thin map and owns repository workflow,
   deterministic reading order, and documentation hygiene.
2. `ARCHITECTURE.md` owns current product and cross-subproject facts only.
3. `docs/vision.md` owns product intent and target architecture, explicitly
   deferring current state to `ARCHITECTURE.md`.
4. `docs/roadmap.md` owns delivery status and future backlog, not current
   architecture or plan subtasks.
5. `docs/specs/domains/*.md` and `docs/tests/domains/*.md` mirror one-to-one.
   Every pair starts with `Source:` and `Reconciled:` provenance.
6. Active increment design and plan files are authoritative only during
   execution. Completed increment directories are immutable historical
   evidence and are never consulted for current behavior.
7. A dependency-free repository check validates local Markdown links,
   increment shape, spec/test symmetry, provenance, and conflict markers in CI.
8. Subproject-level maps and architecture files are created lazily. The
   upcoming `apps/web` and `apps/docs` reorganization will introduce them where
   the subproject-specific detail justifies progressive disclosure.

No product behavior changes in this increment.

## Documentation Reconciliation

The audit uses current repository code and contracts at `7cf1c8a` as evidence.
It will:

- refresh the actual module/runtime/version facts in `ARCHITECTURE.md`;
- remove current-state assertions from the vision while preserving intent;
- consolidate the older secure knowledge-graph spec into secure GraphRAG;
- consolidate browser-authentication and legacy relational-graph coverage into
  their owning domain test documents;
- add provenance to every durable domain spec/test pair;
- reconcile active plan status only where merged code or repository gates are
  direct evidence, leaving live/runtime gates open without live proof;
- close the forwarded-port increment because its fix is on `main`, its Docker
  regression is in CI, mandatory public smoke is now the deployment default,
  and its public TLS/OIDC/login callback verification was already recorded.

## Alternatives Rejected

- **Copy the upstream playbook verbatim.** Rejected because the project wins by
  default; OrgMemory already has useful security and verification rules that
  remain binding.
- **Create a second operating-model guideline.** Rejected because process facts
  belong in the root map. A second prose source would recreate the drift this
  increment removes.
- **Rewrite completed increments to modern terminology.** Rejected because they
  are historical point-in-time evidence.
- **Mark active work complete from merged code alone.** Rejected where the
  increment explicitly requires deployment, live-provider, rollback, or
  production measurements.

## Acceptance Criteria

- The root map defines reading order, workflow, consolidation, and hygiene
  without carrying product details.
- Current architecture, intent, status, conventions, decisions, specs, tests,
  and historical increments each have one unambiguous home.
- Domain specs and tests are symmetric and carry valid provenance.
- The documentation check passes locally and in a path-aware CI job.
- Stale current-state facts found in this audit are corrected from repository
  evidence.
- The increment is moved to `completed/` after verification.

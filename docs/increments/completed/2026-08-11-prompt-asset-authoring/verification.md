# Verification

Completed: 2026-08-14.

## Delivered behavior

Prompt is now a browser-native governed Asset workflow. An authorized author can
create one populated private schema-v1 text Prompt Draft atomically, edit it in
the shared Governance workspace, persist bounded synthetic evaluation cases,
choose no or optional natural-language Knowledge grounding, publish directly,
and evaluate the visibly selected immutable exact release after confirmation.
Sharing remains a separate explicit action. Ordered-message Drafts remain
read-only in the text editor.

No backend, persistence, authorization-model, OpenAPI, review-path, or strict
Required-grounding contract changed in this increment. The changed contracts
are the generated-client-backed Prompt creation route, the reusable Prompt Draft
editor, the Governance refresh/recovery behavior, and the released Prompt
evaluation flow.

## Reviewed head and pull request

The implementation branch was `feat/prompt-asset-authoring-and-use`. Project-owner
visual approval covered the immutable desktop and mobile browser evidence at
reviewed commit `a54b958c3dae77329dd8b5ff73ffe7af9bc7c961`.

Pull request [#352](https://github.com/kl3inIT/OrgMemory/pull/352) completed its
required checks and review loop. The final PR-head CI run `31820504034` passed.
Two actionable review threads were fixed with regressions before merge; no
review thread remained unresolved. The PR was merged without squash as
`3701b365bd85bfe34eb5e8a045cf84c916c96293`.

## Verification gates

The final local candidate passed:

- `corepack pnpm --filter @orgmemory/web lint`;
- `corepack pnpm --filter @orgmemory/web typecheck`;
- `corepack pnpm --filter @orgmemory/web test:unit` — 36 files and 133 tests;
- `corepack pnpm --filter @orgmemory/web build`;
- `corepack pnpm release:check`;
- focused Prompt browser cases for atomic creation, exact-release evaluation,
  transient target retry, successful-mutation refresh recovery, denied creation,
  ordered-message protection, and withdrawn-release denial.

The authenticated browser harness exercised:

```text
/assets/new/prompt
  -> /assets/{promptId}/governance
  -> /assets/{promptId}?release={exactReleaseId}
```

It proved atomic create, Governance edit, direct publication, and evaluation of
the selected exact release. Error variants proved input retention without an
orphan, explicit retry, capability denial, and withdrawn-release refusal.
Desktop and narrow mobile screenshots covered light/dark presentation, reduced
motion, labels, keyboard error focus, non-color status, and horizontal overflow.

## Immutable release and production evidence

Main CI run `31820867126` passed on the exact merge commit. The automatic
release chain completed for the same SHA:

| Workflow | Run | Result |
| --- | --- | --- |
| Build production images | `31821173287` | passed |
| Build docs image | `31821173355` | passed |
| Release OrgMemory | `31821173359` | passed |
| Deploy docs | `31821191401` | passed |
| Deploy production | `31821342820` | passed |

The production deployment checked out detached commit
`3701b365bd85bfe34eb5e8a045cf84c916c96293`, deployed its immutable image set,
passed the rendered login check, and reported `OrgMemory production smoke
passed`. An independent browser request to `/assets/new/prompt` reached the
production OrgMemory OIDC login boundary. No deployment rollback was required.

## Known gaps and cleanup

Strict Required Knowledge grounding, Draft-time provider execution, automatic
promotion, review publication, mutable aliases, and personal Prompt stores
remain explicitly outside this increment. Production visual verification did
not reuse a user credential; authenticated product behavior is covered by the
browser harness, while production evidence covers the exact-SHA deployment,
OIDC boundary, rendered login, and runtime smoke.

This closeout moves the complete increment from `active` to `completed`, updates
the roadmap and domain provenance, and introduces no runtime change. The local
implementation branch and worktree are removed after the closeout merges.

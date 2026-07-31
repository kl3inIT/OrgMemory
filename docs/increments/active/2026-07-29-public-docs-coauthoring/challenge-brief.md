# Changelog Publication Architecture Challenge

## Adversarial review instruction

Attack this proposal rather than validating it. Verify every claim in the
repository and pinned references. Look specifically for release recursion,
stale generated navigation, broken locale routing, private-repository coupling,
and a sidebar design that hides the normal documentation selector. Do not edit
files. Read `CLAUDE.md`, `AGENTS.md`, `docs/conventions.md`, this increment's
`design.md`, and relevant decision filenames before returning a structured
verdict with must-fix items and file evidence for every claim.

## Product promise at stake

OrgMemory is a governed organizational-memory product whose public docs must be
a safe, reader-oriented projection of repository truth. A product release must
produce release notes that remain available even when the source repository is
private, while the public site must never drift from the reviewed Tegami release
record.

## Exact proposal under review

> Keep `release/CHANGELOG.md` as the sole canonical product-release history.
> During Tegami's `applyCliDraft`, deterministically generate the public recent
> release include, internal archive include, and localized Fumadocs release-note
> navigation metadata. Expose Changelog as a Fumadocs root context so its page
> tree contains `Latest`, recent version anchors, and an internal `Older
> releases` page. Keep the global Changelog link. Never fetch GitHub Releases at
> docs build or request time, and never hand-edit versions in MDX, React, or
> metadata.

Current enforcement and affected paths:

- `scripts/tegami-product.mts:18-22,241-255,603-614` owns the canonical preamble,
  public projection, and post-version write hook.
- `scripts/check-release.mjs:34-42` currently rejects a stale public projection.
- `apps/docs/src/lib/layout.shared.tsx:14-15` owns the global localized link.
- `apps/docs/content/docs/changelog/meta.json` is the prototype root metadata;
  its hard-coded versions and GitHub archive link are specifically rejected.
- `apps/docs/content/docs/meta.json` determines which root contexts the selector
  exposes.

## Comparable-system evidence

| System | Observed behavior | Mechanism and pinned source |
| --- | --- | --- |
| Fumadocs | A page uses the closest root folder as its contextual tree, while the full tree remains available to the selector. A folder may bind an explicit index page. | Pin `81c88c6bb3b03750ff707e6a9470b7ab20dfec7b`; `tmp/upstream-fumadocs-20260731/packages/base-ui/src/contexts/tree.tsx:31-44`; `tmp/upstream-fumadocs-20260731/packages/core/src/source/page-tree/builder.ts:355-389`. |
| Tegami | Changelog fragments form the version draft, and plugins may run after the CLI applies that draft. | Pin `86f2315e1adef4606ac4ef51333be53346520658`; `tmp/upstream-tegami-20260731/packages/tegami/src/index.ts:137-150`; `tmp/upstream-tegami-20260731/packages/tegami/src/types.ts:176-180`. |
| Onyx | The application links its exact running version to one standalone public Changelog URL rather than embedding release history in the application. | Pin `618b5031bf21463f44e3bed9eb9d5073b806fec0`; `tmp/onyx/web/src/sections/sidebar/AccountPopover.tsx:169-179`. |

## Motivating cost and incident

The first Changelog release added a global link, but entering it removed the
documentation-area selector because it was a loose root page. The prototype
restored contextual navigation by hard-coding `v0.1.1`, `v0.1.0`, and an
external GitHub archive URL. That creates manual work every release, guaranteed
drift risk, and an availability failure after repository privatization. A long
single page also becomes progressively harder to scan.

## Required verdict shape

Return:

1. `VERDICT`: accept, accept-with-must-fix, or reject.
2. `MUST-FIX`: concrete pre-implementation requirements.
3. `EVIDENCE`: repository path and line references for every finding.
4. `COUNTERARGUMENT`: the strongest alternative and why it wins or loses.
5. `SCOPE LIMITS`: what this verdict does not establish.

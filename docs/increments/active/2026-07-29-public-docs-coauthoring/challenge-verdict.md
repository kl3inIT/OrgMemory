# Changelog Publication Architecture Verdict

Date: 2026-07-31  
Reviewed commit: `31bf7c834afe5220fb41143130f4510866829718` plus the
uncommitted implementation in `docs/changelog-sidebar-prototype`  
Verdict: **accept with must-fix items**

## Committed recommendation

Keep `release/CHANGELOG.md` as the canonical product history and generate the
recent public include, internal archive include, and localized Fumadocs
navigation metadata during Tegami's post-version hook. Changelog is a
Fumadocs root context for presentation, while the four Diátaxis roots remain
the product-documentation categories. Keep the global Changelog link and never
depend on GitHub repository visibility to serve public history.

Before completion, the implementation must:

1. permit release-controlled files only in a structurally valid Tegami Version
   PR and reject synchronized manual canonical-history edits;
2. use explicit stable release anchors shared by generated content and
   navigation;
3. bind the first release to `release/product.json` and require strictly
   descending semantic versions;
4. record this root-context distinction in the active design and docs
   architecture;
5. keep the numeric recent-release limit in generator code rather than
   duplicating it in authored prose.

All five items are in scope for this change and are not deferred.

## Strongest counterargument and rejected alternative

The strongest alternative parses the canonical changelog at docs build time
and renders a custom runtime sidebar. It avoids committed metadata, but it
duplicates Fumadocs locale, mobile, accessibility, active-path, and selector
behavior and still does not prove Tegami provenance. It is rejected in favor
of deterministic committed projections plus byte-level drift checks.

Using GitHub Releases as the archive is also rejected because a private source
repository must not make public release history unavailable.

## Repository and reference evidence

- Fumadocs chooses the nearest root folder while retaining the full selector
  tree: pinned `tmp/upstream-fumadocs-20260731/packages/base-ui/src/contexts/tree.tsx:31-44`.
- Fumadocs supports root folders and `pagesIndex`: pinned
  `tmp/upstream-fumadocs-20260731/packages/core/src/source/page-tree/builder.ts:355-389`.
- Tegami exposes the post-draft `applyCliDraft` hook: pinned
  `tmp/upstream-tegami-20260731/packages/tegami/src/types.ts:176-180`.
- Onyx links its running product version to a standalone Changelog URL: pinned
  `tmp/onyx/web/src/sections/sidebar/AccountPopover.tsx:169-179`.
- The exact proposal and motivating prototype defect are recorded in
  [challenge-brief.md](challenge-brief.md).

## Scope limits

This verdict does not establish publication dates, translated generated release
bodies, optimal retention count, immutable artifact correctness, deployment
success, or final cross-browser visual polish. Those require their existing
release, editorial, CI, and deployment gates.

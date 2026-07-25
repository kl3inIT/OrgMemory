# Plan

- [x] Audit the pinned LightRAG Graph Viewer components and current OrgMemory
  graph/curation contracts.
- [x] Obtain the required independent architecture review.
- [x] Add the permission-scoped viewer metadata required for curation.
- [x] Port viewer state, loading, events, search, layouts, controls, legend,
  settings, and property panels.
- [x] Wire append-only entity/relation edits, aliases, and suppressions.
- [x] Add focused backend tests and frontend static/build coverage.
- [x] Verify the Sources > Knowledge graph flow in a real authenticated browser.
- [x] Consolidate implemented behavior into specs/tests and complete this
  increment.

## Verification

- [x] Authenticated browser rendered 23 entities and 19 relations without Sigma
  coordinate or React state warnings.
- [x] Search, selection, properties, evidence, six layouts, legend, settings,
  expand, hide, and refresh were exercised against an indexed document.
- [x] Assistant remained server-owned `MIX` and returned a grounded answer with
  an authenticated citation.
- [x] OpenFGA bootstrap imported 127/127 current-model tuples and now rejects
  partial fixture imports.
- [x] Oxlint, TypeScript, production build, mechanical checks, and backend
  `clean test` passed.
- [ ] JetBrains inspection was unavailable for this non-indexed worktree; the
  mandated Gradle and mechanical fallback gates passed instead.

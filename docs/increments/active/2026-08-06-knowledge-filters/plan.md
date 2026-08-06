# Knowledge Workspace Filters And Paging — Plan

Design: [design.md](design.md). Its "Binding constraints from the challenge" are
requirements, not suggestions — each one below cites the constraint it satisfies.
One PR.

## 1. Persistence and query

- [ ] Flyway `V<next>__source_listing_indexes.sql` (check the highest existing
  `V` number first — main moves): trigram GIN on `source_objects.title` and
  `source_revisions.file_name` (`CREATE EXTENSION IF NOT EXISTS pg_trgm`),
  btree on `source_objects (organization_id, classification)`, and a
  `source_revisions (organization_id, updated_at DESC, id)` index supporting the
  cursor walk. Constraint 6.
- [ ] `SourceObjectRepository`: replace the two unbounded finders used by
  `listVisible` with one paged query that joins `source_revisions`
  **on `so.latest_revision_id`** (constraint 2), applies
  `so.status = ACTIVE`, the optional filters, `ORDER BY sr.updated_at DESC, so.id`
  (constraint 4), keyset predicate from the cursor, and `LIMIT pageSize + 1`
  (constraint 3).
- [ ] Companion count query returning `total` plus the three status buckets in
  one pass (constraint 5). Do not re-bind the id list more times than necessary.
- [ ] Bound the owner leg and assert the union against
  `KnowledgeRetrievalProperties.maximumAuthorizedObjects`, throwing
  `KnowledgeRetrievalUnavailableException` to match
  `SecureSourceVisibilityAdapter.java:57-61` (constraint 1).
- [ ] Keep the empty-authorized-set early return (constraint 7).
- [ ] Gate: terminating clean JVM test; migration applies on a fresh database.

## 2. Core service and API

- [ ] `SourceQueryService.listVisible` takes a filter/cursor command and returns
  a page record. Clamp `pageSize` with
  `Math.min(Math.max(pageSize, 1), 60)` **in the service**, never a 400
  (`AssetRegistryService.java:152-153` precedent).
- [ ] Do not unify the authorization-outage posture: visibility keeps throwing,
  action authorization keeps returning empty (constraint 8).
- [ ] `SourceController` `GET /api/sources` accepts `knowledgeSpaceId`,
  `classification`, `status`, `q`, `cursor`, `pageSize`; returns
  `{ items, nextCursor, pageSize, total, statusCounts }`.
- [ ] Tests — none exist for this path today
  (`SourceUploadIntegrationTests.java:97-98` covers `listOwn`):
  a staged-but-unpublished revision stays visible with its true status;
  filtering by a space the actor cannot see returns empty, not a leak;
  the empty authorized set short-circuits; cursor paging over a set mutated
  between pages neither duplicates nor skips a stable row; `pageSize` clamps.
- [ ] Regenerate `contracts/openapi.json` via `OpenApiContractTests` with
  `ORGMEMORY_OPENAPI_WRITE=true`.

## 3. Web

- [ ] Regenerate the hey-api client.
- [ ] Documents: `FilterBar` per the `/admin/users` convention — Space select
  (source: `listVisibleKnowledgeSpaces`, **not** upload targets, with an
  "All spaces" option), Classification select, existing status tabs, debounced
  search. All drive server query parameters.
- [ ] Status tab badges read `statusCounts` from the envelope, not the loaded
  array (constraint 5).
- [ ] `refetchInterval` keys off `statusCounts.processing > 0`, not the current
  page.
- [ ] Pagination control; an out-of-range cursor shows a pagination correction
  rather than the "No documents yet" empty state.
- [ ] Graph tab: drop the `Explore` submit, debounce the query the same way.
- [ ] Update the Playwright stubs that return bare arrays
  (`document-actions.spec.ts:227-340`, `knowledge-graph-layout.spec.ts:120`);
  add coverage for filtering by space and for truthful tab counts across pages.
- [ ] Gate: lint, typecheck, unit tests, production build, browser check.

## 4. Docs and consolidation

- [ ] `docs/roadmap.md`: mark this increment active.
- [ ] Reconcile `docs/specs/domains/knowledge-ingestion.md:29-39` (row contract,
  new list contract) and its test mirror; refresh `Source:`/`Reconciled:` lines.
- [ ] Decision entry for the cursor-over-offset choice and the envelope
  divergence from `AssetSummaryPage`, carrying the rejected alternative
  (filters pushed into the retrieval predicate) and why it cannot work.
- [ ] Regenerate `apps/docs/generated/openapi.public.json`.

# Knowledge Workspace Filters And Paging — Design

## Intent

The Knowledge workspace presents two tabs that disagree about how a reader
narrows what they see. The graph tab scopes by Knowledge Space (a mandatory path
parameter) and searches server-side behind an explicit submit. The Documents tab
carries no space concept at all: `GET /api/sources` takes no parameters, returns
every visible document, and every filter is client-side. Documents therefore
prints `Space: <name>` on every row (`sources-table.tsx:82`) while offering no
way to filter by it, and exposes `classification`, `sourceSystem` and
`owningDepartmentName` in the payload without a filter for any of them.

This increment makes Documents a server-filtered, server-paged, permission-scoped
list, and gives both tabs one search behaviour.

## Architecture challenge

An independent read-only reviewer challenged the first proposal (keep both
authorization scans untouched, add `IN (:authorizedIds)` filtering plus offset
pagination). Verdict: **sound in principle, must not ship as written**. Full
record: `tmp/knowledge-filters/challenge-verdict.md`. The findings that changed
the design are carried as binding constraints below. The project owner reviewed
the recommended scope cut (filters now, paging later) and chose to deliver the
whole capability now, done to the reviewer's requirements.

### Binding constraints from the challenge

1. **The owner leg is the unbounded one.** `created_by_user_id` on a
   connector-crawled object is the human connection owner
   (`ConnectorCrawlBatch.java:20`), and a connection "can hold tens of thousands"
   (`SourceObjectRepository.java:38-42`). Today
   `findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc`
   (`SourceQueryService.java:59-61`) loads all of them as managed entities on
   every request, with the 2s poll repeating it. Only the content-visible leg is
   capped at 5 000 (`SecureSourceVisibilityAdapter.java:57-61`). Past ~65 535 ids
   the `IN` bind list exceeds the PostgreSQL extended-protocol ceiling and the
   endpoint 500s — for exactly the account whose corpus justified paging. The
   owner leg must be bounded and the union asserted against
   `maximumAuthorizedObjects`, raising `KnowledgeRetrievalUnavailableException`
   rather than letting the driver decide.
2. **Join `so.latest_revision_id`, never `so.current_revision_id`.**
   `stageRevision` sets only `latestRevisionId` (`SourceObject.java:158-163`);
   `publishRevision` sets `currentRevisionId` (`:165-172`). The one existing
   SQL join between these tables uses `current_revision_id`
   (`SecureKnowledgeRetrievalStore.java:110`) and is the natural thing to copy.
   Copying it hides every in-flight document: a first upload has
   `current_revision_id IS NULL` and disappears, Processing and Needs-attention
   go permanently empty, and the poll never starts because the empty page has no
   active row. This is a correctness defect at zero corpus, not a scale one.
3. **Keyset cursor, not offset.** The sort key is mutated by the pipeline the
   page exists to watch. With `OFFSET`, a `publishRevision` on a lower row shifts
   everything down and the next 2s refetch shows one row twice and skips another.
   The repository already specifies the correct shape — exclusive cursor,
   `pageSize + 1` over-fetch, bounded page size
   (`AuthorizedGraphTraversalSource.java:19-63`,
   `docs/specs/domains/secure-graph-rag.md:130-144`).
4. **Sort on the timestamp the row displays.** The list renders
   `revision.getUpdatedAt()` (`SourceQueryService.java:200`) but a naive
   implementation sorts on `source_objects.updated_at`, which only advances on
   stage/publish. Sort and cursor on `source_revisions.updated_at` so the order a
   reader sees is the order the cursor walks.
5. **Per-status counts must ship with the page.** The four status tabs count the
   loaded array (`sources-page.tsx:181`, `source-status.ts:33-35`). The moment
   that array is one page, "Needs attention 2" can mean forty failures. A single
   `total` cannot repair it; the envelope carries counts for all four buckets
   over the authorized-and-filtered set.
6. **Indexes are missing.** No `(organization_id, updated_at DESC)`, none on
   `classification`, and no trigram index for text search
   (`V1__baseline.sql:2572,2579,2593`). Paired Flyway migration required.
7. **Keep the empty-authorized-set early return** (`SourceQueryService.java:65-67`)
   and pin it with a test; hand-built SQL that drops it can widen to the whole
   organization.
8. **Do not flatten the outage posture.** Visibility throws on an indeterminate
   authorization answer (`SecureSourceVisibilityAdapter.java:48-51`); action
   authorization returns empty (`SecureSourceActionAuthorizationAdapter.java:34-36`).
   Both are deliberate. An envelope reporting `total: 0` would lend a false
   authority to an outage that today merely looks suspicious.

### Rejected alternative

Push the filters and paging into the secure retrieval SQL by joining
`source_objects` against `ELIGIBLE_FROM`. Rejected: it crosses a closed module
boundary (`docs/specs/domains/secure-retrieval.md:49-58`; Source Ledger reaches
retrieval only through `SourceVisibilityPort`), and — decisively — it **cannot
express the feature**. `ELIGIBLE_FROM` requires `sr.status = 'READY'`,
`publication.status = 'APPLIED'` and `kc.active`
(`SecureKnowledgeRetrievalStore.java:174-180`), so a `PARSING` or `FAILED`
document has no rows in it at all. That is precisely why `listVisible` unions the
owner leg. Filtering by status inside that predicate makes the Processing and
Needs-attention tabs structurally incapable of returning anything.

## Decisions

### Response envelope

`GET /api/sources` becomes a paged envelope. The reviewer verified the blast
radius is one React page and two Playwright stubs — no MCP, assistant or CLI
consumer — so a clean break beats an optional-parameter shape that would leave
one operation with two response types.

The repository's shipped envelope is `AssetSummaryPage`
(`items/total/page/pageSize/totalPages/sort`), but that is offset paging over a
comparatively stable set. Documents move under active ingestion, which
constraint 3 rules out. This endpoint therefore keeps the *conventions* of
`AssetSummaryPage` — field naming style, and the
`Math.min(Math.max(pageSize, 1), 60)` clamp applied in the core service rather
than a 400 (`AssetRegistryService.java:152-153`) — while carrying a cursor
instead of a page number:

```
{ items, nextCursor, pageSize, total, statusCounts: { processing, ready, attention } }
```

`nextCursor` is null on the last page. `total` and `statusCounts` are computed
over the authorized-and-filtered set and are advisory: the authorized id set was
resolved by a non-transactional OpenFGA `ListObjects` call milliseconds earlier
(`OpenFgaRelationshipAuthorizationSetAdapter.java:47-58`), so neither is a
transactional guarantee. The design states this rather than implying otherwise.

### Query parameters

`knowledgeSpaceId`, `classification`, `status`, `q`, `cursor`, `pageSize`. Every
one is optional and every one narrows: filters are applied as `AND` against the
authorized id set and never replace it, so filtering by an invisible space
returns an empty page rather than a leak.

`q` moves server-side — with paging, a client-side `q` would search only the
loaded page. It matches `source_objects.title` and `source_revisions.file_name`;
`media_type` is dropped from search (nobody searches `application/pdf`). Both
columns get trigram GIN indexes.

### Space filter source

The filter list must come from `GET /api/knowledge-spaces/visible`, not the
`upload-targets` call the page already makes for its upload dialog
(`sources-page.tsx:75`). Those are different sets — a reader can hold view access
to a space they cannot contribute to — and using upload targets would omit
exactly the read-only spaces whose documents are on screen.

### Search parity

The graph tab drops its explicit `Explore` submit and searches on a debounce, the
same as Documents. One search idiom on one screen.

## Scope

In: server-side filters, keyset paging with per-status counts, the owner-leg
bound, the `latest_revision_id` join, the Flyway indexes, the Documents filter
bar and pagination, debounced graph search, the missing `listVisible` tests,
contract and generated-client regeneration.

Out: the graph space selector's scope-versus-filter labelling (explicitly
deferred by the project owner); `sourceSystem` and department filters; any change
to the retrieval predicate or the OpenFGA model.

## Constraints

- `ddl-auto=validate`: index changes ship as a paired Flyway migration.
- The `2s` refetch must key off `statusCounts.processing`, not the current page,
  or a reader on page 3 stops seeing progress on page 1.
- An out-of-range cursor renders a pagination correction, not the
  "No documents yet" empty state (`sources-page.tsx:249,353-361`).
- Reconcile `docs/specs/domains/knowledge-ingestion.md:29-39`, which documents
  the row contract.

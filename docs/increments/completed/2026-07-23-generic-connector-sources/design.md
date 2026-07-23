# Generic Connector Sources

## Problem

Adding a second source costs a schema migration, an edit to `core`, and an edit to
the catalogue page. None of those three should be true.

`source_objects.source_type` answers two questions with one column:

```
source_type = 'SLACK'   -- which system?  and  which access rule?
```

Only the second is ever read. Retrieval branches on `source_type <> 'UPLOAD'` and
nothing anywhere branches on the value `SLACK`. But because the source's name lives
in that column, it also lives in `chk_source_object_type CHECK (source_type IN
('UPLOAD','SLACK'))`, so every new connector needs DDL to widen a constraint that
guards a distinction the name has nothing to do with.

`core` holds thirteen references to a package-private `SlackConnectorProfile` across
`ConnectorReconciler`, `ConnectorObjectDirectory`, and `ConnectorIngestionService`.
The governed ledger should not know that Slack exists.

`source_connections` types the Slack-shaped settings as columns — `channel_filter`,
`max_threads_per_channel`. Google Drive has folders, not channels.

## Decision

**Separate the axes Onyx separates, without adopting the axis it puts on the
connection.**

Onyx models four things independently: `DocumentSource` (which system),
`SourceCategory` (a frontend-only grouping), `AccessType` on the
connector-credential pair (`PUBLIC`/`PRIVATE`/`SYNC`), and `InputType` (how the
crawl runs). Three of those four transfer.

`AccessType` does not. It is a policy an administrator picks, stored on the
connection, so flipping it silently rewrites the access rule for every object
already indexed under it. ADR 0009's live/native split is not a policy — it is a
fact about custody. A Slack thread's ACL *is* owned by Slack. Recording it per
object and refusing to let it change is the property a governed ledger wants, and
it is the same reason the ledger seals an ACL snapshot instead of re-asking the
source.

### Four layers

| Axis | Where it lives | Who owns it | Mutable |
| --- | --- | --- | --- |
| Which system | `source_system` column | the connector registry | no |
| Which access rule | `acl_authority` column, `updatable=false` | derived from the profile | never |
| Category, icon, form | TypeScript only | the browser | — |
| Crawl settings | shared columns + `source_config` JSON | an administrator | per poll |

`acl_authority` rather than `source_type`, because `source_type` is the name that
invites somebody to put `SLACK` back in it, and because the resulting SQL explains
itself:

```sql
so.acl_authority = 'SOURCE'      -- the source still decides; latest sealed generation only
so.acl_authority = 'ORGMEMORY'   -- we decide; ingestion ∩ current
```

It is stored per object rather than looked up from `source_system`, even though it
is derivable from it. Deriving it would mean one editable row could change the rule
retroactively for everything already ingested under it — the same defect as
`AccessType`. Storing it records what was true when the evidence entered.

### Profiles are contributed, not listed

`core` defines `ConnectorSourceProfile` and collects the beans that implement it.
Each adapter declares its own. After this, `core` contains no occurrence of the
string `slack`; the only profile it owns is the native one, because upload is not a
connector.

### The catalogue is the intersection of two registries

The backend reports which sources this deployment actually has, from the contributed
profiles. The browser owns display name, icon, category, and the form field
description. A source the browser knows about that the backend does not report
renders as unavailable rather than as a tile that fails when clicked.

This is what lets the grid show sources we have not built without lying about them.

## Consequences

Adding a source becomes: one adapter package with a `ConnectorSourceProfile` bean,
and one entry in the browser catalogue. No migration, no change to `core`, no change
to the catalogue page. If a third edit is needed, the separation is incomplete.

Renaming the column is a one-time data migration over `source_objects` and two lines
of retrieval SQL. The unique identity constraint changes with it: `source_type` was
in that key only because it carried the system, and leaving it would let two
connectors collide on one connection key.

Moving Slack-shaped settings into JSON gives up database validation of those fields.
The settings every source shares — enabled, target Space, actor, interval — keep
real columns and keep `chk_source_connection_crawl_targets`, because those are the
ones whose violation is a governance failure rather than a bad crawl.

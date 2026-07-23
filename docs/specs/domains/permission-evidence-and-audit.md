# Permission Evidence And Audit Spec

## Current Behavior

Source ACL snapshots/entries are sealed immutable evidence. A compare-and-set
head selects the monotonically increasing current generation and rejects stale
writers. Entries cannot be inserted after sealing; update/delete/truncate are
database-rejected. Unsupported or incomplete principals are quarantined.

Permission audit is append-only and written in an independent transaction. It
stores actor/resource/operation/decision/reason/policy/request/query fingerprint
and selected ACL snapshot IDs. Raw query text and unrestricted metadata are not
stored. An audit attempt does not prove the surrounding business transaction
committed.

Administrator actions on the identity ledger append an event attributed to the
acting administrator alongside the mapping service's event attributed to the
affected user; the pair answers who acted and who was affected. A per-connection
identity trust decision is audited the same way, and any non-default trust level
also records on the connection row who decided it and when.

## Source Modules

- `core.permission`
- `core.knowledge` ACL records
- `apps.api.permission`
- `apps.api.admin`

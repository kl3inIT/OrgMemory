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

## Source Modules

- `core.permission`
- `core.knowledge` ACL records
- `apps.api.permission`

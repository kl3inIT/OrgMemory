# Active Increments

Each active increment has `design.md` and `plan.md`. Keep only coherent work in
progress here. Consolidate current behavior before moving an increment to
`../completed`.

## Current Queue

1. Complete the
   [production ZM runtime](2026-07-25-production-cicd-zm/plan.md): disable the
   obsolete runner, take and restore-test backups, perform the bounded shared
   PostgreSQL cutover, bring up the runtime, and prove login, upload, GraphRAG,
   citation, denial, rollback, and resource behavior.
2. Complete the reproducible demo through the real ingestion API and run the
   permission evaluation dataset.
3. Prove the Slack connector against a real workspace, including member removal
   and the next-crawl access revocation.

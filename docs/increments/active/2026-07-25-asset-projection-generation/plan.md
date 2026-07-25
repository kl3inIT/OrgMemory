# Asset Projection Generation Repair Plan

- [x] Compare the failure with pinned LightRAG `v1.5.4` and current upstream
  issues.
- [x] Propagate the immutable asset generation through content metadata.
- [x] Remove the namespace snapshot generation from chunk construction.
- [x] Add fail-closed and differing-generation regression tests.
- [x] Add a forward data migration for existing PostgreSQL records.
- [x] Run focused and completion-grade verification.
- [ ] Deploy, verify Assistant retrieval/citations/permission, and archive this
  increment.

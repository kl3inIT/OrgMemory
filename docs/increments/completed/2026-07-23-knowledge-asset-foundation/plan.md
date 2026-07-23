# Plan

- [x] Inventory and remove Capability Asset consumers, model relations, API
  contract, tests, current specs, and forward-drop its schema.
- [x] Add the stable `KnowledgeAsset` and immutable `KnowledgeAssetVersion`
  schema/entity split with source evidence links.
- [x] Make source revision numbering monotonic and support changed-content
  revision N+1 without duplicate materialization.
- [x] Move projection generation allocation into the publication ledger and pin
  chunks/publications/citations to the immutable version.
- [x] Refactor connector materialization so OpenFGA is never invoked from an
  uncommitted caller transaction.
- [x] Replace connector-private chunking with the shared source processing
  contract.
- [x] Add publication convergence for retries, obsolete OpenFGA models, and
  orphan relationship tuples.
- [x] Update architecture, roadmap, specs, tests, ADRs, skills, and active
  increment status to current repository facts.
- [x] Run Java inspection when available, migration checks, OpenFGA model tests,
  focused integration tests, full Gradle clean tests, and web contract/build
  gates.

# Plan - Graph Extraction Model Route

Design: [design.md](design.md).

## Step 1 - Independent challenge

- [x] Dispatch the written brief to an independent read-only reviewer.
- [x] Record the verdict, strongest contradiction, recommendation, and scope.

## Step 2 - Configuration and tests

- [x] Set the Graph Extraction default to `gpt-5.4-mini` in API, worker, shared
  gateway properties, production Compose, and the production env example.
- [x] Keep Assistant Chat and Keyword Planning defaults unchanged.
- [x] Add focused tests proving the route default and production Compose contract.

## Step 3 - Consolidation and gates

- [x] Record the implemented default and the profile-aware UI boundary in the AI
  model control-plane and secure Graph RAG documentation.
- [ ] Run focused JVM configuration tests, production Compose validation, and the
  terminating clean test gate.

## Step 4 - Delivery and production proof

- Merge to `main`, deploy to ZM, and change the explicit production override.
- Verify the worker environment and a new graph-indexing profile/job.
- Record the production checkpoint in Northstar without secrets.

# Plan - RAG Workload Routing And Luna Evaluation

Design: [design.md](design.md).

## Step 1 - Evidence and independent challenge

- [x] Verify pinned LightRAG and Onyx role/reasoning behavior.
- [x] Verify OpenAI GPT-5.6 Luna and Spring AI 2.0.0 reasoning APIs.
- [x] Dispatch `challenge-brief.md` to a fresh read-only reviewer.
- [x] Record and resolve the accept-with-changes verdict before implementation.

## Step 2 - Characterization tests first

- [ ] Pin unchanged route defaults, OpenAI-option capability validation,
  protocol fail-closed behavior, route/cache identity, Keyword editability,
  request-time organization isolation, and Graph mutation denial.
- [ ] Pin schema-v1 graph profile restore and schema-v2 reasoning identity.
- [ ] Pin the REST/OpenAPI contract and RAG pipeline UI lifecycle copy.

## Step 3 - Backend and persistence

- [ ] Add capability-gated OpenAI route reasoning effort and pass it only
  through a declared supporting OpenAI-compatible adapter.
- [ ] Persist reasoning effort on organization Keyword/Assistant/Prompt route
  overrides and widen the workload constraint to Keyword Planning.
- [ ] Add reasoning effort to new immutable graph profiles while preserving
  exact schema-v1 restore behavior.
- [ ] Retain current deployment defaults until the live gates approve separate
  candidate activation.

## Step 4 - Administration UI

- [ ] Present Assistant Answer, Keyword Planning, and Graph Extraction as one
  RAG pipeline section.
- [ ] Allow Answer and Keyword route/reasoning edits; show Graph as
  deployment-managed and future-jobs-only.
- [ ] Keep Prompt Execution available and keep Reindex absent.

## Step 5 - Evaluation and verification

- [ ] Run bounded redacted Keyword and Graph candidate evaluations against the
  current baselines.
- [ ] Apply the activation rules in `design.md`; retain Graph baseline if Luna
  does not pass.
- [ ] Run focused backend/static/frontend gates, contract generation/drift,
  production build, and terminating clean JVM test.
- [ ] Reconcile specs, test matrices, architecture facts, and deployment docs.

## Step 6 - Delivery

- [ ] Open one PR, wait for CI and review, fix findings, and merge only green.
- [ ] Deploy the merged SHA and verify route values, worker health, one new
  Graph profile/job, and query behavior without starting a reindex.
- [ ] Archive this increment and the completed predecessor when their evidence
  is complete; update roadmap and Northstar with no secrets.

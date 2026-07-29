# AI Model Gateway Boundary Plan

- [x] Rename the Gradle module and provider-neutral Java package.
- [x] Extract OpenAI-compatible and Anthropic model construction into distinct
  protocol factories.
- [x] Replace the GraphRAG-facing OpenAI-named provider with a generic Spring AI
  chat model provider.
- [x] Preserve organization route resolution, cache isolation, and credential
  rotation eviction.
- [x] Add dispatcher tests for both protocols, missing factories, and duplicate
  registrations.
- [ ] Reconcile architecture, domain spec, coverage, and roadmap.
- [ ] Run focused module tests, API/worker compile, full backend/static/docs
  gates, then resolve PR review and CI.

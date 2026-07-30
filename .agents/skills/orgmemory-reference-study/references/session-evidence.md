# Session Evidence — orgmemory-reference-study

Sanitized summaries only. No transcripts, credentials, or personal data.

- **claude 78fca769 (2026-07-22 → 07-25, main).** Origin of the pattern: user
  asked to clone Onyx into `tmp` and learn from it, then repeatedly enforced
  it — "judge me, don't praise me: what do they do better, where am I
  over-engineered", "always learn from the Onyx reference", "run their UI and
  learn the component from their git directly". Also: "the mindset is to use
  Spring AI natives — poke straight into the vector module's jar", which
  became the Gradle-cache source-reading step.
- **LightRAG port sessions (orca workspaces pr02–pr12, 2026-07-23 → 07-24).**
  Every review prompt pins parity to
  `D:\OrgMemory\tmp\upstream-lightrag-v1.5.4`; the parity manifest lives in
  `docs/research/lightrag-v1.5.4-parity-manifest.md`.
- **claude bfc22d2b (2026-07-29 → 07-30, observability).** Spring Boot 4.1 /
  Spring AI observability defaults were established by unzipping
  `spring-boot-micrometer-tracing-opentelemetry` and starter jars from
  `~/.gradle/caches` and reading the auto-configuration source; Onyx tracing
  read at `tmp/onyx/backend/onyx/tracing/`. Findings landed in the
  observability challenge brief's comparable-system table.
- **codex 4003dcf7 (2026-07-27 → 07-29).** Model-provider gateway designed by
  comparing Northstar and Onyx side by side ("compare the two to derive the
  best architecture + research more"); user pointed at Spring AI examples
  repos to clone and read.
- **codex d016c3f6 / d68f7009 (2026-07-28 → 07-30, docs site).** Docs
  information architecture derived from reading Onyx docs and Fumadocs' own
  site; explicit correction: "don't trust the old research docs — treat them
  as reference and verify again."
- **Standing memory.** The Onyx checkout location and "read it rather than
  asserting what Onyx does" was already a persistent user instruction.

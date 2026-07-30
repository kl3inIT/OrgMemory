# 0019 — The payload boundary is its own module

Status: accepted, 2026-07-30.

## Context

[0018](0018-telemetry-carries-counts-never-payload.md) states that telemetry carries
counts and never the text those counts measure, and that the enforcement is structural.
It names `ExceptionSanitizingSpanExporter` as the last gate before egress and says it
applies to every span in the process, not only GraphRAG spans.

All of that enforcement lived in `integrations/graph-rag-observability`, whose build file
opens with `api(project(":components:graph-rag-core"))`. Taking the boundary therefore
meant taking the graph domain.

`apps/mcp` did not take it. Its build file has no `project(...)` dependency at all — it
was the only Gradle application in this repository with none — while carrying
`spring-boot-starter-opentelemetry` and exporting spans. So the guarantee 0018 claims
across the process held in two of the three deployables and in none of the third's spans.
`KnowledgeSearchApiClient` chains a `RestClientException` as the cause of its own
constant-message failure, and `exception.stacktrace` is exported unfiltered without the
sanitizer. Whether any particular exception carried payload was not established; that
nothing would have stopped it was.

By the module inventory, seven of the ten files in that module had nothing to do with
GraphRAG, two of them added the same day by the increment that found this.

## Decision

The boundary becomes `integrations/observability`, depending on no OrgMemory module:

- `ExceptionSanitizingSpanExporter` and `SpanExportSanitizationAutoConfiguration`
- `ProviderLoggingBoundaryVerifier` and its auto-configuration
- `ObservationContentBoundaryVerifier` and its auto-configuration

`integrations/graph-rag-observability` keeps what implements a core port — the Micrometer
and OpenTelemetry `GraphRagEventSink` adapters and the `GraphRagTaskDecorator`
implementation — and depends on the new module only in its test source set, where
`WholeExportAllowlistTests` asserts a GraphRAG span through the sanitizer in the position
the sanitizer occupies in production.

`orgmemory.spring-boot-app-conventions` adds the new module. Taking the convention is
taking the boundary. Every Gradle application in this repository already applies that
plugin, so `apps/mcp` gains the boundary without its build file changing, and a fourth
application cannot omit it by not knowing to ask.

The absence of a `project(...)` line in the new module's build file is load-bearing. It
is what keeps the boundary adoptable by a deployable that has no domain dependency, which
is the condition that failed here.

## Acknowledged exception to a stated rule

`ARCHITECTURE.md` states the adapter rule as `integrations -> core ports`. The new module
implements no core port; it implements OpenTelemetry's `SpanExporter` and two startup
checks that implement nothing. By that rule it is not an integration.

Placing it in `integrations/` anyway is the project owner's decision, taken on 2026-07-30
after the alternatives were presented. It is recorded here as an exception that was seen
and chosen, not one that was never noticed. `ARCHITECTURE.md` names it in the same terms.

## Rejected alternatives

**Add `graph-rag-observability` to `apps/mcp` and change nothing else.** One line, and it
closes the same gap. Rejected because it puts `components/graph-rag-core` into the one
deployable that had no domain dependency, to obtain a guarantee that has nothing to do
with the domain. The dependency would be permanent and the reason for it invisible.

**Leave the module whole and rely on the convention plugin alone.** The convention makes
the boundary unforgettable wherever it lives, so this closes the `apps/mcp` gap without
any move. Considered seriously and rejected: it would attach the graph domain to every
future application as the price of a payload boundary, which is the coupling rather than
the naming.

**A fifth top-level directory, `platform/`, for cross-cutting runtime policy.** The
honest placement by the repository's own rules, since the module fits neither
`integrations -> core ports` nor the framework-neutral description of `components/`.
Rejected by the project owner as more structure than roughly two hundred and fifty lines
earns, against a module count already at seventeen.

**Rename `graph-rag-observability` without splitting it.** Fixes the reading and not the
coupling. The name was the symptom.

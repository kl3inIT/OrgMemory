# 0020 — Assistant generation is observed on its own payload-free surface

Status: accepted, 2026-07-30.

## Context

`GraphRagEventSink.Stage` declared fourteen stages. Thirteen had producers. `GENERATE` never
did, so no answer-generation signal existed and a slow answer was invisible past
`ASSEMBLE_CONTEXT`.

It was not a wiring gap. Generation deliberately does not happen inside the GraphRAG runtime:
`GraphRagRetrievalPolicy` pins `CONTEXT` rather than `ANSWER` so the application shell can
re-verify the whole evidence closure against the canonical ledger before anything reaches a
model. `AssistantService` therefore sits above `PermissionAwareKnowledgeSearch`, which is
engine-neutral and has a second implementation, `CanonicalHybridKnowledgeSearch`, selected by
`orgmemory.assistant.retrieval-engine`.

So emitting `Stage.GENERATE` from the assistant would either label canonical-engine turns as
GraphRAG stages, which is false, or thread a GraphRAG operation identifier through an
interface that has no such concept, which inverts the dependency that interface exists to
prevent.

The counterargument was recorded in
`docs/increments/active/2026-07-29-observability-pipeline/challenge-generation-telemetry.md`
and is the reason this sat open: `GraphRagEventSink` is not a telemetry convenience, it is
where the payload boundary of [0018](0018-telemetry-carries-counts-never-payload.md) is
enforced. Generation is the highest-risk stage in the system for payload leakage, because it
is where prompts and completions exist. A second telemetry path for exactly that stage is how
a structural guarantee decays into a conventional one.

Research on 2026-07-30 shrank the question. Spring AI's model observations are already live
and already emit generation duration, per-call token usage and `gen_ai.response.finish_reasons`
— it observes inside `ChatModel`, below `ChatModelPort`. Only time to first token remained,
and Spring AI cannot supply it because its observation for a streaming call covers the whole
stream.

## Decision

Assistant turns are observed on their own surface, and that surface carries the same
structural boundary rather than a promise to be careful.

- `AssistantTurnEvent` is a record whose compact constructor rejects everything but counts,
  durations, bounded enumerations and an organization identifier. Its only string component
  is a failure code matched against `[a-z0-9_]{1,64}`.
- `AssistantTurnObservationContext` is mutable, as a Micrometer context must be, but its
  setters accept only those types, and `DefaultAssistantTurnObservationConvention` reads the
  event rather than the context. A convention that could reach past the event could put a
  completion on a span; one that can only reach the event cannot.
- Correlation to retrieval and to the model call is by trace context. There is deliberately no
  request or conversation identifier: a string field for an identifier is a string field, and
  the first person in a hurry puts the question in it.
- `Stage.GENERATE` is removed. A value nothing can emit is the same defect class as an
  exporter that reports healthy while pushing to nowhere, which is the defect this increment
  opened to fix.

Time to first token is recorded as its own distribution,
`orgmemory.assistant.time_to_first_token`, by an `ObservationHandler` on the meter registry —
the shape Spring AI uses for `ChatModelMeterObservationHandler`, so the instrumented code
knows about `ObservationRegistry` and nothing about storage.

The measurement starts when the question arrives, not when the model is called. Retrieval is
permission-scoped — an OpenFGA batch check and a canonical ledger re-read — and the user waits
through it, so a measurement that excluded it would report a fast assistant while the person
watched a blank screen.

## Why not the OpenTelemetry name

The GenAI semantic conventions define `gen_ai.client.operation.time_to_first_chunk`, measured
from the model call. That is a different interval from this one: it excludes retrieval and this
does not. Publishing a turn-level measurement under the convention's name would misdescribe it
and would collide the day Spring AI emits the real one. Both are wanted — one isolates the
model, the other measures what the person waiting experiences — so this one keeps OrgMemory's
namespace and leaves the convention's name free.

## Rejected alternatives

**Emit `Stage.GENERATE` through `GraphRagEventSink`.** Keeps one surface and one boundary,
and requires either mislabelling canonical-engine turns or threading a GraphRAG identifier
through an engine-neutral interface. Rejected on both.

**A new sink port mirroring `GraphRagEventSink`, with its own Micrometer and OpenTelemetry
adapters.** The same structural guarantee, and it duplicates plumbing Micrometer already
provides — two adapters to write, name and test, and no automatic parenting under Spring AI's
own observations. Rejected as the cost of ignoring the framework.

**Micrometer observation with a free-form context.** The idiomatic default, and it drops the
guarantee: an `Observation.Context` holds whatever a caller puts in it. Rejected — the
combination above keeps the framework's plumbing and the record's constraint, which is the
thing that made this decision hard.

**Keep `Stage.GENERATE` in case the policy ever selects `ANSWER`.** Speculative. Re-add it
when a mode the product deliberately does not run becomes one it does.

## Not done

`CLAUDE.md` requires an independent architecture challenge before a domain-boundary decision
is implemented. The brief was written
(`docs/increments/active/2026-07-29-observability-pipeline/challenge-generation-telemetry.md`)
and no independent reviewer ran it; the counterargument above is the author's own. The project
owner directed the choice explicitly on 2026-07-30 after the alternatives and their trade-offs
were laid out, which is the escape `CLAUDE.md` provides when the configured reviewer is
unavailable. Recorded so the gap is visible rather than assumed closed.

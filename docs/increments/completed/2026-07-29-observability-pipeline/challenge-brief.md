# Architecture challenge: payload-free telemetry policy for OrgMemory

You are an independent architecture reviewer. Your job is to attack the proposal below,
not to validate it. `CLAUDE.md` in this repo requires an independent challenge before
decisions about authorization, persistence, or publication boundaries are implemented —
this is that challenge. Verify claims against the code yourself; do not take the summary
on trust. File paths are given so you can check.

## What OrgMemory is

A governed organizational memory layer. Its product promise is permission-aware retrieval:
every chunk of evidence is scoped by an OpenFGA-backed ACL per Knowledge Asset, and
retrieval re-verifies the full evidence closure against the canonical ledger before
anything reaches a model or a user. Multi-tenant, enterprise-facing.

## The rule under review

`docs/runbooks/graph-rag-production-hardening.md`, section "Payload-Free Tracing":

> The application allowlist is limited to operation and organization UUIDs, stage/outcome,
> monotonic duration, bounded input/output counts, an optional lowercase SHA-256
> model-route fingerprint, and a bounded machine failure code. Never add query, prompt,
> completion, evidence/chunk text, document title/URI, embedding values, actor identity,
> ACL subjects or exception messages. Spring AI prompt and completion observation logging
> is explicitly disabled in API and worker configuration.

Enforcement is structural, not by convention. See
`components/graph-rag-core/src/main/java/com/orgmemory/graphrag/observability/GraphRagEventSink.java`
— the `GraphRagEvent` record's compact constructor rejects anything outside the allowlist:
fingerprints must match `[0-9a-f]{64}`, `failureCode` must match `[a-z0-9_]{1,64}`, and
there is no field that can carry free text. The OpenTelemetry adapter beside it
(`integrations/graph-rag-observability/.../OpenTelemetryGraphRagEventSink.java`) can only
emit what the record allows.

## Evidence gathered from comparable systems

Read from source where marked, otherwise from vendor documentation.

| System | Prompt/completion leaves the process? | Mechanism |
|---|---|---|
| Dify | Yes, by default | Full input/output history logged; community issue #19345 asks for opt-out docs |
| Onyx (source read at `tmp/onyx`) | Yes, once a provider is configured | `backend/onyx/tracing/framework/span_data.py` — `GenerationSpanData` carries `input`/`output`; `langfuse_tracing_processor.py:178` sends both. `enable_masking=True` by default but `masking.py` only redacts `private_key` / `authorization: bearer` and truncates at 500k — prompt text survives. Provider config per tenant in DB; env-only on multi-tenant cloud |
| Sentry MCP (source read at `tmp/upstream-sentry-mcp-20260726`) | Yes | `sendDefaultPii: true` explicitly set, above Sentry's own `false` default. `packages/mcp-core/src/telem/sentry.ts` adds a `beforeSend` hook with pattern-based `SCRUB_PATTERNS` (API keys, bearer tokens), recursive to depth 20, and logs a warning whenever it actually scrubs something |
| LibreChat | Via proxy | Backend validates the session and strips app auth headers before forwarding telemetry |
| OpenTelemetry GenAI semconv | Opt-in, default off | Single env var `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` |
| Spring AI 2.0 | Opt-in, default off | `spring.ai.chat.observations.log-prompt` / `log-completion`, both `matchIfMissing = false` |
| RAGFlow | Yes, via Langfuse integration | Configured in API settings |
| **OrgMemory** | **Never** | Closed allowlist enforced by type |

No comparable system chose "never". OrgMemory is alone at that end.

## Observed operational cost

Production emitted `Failed to publish metrics to OTLP receiver (url=http://localhost:4318/v1/metrics)`
every minute for four days without anyone diagnosing it. Root cause was that
`spring-boot-starter-opentelemetry` transitively pulls `micrometer-registry-otlp`, whose
`OtlpMetricsExportAutoConfiguration` is opt-out and defaults to a localhost URL that
nothing was listening on. Contributing factor: the team's habit is that telemetry does
not say anything useful, partly a product of the policy above.

## The proposal

Replace the flat "never" with tiers:

- **Tier 0** — default, unchanged. Current allowlist, enforced by type. Add a payload-free
  counter for how often a redactor had to block something, so silent near-misses become
  visible (borrowed from Sentry's scrub-visibility idea).
- **Tier 1** — opt-in per deployment, default off. Exception class and message for
  *infrastructure* exceptions, passed through a pattern-based scrubber; hashed actor id.
- **Tier 2** — opt-in per organization, time-boxed, consent recorded. Query and completion
  text. Modelled on Onyx: per-tenant config in the database, reloaded without restart.
- **Never at any tier** — evidence/chunk text, document title/URI, ACL subjects,
  embedding values.

A reduced variant is also on the table: **Tier 0 + Tier 1 only**, keeping "never" for
query and completion. This takes the operational win without touching the governance
promise.

## The strongest counterargument already identified

An absolute rule is auditable in one line and cannot be misconfigured. Three tiers create
three states to audit, and one environment variable set wrong at Tier 2 leaks customer
content. For a product sold on governance, "cannot be wrong" has real value against
"is not wrong when configured correctly". The current design also enforces the rule in the
type system, which no amount of configuration discipline matches.

## What to answer

1. Is the operational-cost argument for Tier 1 actually sound, or is the four-day
   undiagnosed warning better explained by something other than the payload policy?
   Check whether infrastructure exception detail was genuinely unavailable, or merely
   unused — `apps/api/src/main/resources/application-prod.yml` sets log levels, and the
   warning did reach stdout.
2. Does the ACL-scoped-evidence distinction actually justify diverging from every
   comparable system, or is that reasoning motivated? Attack it.
3. If tiering is adopted, what is the failure mode you would most expect in practice, and
   what structural control (not process) prevents it?
4. Is there a fourth option neither variant covers — for example, keeping "never" for the
   sink while allowing detail on a separate egress path with different controls?
5. Which variant would you ship: all three tiers, Tier 0+1, or unchanged? Commit to one.

Answer in prose, be specific about file paths and code you checked, and state plainly
where you think the summary above is wrong or overstated. Disagreement is the useful
output here.

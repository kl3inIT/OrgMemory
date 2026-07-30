# 0018 — Telemetry carries counts, never payload

Status: accepted, 2026-07-30.

## Context

OrgMemory's product promise is permission-aware retrieval: every chunk of
evidence is scoped by an OpenFGA-backed ACL, and retrieval re-verifies the full
evidence closure before anything reaches a model. Telemetry crosses that
boundary in the one direction the authorization model does not govern — outward,
to a collector that has no notion of who may read what.

The observability increment surveyed comparable systems. Dify logs full input and
output by default. Onyx, read from source, sends prompt and completion to
Langfuse once a provider is configured; its masking redacts credentials and
truncates at 500 kB, so prompt text survives. Sentry's MCP server sets
`sendDefaultPii: true` above Sentry's own default. Only the OpenTelemetry GenAI
semantic conventions default to off, behind a single environment variable.

So there is no industry position to defer to here, only an absence of
deliberation.

## Decision

Telemetry may carry counts, identifiers, bounded enumerations and hashes. It may
never carry the text those measure.

Enforcement is structural rather than conventional:

- `GraphRagEvent`'s compact constructor rejects anything outside the allowlist.
  Fingerprints must match `[0-9a-f]{64}`, failure codes must match
  `[a-z0-9_]{1,64}`, and no component can hold free text. An adapter can only
  emit what the record permits.
- `ExceptionSanitizingSpanExporter` is the last gate before egress. It keeps
  `exception.type` alone from each event and clears the status description,
  because Micrometer's bridge copies `throwable.getMessage()` into both and an
  OrgMemory exception can be raised holding query, evidence or provider-response
  text. It applies to every span in the process, not only GraphRAG spans.
- `ProviderLoggingBoundaryVerifier` fails startup when the OpenAI or Anthropic
  client packages are left at WARN, because those libraries concatenate the
  prompt and the response into messages of their own that no Spring AI setting
  reaches.
- `WholeExportAllowlistTests` walks the entire exported span — name, attributes,
  status, events, event attributes, instrumentation scope and resource — against
  the allowlist on both a success and a failure path.

Token counts are explicitly permitted. A number cannot reconstruct the text it
counted, and the alternative is a deployment that cannot see what it spends.

## Why not configuration

Configuration was rejected as the enforcement mechanism, though it remains the
default. A payload boundary expressed only as a setting is undone by anything
that outranks the setting: `LOGGING_LEVEL_*` environment variables, system
properties and logback configuration all outrank `application.yml`. This was not
hypothetical — the first implementation of the provider pin claimed in its own
commit message to be non-overridable, and was wrong. The verifier exists because
config cannot enforce what config can undo.

## Rejected alternative

Capture prompts and completions behind an opt-in flag, defaulting to off, as the
OpenTelemetry GenAI conventions do. Rejected: the flag becomes the whole control,
and the failure mode is one environment variable in one deployment, discovered
after the fact. A boundary the code cannot express is a boundary that holds only
until someone is in a hurry. If governed content capture is ever a product
requirement, it earns its own design with its own authorization model rather
than arriving as a telemetry setting.

## Consequences

Debugging from telemetry alone is harder. A failed retrieval yields a stage, an
outcome, a failure code, a duration and hashes — enough to find the request, not
enough to see what was asked. Reproduction requires the application's own
authorization-checked paths, which is the intended cost.

Anything that adds a field to the telemetry boundary changes an allowlist that
is asserted in two places, and should expect to update both.

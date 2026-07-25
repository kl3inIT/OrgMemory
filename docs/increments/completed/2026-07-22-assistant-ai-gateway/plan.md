# Assistant AI Gateway Plan

## Completion Record

Completed by the secure GraphRAG Assistant delivery merged to `main` in PR #42.
The provider-neutral gateway, verified-evidence model boundary, AI SDK stream,
source preview, timeout/error framing, and browser coverage now live in current
architecture, specs, tests, and code.

## Foundation

- [x] Add provider-neutral AI workload, route, and streaming chat contracts.
- [x] Add immutable typed gateway configuration and the Spring AI
  OpenAI-compatible adapter.
- [x] Validate configured capability and route before a provider call.

## Assistant

- [x] Move the Assistant use case out of the knowledge delivery package.
- [x] Retrieve and canonically verify evidence before starting model streaming.
- [x] Emit only verified sources through AI SDK UI Message Stream v1.
- [x] Replace the synchronous Assistant web client with `useChat` and
  `DefaultChatTransport`.

## Verification

- [x] Test that empty/denied evidence never invokes the model.
- [x] Test route validation and secret-safe configuration rendering.
- [x] Pin exact normal, error, heartbeat, and timeout stream frames.
- [x] Run focused tests, Gradle clean test, web lint/typecheck/build, and a real
  browser Assistant turn.

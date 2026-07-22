# Assistant AI Gateway Plan

## Foundation

- [ ] Add provider-neutral AI workload, route, and streaming chat contracts.
- [ ] Add immutable typed gateway configuration and the Spring AI
  OpenAI-compatible adapter.
- [ ] Validate configured capability and route before a provider call.

## Assistant

- [ ] Move the Assistant use case out of the knowledge delivery package.
- [ ] Retrieve and canonically verify evidence before starting model streaming.
- [ ] Emit only verified sources through AI SDK UI Message Stream v1.
- [ ] Replace the synchronous Assistant web client with `useChat` and
  `DefaultChatTransport`.

## Verification

- [ ] Test that empty/denied evidence never invokes the model.
- [ ] Test route validation and secret-safe configuration rendering.
- [ ] Pin exact normal, error, heartbeat, and timeout stream frames.
- [ ] Run focused tests, Gradle clean test, web lint/typecheck/build, and a real
  browser Assistant turn.

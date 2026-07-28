# Multi-provider Model Control Plane Plan

## Status

Implementation active on `feat/multi-provider-model-settings` from
`origin/main` at `d7ca979`.

## 1. Contract and Persistence

- [x] Add `can_manage_ai` to the OpenFGA organization model and admin guard.
- [x] Add organization-scoped gateway profile, encrypted credential, and route
  override entities/repositories/services.
- [x] Add Flyway tenant constraints and focused persistence tests.
- [x] Make AI route and chat execution carry organization identity.
- [x] Preserve deployment routes as an explicit default, not provider failover.

## 2. Provider Adapters

- [x] Generalize the Spring AI provider to dispatch by protocol.
- [x] Keep OpenAI, 9Router, OpenRouter, LiteLLM, Ollama, and custom endpoints on
  the verified OpenAI-compatible adapter.
- [x] Add native Anthropic Messages support using Spring AI 2.0.0.
- [x] Wire `ObservationRegistry` and `MeterRegistry` into dynamic models.
- [x] Add versioned bounded client caching and fail-closed behavior.
- [x] Add bounded connection test/model discovery with Boot 4.1 HTTP hardening.

## 3. Admin API

- [x] List implemented provider presets and connected profiles.
- [x] Create/update/disable profiles without returning credentials.
- [x] Set/rotate credentials with redacted request objects.
- [x] Test transient and stored credentials and list bounded model catalogs.
- [x] Read/update/clear Assistant and prompt-execution routes.
- [x] Report immutable embedding settings.
- [x] Regenerate OpenAPI and the typed web client.

## 4. Admin UI

- [x] Add AI / Language Models and Knowledge / Index Settings navigation.
- [x] Build the default-model selector and connected-profile list.
- [x] Build grouped provider cards and a connection dialog.
- [x] Support test, discovered/manual model selection, save, and safe errors.
- [x] Build the read-only active embedding profile page.
- [x] Add focused component/browser tests.

## 5. Verification and Delivery

- [x] Run edited-file static analysis.
- [x] Run focused core, integration, API, OpenFGA, and web tests.
- [x] Run affected backend compile/test and frontend lint/typecheck/build.
- [x] Reconcile architecture, specs, test maps, and ADR 0006 consequences.
- [ ] Add `verification.md`, archive this increment, and update the roadmap.
- [ ] Commit, push, open a PR, address CodeRabbit/review feedback, wait for green
  CI, merge to `main`, and verify the remote merge.

# Business Exception Migration Plan

- [x] Inventory API controllers and service/coordinator
  `IllegalArgumentException` paths.
- [x] Define the migration boundary and explicit invariant exclusions.
- [x] Add reusable transport-neutral business failure types where a
  domain-specific class would add no behavior.
- [x] Migrate expected validation, conflict, not-found, and unavailable paths.
- [x] Replace controller `ResponseStatusException` validation with the shared
  RFC 9457 contract.
- [x] Remove the global `IllegalArgumentException` compatibility translator.
- [x] Add focused service and API contract regression tests.
- [x] Run static analysis, `:core:test`, `:apps:api:test`, and terminating
  `clean test`.
- [x] Consolidate current docs and move this increment to `completed`.

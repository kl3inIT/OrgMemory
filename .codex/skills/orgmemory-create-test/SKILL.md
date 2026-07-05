---
name: orgmemory-create-test
description: Create or update focused tests for OrgMemory backend services, REST controllers, Flyway/JPA behavior, Spring Modulith boundaries, or frontend flows. Use when adding business behavior, changing approval/usage/versioning rules, or fixing a regression.
---

# OrgMemory Create Test

Add the smallest test that protects the behavior being changed.

## Backend

Prefer service tests for domain behavior and controller tests for HTTP contract
behavior.

Test these MVP rules when touched:

- creating a capability asset creates version 1,
- submit/approve/reject/deprecate changes status and records an approval event,
- usage events increment usage count,
- approved assets remain searchable,
- missing backup owners remain visible as handover risk,
- JPA mappings match Flyway migrations.

Run:

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :apps:api:test
```

## Frontend

For now, prefer type-safe component logic and manual Playwright verification over
heavy test scaffolding. Add frontend tests once there are stable flows worth
protecting.

## Test Quality

- Do not assert implementation details when domain behavior is enough.
- Use seed-like examples from OrgMemory: Sales follow-up assets, review queue,
  onboarding recommendations, and offboarding handover.
- Keep tests deterministic; no live LLM calls in tests.

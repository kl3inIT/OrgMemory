# Asset Registry POC Verification

Date: 2026-07-26

## Golden business flow

`AssetRegistryIntegrationTests#goldenPocTransfersAReleasedSupportCapabilityToASecondUser`
uses the synthetic files under `demo/fixtures/asset-registry` and proves:

1. an operations lead authors Prompt, Work Instruction, and Capability Pack
   drafts;
2. an independent reviewer approves each immutable digest;
3. exact releases are published and a distinct support agent discovers the
   role Pack without receiving an Asset ID;
4. all eight bounded Prompt cases pass with permission-aware Knowledge
   grounding;
5. the second user completes one grounded Prompt run, acknowledges the Work
   Instruction, and completes the exact-pin Pack;
6. Prompt `2.0.0` does not rewrite the Pack's `1.0.0` pin;
7. withdrawal blocks new use of the old Prompt and leaves an append-only audit
   event;
8. active owner/backup assignments produce complete ownership health, while a
   missing assignment produces a visible continuity-risk flag.

`asset-registry-golden-poc.spec.ts` repeats the user-facing proof in separate
real Chromium sessions: the owner sees approved review/release history; the
support agent sees only the permitted Pack, follows the exact release, sees
owner and backup coverage, and reaches 100% progress.

`mcp-connect.spec.ts` proves that an authenticated user can discover the
canonical MCP URL and follow generic Claude, Codex, or compatible-client
onboarding without receiving a shared client secret.

## Deterministic POC metrics

These values are technical acceptance evidence, not customer benchmarks.

| Metric | POC evidence |
| --- | --- |
| Time to first correct task | start/completion timestamps and Prompt duration are captured; no adoption benchmark claimed |
| First-time-right | 1/1 scripted golden task |
| Second-user reuse | one distinct non-author support agent |
| View-to-use | 1/1 in the scripted browser flow |
| Evaluation pass | 8/8 bounded ticket cases |
| Reviewer correction | 0/4 golden submissions required a correction; the workflow still supports request-changes |
| Owner coverage | 3/3 active golden registry Assets have an owner and backup after handover |
| Unauthorized metadata leakage | zero; REST, Assistant, Pack, and MCP denial tests remain opaque |

## Gate evidence

- `.\gradlew.bat --no-daemon clean test` — passed, 97 actionable tasks.
- OpenFGA CLI `v0.7.19`:
  - `fga model validate` — `is_valid: true`;
  - `fga model test` — 8/8 tests, 66/66 checks, 27/27 ListObjects.
- `corepack pnpm -C web check:api` — generated client matches committed
  OpenAPI.
- `corepack pnpm -C web typecheck` — passed.
- `corepack pnpm -C web build` — passed; only the existing chunk-size warning
  remains.
- `corepack pnpm -C web test:e2e` — 8/8 Chromium tests passed.
- `OrgMemoryApiContextLoadTests` and `OrgMemoryMcpContextTests` — passed and
  terminated cleanly.
- `test-web-forwarded-port.sh` — the public RFC 9728 discovery path is proxied
  to MCP rather than falling through to the SPA.
- `test-keycloak-mcp-onboarding.sh` — the checked-in Keycloak 26.7 image:
  - imports the minimal production realm and applies the migration twice;
  - advertises DCR without the currently incompatible CIMD path;
  - registers a Claude-shaped public, consent-required, PKCE S256 client and
    reaches the login page with no direct grant, service account, or full scope;
  - rejects an untrusted redirect and `assets:write` with `403`;
  - deletes the dynamic verification client.
- Keycloak 26.7 persisted-state upgrade rehearsal — passed:
  - an old CIMD-enabled image created the retired policy/profile in a persistent
    Keycloak database;
  - the new CIMD-disabled image started against that unchanged database;
  - the migration removed the retired entries without replacing unrelated
    realm policy;
  - a Claude-shaped DCR client then reached the login page with HTTP `200` and
    its temporary registration was deleted.
- Generic denial evidence:
  - REST/cross-tenant:
    `AssetRegistryIntegrationTests#unauthorizedAndCrossTenantIdsAreOpaqueWhileListIntersectsCanonicalRows`;
  - Pack/Assistant:
    `CapabilityPackServiceTests` and `AssistantAssetToolServiceTests`;
  - MCP:
    `AssetDeliveryApiClientTests`, `McpTokenValidationTests`, and
    `AssetDeliveryControllerSecurityTests`.

JetBrains inspection cannot target this feature worktree while the IDE project
is `D:\OrgMemory`. Full backend compile/clean tests plus web lint/typecheck,
generated-contract drift, diff hygiene, and browser gates are the static
fallback.

## Scope statement

The repository POC is technically complete. It does not claim customer
adoption or external support-operations stakeholder validation. Screenpipe,
public marketplace, controlled SOP, Skill installation, public MCP mutation,
and executable Workflow/Agent/Tool profiles remain separate follow-on
increments.

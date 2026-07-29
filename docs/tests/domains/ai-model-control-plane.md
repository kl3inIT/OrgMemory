# AI Model Control Plane Coverage

Source: `core/src/test/java/com/orgmemory/core/ai`,
`integrations/ai-model-gateways/src/test`,
`apps/api/src/test/java/com/orgmemory/api/admin`,
`integrations/authorization-openfga/src/test/openfga`,
`apps/web/src/features/admin/components/provider-logo.test.tsx`,
`apps/web/test/e2e/admin-language-models.spec.ts`, and the admin web build.

Reconciled: `2026-07-29-ai-provider-setup-ui (2bcf082)`.

| Behavior | Evidence | Status |
| --- | --- | --- |
| Only organization administrators receive `can_manage_ai` | OpenFGA `store.fga.yaml`, `PermissionsAdminIntegrationTests` | covered |
| Production writes and pins a changed OpenFGA model before application recreation, skips identical bytes, and restores the previous pin on failed canary | `test-deploy-openfga-model-rollout.sh` | covered |
| Secrets are encrypted and absent from views/log rendering | `AiGatewayAdministrationServiceTests#storesOnlyCiphertextAndKeepsCredentialsOutOfViewsAndLogs` | covered |
| Cross-tenant profile IDs are opaque and cannot rotate credentials | `AiGatewayAdministrationServiceTests#aProfileIdFromAnotherOrganizationIsOpaqueAndCannotRotateASecret` | covered |
| Profile, credential, and route actor FKs cannot cross tenant boundaries | `PermissionsAdminIntegrationTests#aiControlPlaneActorReferencesCannotCrossTenantBoundaries` | covered |
| Credential rotation invalidates runtime model caches | `AiGatewayAdministrationServiceTests#credentialRotationAlwaysAdvancesTheRuntimeCacheRevision` | covered |
| Chat model dispatch selects the factory matching the route protocol and fails closed for missing or duplicate factories | `SpringAiChatModelFactoriesTests` | covered |
| Updating metadata and rotating a credential is one service transaction | `AiGatewayAdministrationServiceTests#metadataAndCredentialUpdateShareOneServiceTransaction` | covered |
| Preset/category/protocol combinations cannot be relabeled | `AiGatewayAdministrationServiceTests#providerPresetCannotBeRelabeledAsAnotherProtocolOrCategory` | covered |
| Custom endpoints require exact operator allowlisting | `ConfiguredAiGatewayEndpointPolicyTests` | covered |
| Explicit organization routes fail closed | `AiGatewayPropertiesTests#anExplicitOrganizationRouteFailsClosedWhenItsGatewayIsUnavailable` | covered |
| A colliding organization gateway key cannot replace a deployment default | `AiGatewayPropertiesTests#aCollidingOrganizationGatewayKeyDoesNotReplaceTheDeploymentDefault` | covered |
| OpenAPI and generated TypeScript client match the controller | `OpenApiContractTests`, web generated API drift gate | covered |
| Language Models renders verified provider marks, opens the structured setup flow, discovers live models, and can restore a deployment route | `provider-logo.test.tsx`, `admin-language-models.spec.ts` | covered |
| Read-only Index Settings compiles as a production route | web lint, typecheck, and build | covered |
| Live provider credentials/model responses | no deterministic CI credential | operator verification required |

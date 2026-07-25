# Asset Registry Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| Prompt, Work Instruction, and Pack schemas reject invalid payloads | `AssetProfileValidationTests` | covered |
| Prompt rendering is deterministic and validates typed variables | `PromptTemplateRendererTests` | covered |
| Inserted variables and grounding remain untrusted data | `PromptTemplateRendererTests`, `PromptExecutionServiceTests` | covered |
| Prompt output contract and bounded evaluations expose pass/failure | `PromptExecutionServiceTests`, `AssetRegistryIntegrationTests` | covered |
| Prompt runs pin exact release, digest, and model route | `AssetRegistryIntegrationTests` | covered |
| Raw sensitive values and raw output are absent from default persistence | `AssetRegistryIntegrationTests` | covered |
| Withdrawn Prompt releases cannot start new runs | `AssetRegistryIntegrationTests` | covered |
| Prompt provider tests run without network access | `PromptExecutionServiceTests` | covered |
| Work Instruction acknowledgement is actor-derived and idempotent | `AssetRegistryIntegrationTests` | covered |
| Pack items preserve order and exact release/version pins | `CapabilityPackServiceTests`, `AssetRegistryIntegrationTests` | covered |
| Every Pack component is authorized independently | `CapabilityPackServiceTests` | covered |
| Denied Pack component metadata and count remain opaque | `CapabilityPackServiceTests` | covered |
| Knowledge catalog and Prompt grounding use canonical authorization | `KnowledgeCatalogServiceTests`, `PromptExecutionServiceTests` | covered |
| Pack progress is actor-derived and idempotent | `AssetRegistryIntegrationTests` | covered |
| Replacement releases do not mutate existing Pack pins | `AssetRegistryIntegrationTests` | covered |

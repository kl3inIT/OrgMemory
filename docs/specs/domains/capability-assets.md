# Capability Assets Spec

## Current Behavior

Capability Assets store reusable AI work with title, summary, type, business
context, tool/model metadata, owner, backup owner, status, visibility, risk,
versioned content, usage events, and approval events. The lifecycle supports
draft, review, approval/rejection, deprecation, reuse tracking, and backup-owner
assignment.

Current enums are:

- `AssetType`: `PROMPT_TEMPLATE`, `WORKFLOW_AUTOMATION`, `AI_AGENT`,
  `KNOWLEDGE_BOT`, `ANALYTICS_BRIEF`, `CONTENT_GENERATOR`, `DATA_EXTRACTION`,
  `EVALUATION_CHECKLIST`, `PLAYBOOK`, `HANDOVER_PACK`,
  `GOVERNANCE_GUARDRAIL`, `COPILOT`.
- `AssetStatus`: `DRAFT`, `IN_REVIEW`, `APPROVED`, `REJECTED`, `DEPRECATED`.
- `AssetVisibility`: `PRIVATE`, `TEAM`, `ORGANIZATION`.
- `RiskLevel`: `LOW`, `MEDIUM`, `HIGH`.

Entity transition methods currently assign status directly; they do not enforce
a state-transition graph. Authorization is enforced in the service/API layer.
An asset-quality contract (clear purpose, inputs/outputs, workflow/prompt,
owner/backup owner, examples, limitations, visibility, and review evidence) is
product intent, not fully validated by the current entity.

Registry reads and mutations derive organization/current actor on the server and
apply role, ownership, department, visibility, and status rules. Ask Memory and
the relational graph currently use registry data, not the knowledge retrieval
slice.

Collection reads resolve persistent visibility with one OpenFGA `ListObjects`
request, then batch-check remaining assets with their resource-specific
contextual ownership/visibility tuples. Usage totals are loaded in one aggregate
query. An indeterminate OpenFGA set operation contributes no authorization.

## Source Modules

- `core.capability`
- `apps/api.capability`
- current `web` registry/review/detail/create features

## Related Decisions

- [0002](../../decisions/0002-one-domain-three-deployables.md)
- [0007](../../decisions/0007-replace-the-prototype-web-experience.md)

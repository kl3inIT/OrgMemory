# L1 Support Asset Registry Golden POC

This synthetic fixture proves the browser-native, governed reuse path without
customer or employee data.

Stable coordinates:

- Knowledge: `support.sla-and-escalation@1`
- Work Instruction: `support.classify-and-respond@1.0.0`
- Prompt Template: `support.triage-customer-ticket@1.0.0`
- Capability Pack: `support.l1-onboarding@1.0.0`
- Evaluation rubric: `support.triage-quality@1`

Files:

- `support-sla-and-escalation.md` is the permission-aware grounding source.
- `prompt-template.json` is the released Prompt payload with eight bounded
  evaluation cases.
- `work-instruction.json` is the released task procedure.
- `capability-pack-template.json` is resolved with exact release UUIDs by the
  golden integration test.
- `quality-checklist.json` is the human verification checklist.
- `mock-tickets.json` fixes expected classification, SLA, escalation, and
  citation behavior.
- `success-metrics.json` defines the POC metric formulas and thresholds.

The fixture intentionally contains no executable Skill, Tool, Agent, public
marketplace metadata, Screenpipe event, or MCP mutation.

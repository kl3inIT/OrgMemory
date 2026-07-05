import type { AssetType, CapabilityAsset, RiskLevel } from "@/lib/api"

export type DraftForm = {
  title: string
  summary: string
  assetType: AssetType
  useCase: string
  businessProcess: string
  aiTool: string
  tagNames: string
  promptTemplate: string
  workflowStepsJson: string
  inputSchemaJson: string
  outputSchemaJson: string
  exampleInput: string
  exampleOutput: string
  riskLevel: RiskLevel
}

export const demo = {
  organizationId: "11111111-1111-1111-1111-111111111111",
  salesDepartmentId: "22222222-2222-2222-2222-222222222222",
  ownerUserId: "44444444-4444-4444-4444-444444444444",
  backupOwnerUserId: "55555555-5555-5555-5555-555555555555",
}

export const initialRawCapture = `After each B2B product demo, paste call notes into Claude.
It writes a concise follow-up email, extracts promised next steps, flags customer concerns, and gives the sales rep a clean handoff summary for CRM.
The workflow touches customer context but no payment data.`

export const initialForm: DraftForm = {
  title: "Post-demo follow-up email",
  summary: "Generates a concise follow-up email after a B2B product demo.",
  assetType: "CONTENT_GENERATOR",
  useCase: "Sales follow-up",
  businessProcess: "Sales",
  aiTool: "Claude",
  tagNames: "sales, follow-up, email",
  promptTemplate:
    "Use these demo notes: {{notes}}. Write a short follow-up email, list promised next steps, and flag customer concerns.",
  workflowStepsJson:
    '[{"name":"Paste demo notes"},{"name":"Generate email and action list"},{"name":"Sales rep reviews before sending"}]',
  inputSchemaJson: '{"notes":"string","account":"string"}',
  outputSchemaJson: '{"email":"string","nextSteps":"string[]","risks":"string[]"}',
  exampleInput: "The buyer asked about onboarding time and requested a security checklist.",
  exampleOutput: "Draft email, next steps, and concerns ready for rep review.",
  riskLevel: "MEDIUM",
}

export function buildMetrics(assets: CapabilityAsset[]) {
  return {
    total: assets.length,
    approved: assets.filter((asset) => asset.status === "APPROVED").length,
    inReview: assets.filter((asset) => asset.status === "IN_REVIEW").length,
    missingBackup: assets.filter((asset) => !asset.backupOwnerUserId).length,
    usage: assets.reduce((sum, asset) => sum + asset.usageCount, 0),
  }
}

import type { AssetType } from "@/lib/api"

export const assetTypes: AssetType[] = [
  "PROMPT_TEMPLATE",
  "WORKFLOW_AUTOMATION",
  "AI_AGENT",
  "KNOWLEDGE_BOT",
  "ANALYTICS_BRIEF",
  "CONTENT_GENERATOR",
  "DATA_EXTRACTION",
  "EVALUATION_CHECKLIST",
  "PLAYBOOK",
  "HANDOVER_PACK",
  "GOVERNANCE_GUARDRAIL",
  "COPILOT",
]

export const assetTypeLabels: Record<AssetType, string> = {
  PROMPT_TEMPLATE: "Prompt Template",
  WORKFLOW_AUTOMATION: "Workflow Automation",
  AI_AGENT: "AI Agent",
  KNOWLEDGE_BOT: "Knowledge Bot",
  ANALYTICS_BRIEF: "Analytics Brief",
  CONTENT_GENERATOR: "Content Generator",
  DATA_EXTRACTION: "Data Extraction",
  EVALUATION_CHECKLIST: "Evaluation Checklist",
  PLAYBOOK: "Playbook",
  HANDOVER_PACK: "Handover Pack",
  GOVERNANCE_GUARDRAIL: "Governance Guardrail",
  COPILOT: "Copilot",
}

export function formatAssetType(assetType?: AssetType | null) {
  return assetType ? assetTypeLabels[assetType] : "Workflow Automation"
}

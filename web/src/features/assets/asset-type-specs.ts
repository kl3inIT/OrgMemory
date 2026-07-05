import type { AssetType } from "@/lib/api"
import type { DraftForm } from "@/features/assets/demo-data"
import { formatAssetType } from "@/features/assets/asset-type"

export type AssetTypeSpec = {
  type: AssetType
  departmentName: string
  captureLabel: string
  contentLabel: string
  description: string
  requiredInputs: string[]
  expectedOutputs: string[]
  rawCapture: string
  template: DraftForm
}

export const assetTypeSpecs: Record<AssetType, AssetTypeSpec> = {
  PROMPT_TEMPLATE: {
    type: "PROMPT_TEMPLATE",
    departmentName: "Operations",
    captureLabel: "Raw decision or writing pattern",
    contentLabel: "Reusable Prompt Template",
    description: "A reusable prompt with variables, tone rules, and output shape.",
    requiredInputs: ["Source notes", "Audience", "Decision context"],
    expectedOutputs: ["Draft artifact", "Assumptions", "Decision owner"],
    rawCapture:
      "We often turn rough operating notes into an executive decision memo. The prompt should compare options, state risks, recommend one path, and assign an owner.",
    template: {
      title: "Executive Decision Memo Prompt",
      summary: "Turns rough operating notes into an executive-ready decision memo with options, risks, recommendation, and owner.",
      assetType: "PROMPT_TEMPLATE",
      useCase: "Executive decision support",
      businessProcess: "Operations planning",
      aiTool: "Claude",
      tagNames: "prompt-template, decision-memo, leadership",
      promptTemplate:
        "Turn {{source_notes}} into an executive decision memo for {{audience}}. Include options, tradeoffs, recommendation, owner, and due date.",
      workflowStepsJson:
        '[{"name":"Collect source notes"},{"name":"Identify decision options"},{"name":"Compare tradeoffs"},{"name":"Draft recommendation"},{"name":"Assign owner"}]',
      inputSchemaJson: '{"source_notes":"string","audience":"string","decision_context":"string","deadline":"string"}',
      outputSchemaJson: '{"memo":"markdown","options":"array","recommendation":"string","owner":"string"}',
      exampleInput: "Leadership notes about packaging changes and support cost risk.",
      exampleOutput: "Decision memo with three options, tradeoffs, and recommended next action.",
      riskLevel: "MEDIUM",
    },
  },
  WORKFLOW_AUTOMATION: {
    type: "WORKFLOW_AUTOMATION",
    departmentName: "Customer Success",
    captureLabel: "Raw repeatable workflow",
    contentLabel: "Workflow Instructions",
    description: "A multi-step process that transforms business inputs into a reviewed output.",
    requiredInputs: ["Source data", "Time range", "Business unit"],
    expectedOutputs: ["Workflow result", "Action list", "Review notes"],
    rawCapture:
      "Growth ops uses n8n to pull new demo requests from HubSpot, enrich company data, summarize intent with AI, route qualified leads to Slack, and create follow-up tasks.",
    template: {
      title: "Inbound Lead Enrichment Automation",
      summary: "Enriches inbound demo requests, summarizes buying intent, routes qualified leads, and creates CRM follow-up tasks.",
      assetType: "WORKFLOW_AUTOMATION",
      useCase: "Inbound lead routing",
      businessProcess: "Revenue operations",
      aiTool: "n8n",
      tagNames: "workflow, n8n, lead-enrichment, revops",
      promptTemplate:
        "Summarize {{lead_context}} and classify buying intent, segment, urgency, and recommended next action for the revenue team.",
      workflowStepsJson:
        '[{"name":"Trigger from HubSpot form"},{"name":"Enrich company profile"},{"name":"Classify buying intent"},{"name":"Route to Slack"},{"name":"Create CRM task"}]',
      inputSchemaJson: '{"lead_context":"object","company_domain":"string","source_form":"string","routing_rules":"array"}',
      outputSchemaJson: '{"intent":"string","segment":"string","urgency":"string","recommended_action":"string","crm_task":"object"}',
      exampleInput: "Demo request from a 400-person SaaS company mentioning SOC2 and migration timeline.",
      exampleOutput: "High-intent enterprise lead routed to AE with enriched account summary.",
      riskLevel: "MEDIUM",
    },
  },
  AI_AGENT: {
    type: "AI_AGENT",
    departmentName: "Sales",
    captureLabel: "Agent operating brief",
    contentLabel: "Agent Instructions",
    description: "A task-oriented agent with goal, tools, decision rules, and escalation boundaries.",
    requiredInputs: ["Goal", "Tools/data sources", "Escalation rules"],
    expectedOutputs: ["Recommended action", "Reasoning summary", "Escalation path"],
    rawCapture:
      "Engineering wants a Claude Code/Codex agent recipe for small repository changes: read issue, inspect files, edit scoped code, run tests, and produce a reviewable summary.",
    template: {
      title: "Repository Change Agent Recipe",
      summary: "Guides Claude Code or Codex through scoped repo changes with file inspection, patching, tests, and handoff summary.",
      assetType: "AI_AGENT",
      useCase: "AI-assisted software delivery",
      businessProcess: "Engineering delivery",
      aiTool: "Claude Code",
      tagNames: "ai-agent, codex, claude-code, engineering",
      promptTemplate:
        "Act as a repository change agent. Use {{issue_context}}, inspect relevant files, make scoped edits, run {{verification_commands}}, and summarize risks.",
      workflowStepsJson:
        '[{"name":"Read issue and repo context"},{"name":"Inspect relevant files"},{"name":"Apply scoped patch"},{"name":"Run verification"},{"name":"Write handoff summary"}]',
      inputSchemaJson: '{"issue_context":"string","repo_path":"string","verification_commands":"array","constraints":"string"}',
      outputSchemaJson: '{"changed_files":"array","summary":"string","tests":"array","risks":"array"}',
      exampleInput: "Bug report for a React table filter and repo path with pnpm typecheck command.",
      exampleOutput: "Patch, passing checks, changed-files summary, and residual risks.",
      riskLevel: "HIGH",
    },
  },
  KNOWLEDGE_BOT: {
    type: "KNOWLEDGE_BOT",
    departmentName: "People Operations",
    captureLabel: "Knowledge source and answer policy",
    contentLabel: "Knowledge Bot Behavior",
    description: "A source-grounded Q&A capability over approved internal knowledge.",
    requiredInputs: ["Question", "Approved sources", "Escalation owner"],
    expectedOutputs: ["Grounded answer", "Sources", "Confidence"],
    rawCapture:
      "Teams ask repeated questions about product limits, pricing exceptions, implementation SOPs, and internal policies. The bot should answer from approved sources and cite them.",
    template: {
      title: "Enterprise Knowledge RAG Assistant",
      summary: "Answers internal questions from approved product, policy, pricing, and SOP sources with citations and escalation guidance.",
      assetType: "KNOWLEDGE_BOT",
      useCase: "Enterprise knowledge retrieval",
      businessProcess: "Knowledge operations",
      aiTool: "LlamaIndex",
      tagNames: "knowledge-bot, rag, enterprise-knowledge",
      promptTemplate:
        "Answer {{question}} using only {{approved_sources}}. Cite sources, state confidence, and route uncertain cases to {{escalation_owner}}.",
      workflowStepsJson:
        '[{"name":"Receive question"},{"name":"Retrieve approved sources"},{"name":"Draft cited answer"},{"name":"Assess confidence"},{"name":"Escalate uncertain cases"}]',
      inputSchemaJson: '{"question":"string","approved_sources":"array","escalation_owner":"string"}',
      outputSchemaJson: '{"answer":"string","sources":"array","confidence":"string","escalation":"string"}',
      exampleInput: "AE asks whether a customer can get an implementation exception.",
      exampleOutput: "Cited answer from pricing policy and implementation SOP with confidence.",
      riskLevel: "MEDIUM",
    },
  },
  ANALYTICS_BRIEF: {
    type: "ANALYTICS_BRIEF",
    departmentName: "Product",
    captureLabel: "Metrics and analysis cadence",
    contentLabel: "Analytics Brief Instructions",
    description: "A recurring analysis package that explains trends, anomalies, and decisions.",
    requiredInputs: ["Metric export", "Period", "Segment"],
    expectedOutputs: ["Executive summary", "Drivers", "Recommended decisions"],
    rawCapture:
      "Product teams need a monthly adoption brief from feature usage, expansion signals, churn risk, and qualitative notes.",
    template: {
      title: "Product Adoption Insights Brief",
      summary: "Summarizes feature adoption, usage movement, churn signals, and recommended product follow-up decisions.",
      assetType: "ANALYTICS_BRIEF",
      useCase: "Product adoption reporting",
      businessProcess: "Product analytics",
      aiTool: "Claude",
      tagNames: "analytics-brief, product, adoption",
      promptTemplate:
        "Create an adoption brief from {{usage_export}}, {{segment}}, and {{qualitative_notes}}. Explain trend drivers and recommended decisions.",
      workflowStepsJson:
        '[{"name":"Import usage data"},{"name":"Detect movement"},{"name":"Find drivers"},{"name":"Summarize risks"},{"name":"Recommend decisions"}]',
      inputSchemaJson: '{"usage_export":"string","period":"string","segment":"string","qualitative_notes":"string"}',
      outputSchemaJson: '{"brief":"markdown","drivers":"array","risks":"array","recommendations":"array"}',
      exampleInput: "Feature usage export for admins in the last 30 days.",
      exampleOutput: "Adoption brief with top drivers and recommended product actions.",
      riskLevel: "MEDIUM",
    },
  },
  CONTENT_GENERATOR: {
    type: "CONTENT_GENERATOR",
    departmentName: "Marketing",
    captureLabel: "Content brief",
    contentLabel: "Content Generation Instructions",
    description: "A governed generator for business content, decks, emails, or knowledge articles.",
    requiredInputs: ["Audience", "Source facts", "Tone constraints"],
    expectedOutputs: ["Draft content", "Claims to review", "Variants"],
    rawCapture:
      "Marketing needs to turn a product launch brief into a board-ready slide deck, short demo video script, speaker notes, visual direction, and review checklist.",
    template: {
      title: "Launch Deck and Demo Video Generator",
      summary: "Creates a slide deck outline, demo video script, speaker notes, visual direction, and claims review checklist from a launch brief.",
      assetType: "CONTENT_GENERATOR",
      useCase: "Launch content production",
      businessProcess: "Product marketing",
      aiTool: "Canva",
      tagNames: "content-generator, slides, video, product-marketing",
      promptTemplate:
        "Create launch assets from {{launch_brief}} for {{audience}}: slide deck outline, demo video script, speaker notes, visual direction, and claims to review.",
      workflowStepsJson:
        '[{"name":"Capture launch brief"},{"name":"Draft narrative arc"},{"name":"Create slide outline"},{"name":"Write demo video script"},{"name":"Flag claims for review"}]',
      inputSchemaJson: '{"launch_brief":"string","audience":"string","brand_constraints":"string","demo_flow":"string"}',
      outputSchemaJson: '{"slides":"array","video_script":"string","speaker_notes":"array","visual_direction":"string","claims_to_review":"array"}',
      exampleInput: "Launch brief for an AI governance feature aimed at enterprise ops leaders.",
      exampleOutput: "10-slide launch outline, 90-second script, speaker notes, and visual direction.",
      riskLevel: "MEDIUM",
    },
  },
  DATA_EXTRACTION: {
    type: "DATA_EXTRACTION",
    departmentName: "Finance",
    captureLabel: "Extraction source and target fields",
    contentLabel: "Extraction Instructions",
    description: "A structured extraction asset that turns messy documents into fields or records.",
    requiredInputs: ["Document text", "Target schema", "Validation rules"],
    expectedOutputs: ["Extracted fields", "Confidence", "Exceptions"],
    rawCapture:
      "Finance receives vendor contracts and needs to extract renewal date, termination window, payment terms, obligations, and unusual clauses.",
    template: {
      title: "Contract Obligation Extractor",
      summary: "Extracts renewal dates, notice windows, payment terms, obligations, and clause exceptions from vendor contracts.",
      assetType: "DATA_EXTRACTION",
      useCase: "Contract metadata extraction",
      businessProcess: "Vendor management",
      aiTool: "OpenAI GPT-4o",
      tagNames: "data-extraction, contracts, finance",
      promptTemplate:
        "Extract contract fields from {{contract_text}} using {{target_schema}}. Flag ambiguity, unusual clauses, and confidence for each field.",
      workflowStepsJson:
        '[{"name":"Import contract text"},{"name":"Extract target fields"},{"name":"Validate dates and amounts"},{"name":"Flag exceptions"},{"name":"Route for review"}]',
      inputSchemaJson: '{"contract_text":"string","target_schema":"object","vendor_name":"string"}',
      outputSchemaJson: '{"fields":"object","confidence":"object","exceptions":"array"}',
      exampleInput: "MSA text with renewal terms, termination notice, and SLA obligations.",
      exampleOutput: "Structured contract fields with confidence and review flags.",
      riskLevel: "HIGH",
    },
  },
  EVALUATION_CHECKLIST: {
    type: "EVALUATION_CHECKLIST",
    departmentName: "Governance",
    captureLabel: "Review criteria",
    contentLabel: "Evaluation Rubric",
    description: "A quality gate for reviewing AI outputs, tools, vendors, or prompts.",
    requiredInputs: ["Artifact to review", "Criteria", "Business context"],
    expectedOutputs: ["Score", "Issues", "Decision recommendation"],
    rawCapture:
      "Governance reviews new AI tools before teams use them. The checklist should cover data access, privacy, hallucination risk, auditability, and owner readiness.",
    template: {
      title: "Vendor AI Tool Review Checklist",
      summary: "Scores proposed AI tools across privacy, data access, auditability, hallucination risk, and operational ownership.",
      assetType: "EVALUATION_CHECKLIST",
      useCase: "AI vendor review",
      businessProcess: "AI governance",
      aiTool: "Claude",
      tagNames: "evaluation, vendor-review, governance",
      promptTemplate:
        "Evaluate {{tool_profile}} against {{review_criteria}}. Score each area, flag blockers, and recommend approve, pilot, or reject.",
      workflowStepsJson:
        '[{"name":"Collect tool profile"},{"name":"Check data access"},{"name":"Score risk criteria"},{"name":"Flag blockers"},{"name":"Record decision"}]',
      inputSchemaJson: '{"tool_profile":"string","review_criteria":"array","business_context":"string"}',
      outputSchemaJson: '{"score":"number","findings":"array","blockers":"array","recommendation":"string"}',
      exampleInput: "AI meeting note vendor requesting calendar and transcript access.",
      exampleOutput: "Risk score, blockers, and pilot recommendation.",
      riskLevel: "HIGH",
    },
  },
  PLAYBOOK: {
    type: "PLAYBOOK",
    departmentName: "Sales",
    captureLabel: "Operating playbook notes",
    contentLabel: "Playbook Instructions",
    description: "A reusable operating guide for a business situation with talk tracks and actions.",
    requiredInputs: ["Scenario", "Context", "Allowed actions"],
    expectedOutputs: ["Recommended path", "Talk track", "Follow-up actions"],
    rawCapture:
      "Sales needs an objection playbook for common competitor claims. It should use product facts, approved differentiators, discovery questions, and follow-up assets.",
    template: {
      title: "Competitive Objection Handling Playbook",
      summary: "Turns competitor context into approved differentiators, discovery questions, objection handling, and follow-up assets.",
      assetType: "PLAYBOOK",
      useCase: "Competitive enablement",
      businessProcess: "Sales enablement",
      aiTool: "Claude",
      tagNames: "playbook, competitive, sales",
      promptTemplate:
        "Build an objection handling play from {{competitor_claim}}, {{customer_context}}, and {{approved_differentiators}}.",
      workflowStepsJson:
        '[{"name":"Capture competitor claim"},{"name":"Match approved facts"},{"name":"Draft discovery questions"},{"name":"Suggest talk track"},{"name":"Attach follow-up assets"}]',
      inputSchemaJson: '{"competitor_claim":"string","customer_context":"string","approved_differentiators":"array"}',
      outputSchemaJson: '{"talk_track":"string","questions":"array","follow_up_assets":"array","risks":"array"}',
      exampleInput: "Prospect says competitor has faster onboarding and cheaper analytics.",
      exampleOutput: "Objection play with approved differentiators and discovery questions.",
      riskLevel: "MEDIUM",
    },
  },
  HANDOVER_PACK: {
    type: "HANDOVER_PACK",
    departmentName: "People Operations",
    captureLabel: "Role transition context",
    contentLabel: "Handover Pack Instructions",
    description: "A transfer package that preserves AI capability ownership across onboarding or offboarding.",
    requiredInputs: ["Owned assets", "Recurring workflows", "Successor context"],
    expectedOutputs: ["Handover plan", "At-risk assets", "Action owners"],
    rawCapture:
      "When someone leaves or joins a role, managers need a pack of owned AI assets, recurring workflows, missing backup owners, and first-week actions.",
    template: {
      title: "Role Transition Handover Pack",
      summary: "Builds a role transition pack with owned assets, recurring AI workflows, backup owner gaps, and action checklist.",
      assetType: "HANDOVER_PACK",
      useCase: "Role transition continuity",
      businessProcess: "People operations",
      aiTool: "Claude",
      tagNames: "handover, onboarding, offboarding",
      promptTemplate:
        "Build a transition pack from {{owned_assets}}, {{recurring_workflows}}, {{access_notes}}, and {{successor_context}}.",
      workflowStepsJson:
        '[{"name":"List owned assets"},{"name":"Detect backup gaps"},{"name":"Summarize recurring workflows"},{"name":"Assign continuity actions"},{"name":"Generate transition pack"}]',
      inputSchemaJson: '{"owned_assets":"array","recurring_workflows":"string","access_notes":"string","successor_context":"string"}',
      outputSchemaJson: '{"handover_pack":"markdown","at_risk_assets":"array","actions":"array"}',
      exampleInput: "Departing customer success manager with eight owned workflows.",
      exampleOutput: "Handover pack, at-risk assets, and owner actions.",
      riskLevel: "MEDIUM",
    },
  },
  GOVERNANCE_GUARDRAIL: {
    type: "GOVERNANCE_GUARDRAIL",
    departmentName: "Governance",
    captureLabel: "Policy or safety rule",
    contentLabel: "Guardrail Instructions",
    description: "A reusable policy check for privacy, compliance, brand, or model-risk boundaries.",
    requiredInputs: ["Prompt or output", "Sharing context", "Policy rule"],
    expectedOutputs: ["Risk classification", "Redactions", "Escalation"],
    rawCapture:
      "Before teams share AI-generated customer summaries externally, we need to detect PII, sensitive attributes, unsupported claims, and required redactions.",
    template: {
      title: "External Sharing Safety Guardrail",
      summary: "Checks prompts and outputs before external sharing for PII, unsupported claims, redaction gaps, and escalation needs.",
      assetType: "GOVERNANCE_GUARDRAIL",
      useCase: "External output safety review",
      businessProcess: "AI governance",
      aiTool: "OpenAI GPT-4o",
      tagNames: "guardrail, pii, external-sharing",
      promptTemplate:
        "Review {{prompt_or_output}} for {{sharing_context}}. Detect PII, sensitive attributes, unsupported claims, and redaction gaps.",
      workflowStepsJson:
        '[{"name":"Inspect prompt or output"},{"name":"Detect sensitive data"},{"name":"Classify risk"},{"name":"Recommend redaction"},{"name":"Escalate high-risk cases"}]',
      inputSchemaJson: '{"prompt_or_output":"string","sharing_context":"string","policy_rule":"string"}',
      outputSchemaJson: '{"risk":"string","findings":"array","redacted_version":"string","escalation":"string"}',
      exampleInput: "Customer-facing summary containing names, emails, and account identifiers.",
      exampleOutput: "Risk findings, redacted version, and escalation path.",
      riskLevel: "HIGH",
    },
  },
  COPILOT: {
    type: "COPILOT",
    departmentName: "Customer Success",
    captureLabel: "Copilot usage scenario",
    contentLabel: "Copilot Instructions",
    description: "An assistant that stays in the user's workflow and suggests next actions or drafts.",
    requiredInputs: ["Live context", "User goal", "Knowledge sources"],
    expectedOutputs: ["Suggested response", "Next action", "Source hints"],
    rawCapture:
      "Support agents need an in-workflow copilot that reads a ticket, checks similar resolutions, drafts a reply, and suggests escalation when confidence is low.",
    template: {
      title: "Support Reply Copilot",
      summary: "Suggests support replies from ticket context, customer tier, product area, similar cases, and tone guidelines.",
      assetType: "COPILOT",
      useCase: "Support response assistance",
      businessProcess: "Customer service operations",
      aiTool: "ChatGPT",
      tagNames: "copilot, support, customer-service",
      promptTemplate:
        "Suggest a support reply using {{ticket_context}}, {{customer_tier}}, {{product_area}}, and {{resolution_history}}. Escalate if confidence is low.",
      workflowStepsJson:
        '[{"name":"Read ticket context"},{"name":"Retrieve similar cases"},{"name":"Draft reply"},{"name":"Check tone and policy"},{"name":"Agent approves response"}]',
      inputSchemaJson: '{"ticket_context":"string","customer_tier":"string","product_area":"string","resolution_history":"array"}',
      outputSchemaJson: '{"reply":"string","confidence":"string","related_articles":"array","escalation":"string"}',
      exampleInput: "Priority customer asks about delayed analytics export.",
      exampleOutput: "Support reply with confidence, related articles, and escalation note.",
      riskLevel: "MEDIUM",
    },
  },
}

export function getAssetTypeSpec(assetType: AssetType) {
  return assetTypeSpecs[assetType]
}

export function assetTypeSummary(assetType: AssetType) {
  const spec = getAssetTypeSpec(assetType)
  return `${formatAssetType(assetType)}: ${spec.description}`
}

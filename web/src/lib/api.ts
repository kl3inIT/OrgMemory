export type AssetStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'DEPRECATED'
export type AssetVisibility = 'PRIVATE' | 'TEAM' | 'ORGANIZATION'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type UsageEventType = 'VIEWED' | 'COPIED' | 'USED' | 'SHARED'
export type OrgUserRole = 'EMPLOYEE' | 'TEAM_LEAD' | 'ADMIN'
export type AssetType =
  | 'PROMPT_TEMPLATE'
  | 'WORKFLOW_AUTOMATION'
  | 'AI_AGENT'
  | 'KNOWLEDGE_BOT'
  | 'ANALYTICS_BRIEF'
  | 'CONTENT_GENERATOR'
  | 'DATA_EXTRACTION'
  | 'EVALUATION_CHECKLIST'
  | 'PLAYBOOK'
  | 'HANDOVER_PACK'
  | 'GOVERNANCE_GUARDRAIL'
  | 'COPILOT'

export type GraphNodeKind = 'ASSET' | 'ASSET_TYPE' | 'DEPARTMENT' | 'USER' | 'TAG'
export type GraphEdgeKind =
  | 'HAS_TYPE'
  | 'BELONGS_TO'
  | 'OWNED_BY'
  | 'BACKED_UP_BY'
  | 'TAGGED_WITH'
  | 'RELATED_BY_TAG'
  | 'RELATED_BY_OWNER'
  | 'RELATED_BY_PROCESS'

export type CapabilityAsset = {
  id: string
  organizationId: string
  departmentId: string | null
  title: string
  summary: string
  assetType: AssetType
  useCase: string | null
  businessProcess: string | null
  aiTool: string | null
  tagNames: string | null
  ownerUserId: string | null
  backupOwnerUserId: string | null
  status: AssetStatus
  visibility: AssetVisibility
  riskLevel: RiskLevel | null
  currentVersionId: string | null
  createdByUserId: string | null
  usageCount: number
  createdAt: string
  updatedAt: string
}

export type Department = {
  id: string
  organizationId: string
  name: string
}

export type OrgUser = {
  id: string
  organizationId: string
  departmentId: string | null
  name: string
  email: string
  role: OrgUserRole
}

export type OrganizationContext = {
  organizationId: string
  departments: Department[]
  users: OrgUser[]
}

export type AssetVersion = {
  id: string
  assetId: string
  versionNumber: number
  promptTemplate: string | null
  workflowStepsJson: string | null
  inputSchemaJson: string | null
  outputSchemaJson: string | null
  exampleInput: string | null
  exampleOutput: string | null
  changeNote: string | null
  createdByUserId: string | null
  createdAt: string
}

export type KnowledgeGraphNode = {
  id: string
  label: string
  kind: GraphNodeKind
  detail: string | null
  assetId: string | null
  assetType: AssetType | null
  status: AssetStatus | null
  weight: number
}

export type KnowledgeGraphEdge = {
  id: string
  source: string
  target: string
  kind: GraphEdgeKind
  label: string
  weight: number
}

export type KnowledgeGraph = {
  nodes: KnowledgeGraphNode[]
  edges: KnowledgeGraphEdge[]
  focusNodeId: string | null
  depth: number
}

export type CreateAssetPayload = {
  departmentId?: string
  title: string
  summary: string
  assetType: AssetType
  useCase?: string
  businessProcess?: string
  aiTool?: string
  tagNames?: string
  ownerUserId?: string
  backupOwnerUserId?: string
  visibility: AssetVisibility
  riskLevel: RiskLevel
  promptTemplate?: string
  workflowStepsJson?: string
  inputSchemaJson?: string
  outputSchemaJson?: string
  exampleInput?: string
  exampleOutput?: string
}

export type AiDraftResponse = {
  aiEnabled: boolean
  source: string
  note: string
  title: string
  summary: string
  assetType: AssetType
  useCase: string
  businessProcess: string
  aiTool: string
  tagNames: string
  riskLevel: RiskLevel
  promptTemplate: string
  workflowStepsJson: string
  inputSchemaJson: string
  outputSchemaJson: string
  exampleInput: string
  exampleOutput: string
}

import { getBrowserCsrfToken } from './hey-api'

const jsonHeaders = {
  'Content-Type': 'application/json',
}

export const DEFAULT_ORGANIZATION_ID = '11111111-1111-1111-1111-111111111111'

export type Me = {
  authenticated: boolean
  subject: string | null
  email: string | null
  name: string | null
  authorizationProvider: 'openfga'
  userId: string
  organizationId: string
  departmentId: string | null
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  const method = init?.method?.toUpperCase() ?? 'GET'
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const { data } = await getBrowserCsrfToken({ throwOnError: true })
    if (!data?.headerName || !data.token) {
      throw new Error('The server did not issue a CSRF token.')
    }
    headers.set(data.headerName, data.token)
  }

  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers,
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `Request failed with ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function getMe() {
  return request<Me>('/api/me')
}

export function listAssets(status?: AssetStatus, query?: string, assetType?: AssetType) {
  const params = new URLSearchParams()
  if (status) {
    params.set('status', status)
  }
  if (assetType) {
    params.set('assetType', assetType)
  }
  if (query) {
    params.set('q', query)
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : ''
  return request<CapabilityAsset[]>(`/api/assets${suffix}`)
}

export function getAsset(assetId: string) {
  return request<CapabilityAsset>(`/api/assets/${assetId}`)
}

export function getOrganizationContext() {
  return request<OrganizationContext>('/api/organization/context')
}

export function createAsset(payload: CreateAssetPayload) {
  return request<CapabilityAsset>('/api/assets', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload),
  })
}

export function normalizeAsset(rawText: string, aiTool?: string, businessProcess?: string) {
  return request<AiDraftResponse>('/api/ai/assets/normalize', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ rawText, aiTool, businessProcess }),
  })
}

export function updateAssetStatus(assetId: string, action: 'submit-review' | 'approve' | 'reject' | 'deprecate') {
  return request<CapabilityAsset>(`/api/assets/${assetId}/${action}`, {
    method: 'PATCH',
    headers: jsonHeaders,
    body: JSON.stringify({ comment: `Updated through OrgMemory web: ${action}` }),
  })
}

export function assignBackupOwner(assetId: string, backupOwnerUserId: string) {
  return request<CapabilityAsset>(`/api/assets/${assetId}/backup-owner`, {
    method: 'PATCH',
    headers: jsonHeaders,
    body: JSON.stringify({ backupOwnerUserId }),
  })
}

export function recordUsage(assetId: string, eventType: UsageEventType) {
  return request<{ assetId: string; usageCount: number }>(`/api/assets/${assetId}/usage`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ eventType }),
  })
}

export function listVersions(assetId: string) {
  return request<AssetVersion[]>(`/api/assets/${assetId}/versions`)
}

export function getKnowledgeGraph(options?: { query?: string; focusAssetId?: string; depth?: number }) {
  const params = new URLSearchParams()
  if (options?.query) {
    params.set('q', options.query)
  }
  if (options?.focusAssetId) {
    params.set('focusAssetId', options.focusAssetId)
  }
  if (options?.depth) {
    params.set('depth', String(options.depth))
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : ''
  return request<KnowledgeGraph>(`/api/graph${suffix}`)
}

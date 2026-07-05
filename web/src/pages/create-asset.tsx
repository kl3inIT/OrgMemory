import { useNavigate } from "@tanstack/react-router"
import { HelpCircle, Send, ShieldCheck, Sparkles } from "lucide-react"
import { useEffect, useState } from "react"
import { PageTitle } from "@/components/layout/page-title"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { initialForm, initialRawCapture, type DraftForm } from "@/features/assets/demo-data"
import { useAssetStatusAction, useCreateAsset, useNormalizeAsset } from "@/features/assets/use-assets"
import { assetTypes, formatAssetType } from "@/features/assets/asset-type"
import { assetTypeSummary, getAssetTypeSpec } from "@/features/assets/asset-type-specs"
import { useOrganizationContext } from "@/features/organization/use-organization-context"
import { DEFAULT_ORGANIZATION_ID, type AssetType, type RiskLevel } from "@/lib/api"

const aiToolOptions = [
  "ChatGPT",
  "OpenAI GPT-4o",
  "OpenAI Images",
  "OpenAI Codex",
  "Claude",
  "Claude Code",
  "Gemini",
  "Perplexity",
  "NotebookLM",
  "GitHub Copilot",
  "Cursor",
  "v0",
  "Lovable",
  "Canva",
  "Gamma",
  "Runway",
  "Descript",
  "Midjourney",
  "DALL-E",
  "Ideogram",
  "Stable Diffusion",
  "Napkin AI",
  "n8n",
  "Zapier",
  "Make",
  "LangChain",
  "LlamaIndex",
  "Dify",
  "Flowise",
]

export function CreateAssetPage() {
  const navigate = useNavigate()
  const [rawCapture, setRawCapture] = useState(initialRawCapture)
  const [form, setForm] = useState<DraftForm>(initialForm)
  const [departmentId, setDepartmentId] = useState("")
  const [ownerUserId, setOwnerUserId] = useState("")
  const [backupOwnerUserId, setBackupOwnerUserId] = useState("")
  const [draftNote, setDraftNote] = useState("Generated preview will appear here after Spring AI enrichment.")
  const organization = useOrganizationContext()
  const normalize = useNormalizeAsset()
  const create = useCreateAsset()
  const statusAction = useAssetStatusAction()
  const selectedSpec = getAssetTypeSpec(form.assetType)

  useEffect(() => {
    const context = organization.data
    if (!context) return
    if (!departmentId) {
      const matchingDepartment = context.departments.find((department) => department.name === selectedSpec.departmentName)
      setDepartmentId((matchingDepartment ?? context.departments[0])?.id ?? "")
    }
    if (!ownerUserId) {
      setOwnerUserId(context.users[0]?.id ?? "")
    }
    if (!backupOwnerUserId) {
      setBackupOwnerUserId(context.users.find((user) => user.id !== context.users[0]?.id)?.id ?? "NONE")
    }
  }, [backupOwnerUserId, departmentId, organization.data, ownerUserId, selectedSpec.departmentName])

  function applyAssetType(assetType: AssetType) {
    const spec = getAssetTypeSpec(assetType)
    setForm(spec.template)
    setRawCapture(spec.rawCapture)
    setDraftNote(assetTypeSummary(assetType))
    const matchingDepartment = organization.data?.departments.find((department) => department.name === spec.departmentName)
    if (matchingDepartment) {
      setDepartmentId(matchingDepartment.id)
    }
  }

  function onNormalize() {
    normalize.mutate(
      { rawText: rawCapture, aiTool: form.aiTool, businessProcess: form.businessProcess },
      {
        onSuccess: (draft) => {
          setForm({
            title: draft.title,
            summary: draft.summary,
            assetType: draft.assetType,
            useCase: draft.useCase,
            businessProcess: draft.businessProcess,
            aiTool: draft.aiTool,
            tagNames: draft.tagNames,
            riskLevel: draft.riskLevel,
            promptTemplate: draft.promptTemplate,
            workflowStepsJson: draft.workflowStepsJson,
            inputSchemaJson: draft.inputSchemaJson,
            outputSchemaJson: draft.outputSchemaJson,
            exampleInput: draft.exampleInput,
            exampleOutput: draft.exampleOutput,
          })
          setDraftNote(draft.note)
        },
      },
    )
  }

  function buildPayload() {
    const ownerId = ownerUserId || organization.data?.users[0]?.id
    const backupOwnerId =
      backupOwnerUserId === "NONE"
        ? undefined
        : backupOwnerUserId || organization.data?.users.find((user) => user.id !== ownerId)?.id
    return {
      organizationId: organization.data?.organizationId ?? DEFAULT_ORGANIZATION_ID,
      departmentId: departmentId || organization.data?.departments[0]?.id,
      title: form.title,
      summary: form.summary,
      assetType: form.assetType,
      useCase: form.useCase,
      businessProcess: form.businessProcess,
      aiTool: form.aiTool,
      tagNames: form.tagNames,
      ownerUserId: ownerId,
      backupOwnerUserId: backupOwnerId,
      createdByUserId: ownerId,
      visibility: "TEAM" as const,
      riskLevel: form.riskLevel,
      promptTemplate: form.promptTemplate,
      workflowStepsJson: form.workflowStepsJson,
      inputSchemaJson: form.inputSchemaJson,
      outputSchemaJson: form.outputSchemaJson,
      exampleInput: form.exampleInput,
      exampleOutput: form.exampleOutput,
    }
  }

  function onSaveDraft() {
    create.mutate(buildPayload(), { onSuccess: () => navigate({ to: "/registry" }) })
  }

  function onSubmitForReview() {
    create.mutate(
      buildPayload(),
      {
        onSuccess: (asset) => {
          statusAction.mutate(
            { assetId: asset.id, action: "submit-review" },
            { onSuccess: () => navigate({ to: "/review" }) },
          )
        },
      },
    )
  }

  return (
    <div className="space-y-6">
      <PageTitle title="Create New Asset" subtitle="Turn an individual AI workflow into a reusable organizational capability." />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_28rem]">
        <Card>
          <CardHeader className="border-b">
            <CardTitle>Asset Details</CardTitle>
            <CardDescription>Describe the prompt, workflow, inputs, and expected output.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5 pt-6">
            <Alert>
              <HelpCircle />
              <AlertTitle>{formatAssetType(form.assetType)} capture</AlertTitle>
              <AlertDescription>
                {selectedSpec.description}
              </AlertDescription>
            </Alert>

            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Asset Name" required>
                <Input value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} />
              </Field>
              <Field label="Department" required>
                <Select
                  value={departmentId || "LOADING"}
                  onValueChange={(value) => {
                    setDepartmentId(value)
                    const department = organization.data?.departments.find((item) => item.id === value)
                    if (department && !form.businessProcess.trim()) {
                      setForm({ ...form, businessProcess: department.name })
                    }
                  }}
                >
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {organization.data?.departments.map((department) => (
                      <SelectItem key={department.id} value={department.id}>{department.name}</SelectItem>
                    )) ?? <SelectItem value="LOADING" disabled>Loading departments...</SelectItem>}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Use Case" required>
                <Input value={form.useCase} onChange={(event) => setForm({ ...form, useCase: event.target.value })} />
              </Field>
              <Field label="Asset Type" required>
                <Select value={form.assetType} onValueChange={(value) => applyAssetType(value as AssetType)}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {assetTypes.map((assetType) => (
                      <SelectItem key={assetType} value={assetType}>{formatAssetType(assetType)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Tool Used" required>
                <Select value={form.aiTool} onValueChange={(value) => setForm({ ...form, aiTool: value })}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {aiToolOptions.map((tool) => <SelectItem key={tool} value={tool}>{tool}</SelectItem>)}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Business Process" required>
                <Input value={form.businessProcess} onChange={(event) => setForm({ ...form, businessProcess: event.target.value })} />
              </Field>
              <Field label="Owner" required>
                <Select value={ownerUserId || "LOADING"} onValueChange={setOwnerUserId}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {organization.data?.users.map((user) => (
                      <SelectItem key={user.id} value={user.id}>{user.name}</SelectItem>
                    )) ?? <SelectItem value="LOADING" disabled>Loading users...</SelectItem>}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Backup Owner">
                <Select value={backupOwnerUserId || "NONE"} onValueChange={setBackupOwnerUserId}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="NONE">No backup owner</SelectItem>
                    {organization.data?.users.map((user) => (
                      <SelectItem key={user.id} value={user.id}>{user.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Risk Level" required>
                <Select value={form.riskLevel} onValueChange={(value) => setForm({ ...form, riskLevel: value as RiskLevel })}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="LOW">Low</SelectItem>
                    <SelectItem value="MEDIUM">Medium</SelectItem>
                    <SelectItem value="HIGH">High</SelectItem>
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Tags">
                <Input value={form.tagNames} onChange={(event) => setForm({ ...form, tagNames: event.target.value })} />
              </Field>
            </div>

            <Field label={selectedSpec.captureLabel}>
              <Textarea className="min-h-28" value={rawCapture} onChange={(event) => setRawCapture(event.target.value)} />
            </Field>
            <Field label={selectedSpec.contentLabel} required>
              <Textarea className="min-h-28 font-mono text-xs" value={form.promptTemplate} onChange={(event) => setForm({ ...form, promptTemplate: event.target.value })} />
            </Field>

            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={onSaveDraft} disabled={create.isPending || statusAction.isPending}>Save Draft</Button>
              <Button onClick={onSubmitForReview} disabled={create.isPending || statusAction.isPending}><Send /> Submit for Review</Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Sparkles className="size-5 text-primary" /> AI Enrichment</CardTitle>
            <CardDescription>Generated from pasted workflow</CardDescription>
            <CardAction>
              <Button onClick={onNormalize} disabled={normalize.isPending}><Sparkles /> Generate</Button>
            </CardAction>
          </CardHeader>
          <CardContent className="space-y-5">
            <p className="text-sm leading-6 text-muted-foreground">{draftNote}</p>
            <PreviewBox title="Summary">{form.summary}</PreviewBox>
            <PreviewList title="Required Inputs" items={selectedSpec.requiredInputs} />
            <PreviewList title="Expected Outputs" items={selectedSpec.expectedOutputs} />
            <div className="space-y-2">
              <Label>Suggested Tags</Label>
              <div className="flex flex-wrap gap-2">
                {form.tagNames.split(",").map((tag) => <Badge key={tag.trim()} variant="secondary">{tag.trim()}</Badge>)}
              </div>
            </div>
            <Alert>
              <ShieldCheck />
              <AlertTitle>{form.riskLevel.toLowerCase()} risk</AlertTitle>
              <AlertDescription>Output should be reviewed before external sharing.</AlertDescription>
            </Alert>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div className="grid gap-2">
      <Label>{label} {required ? <span className="text-destructive">*</span> : null}</Label>
      {children}
    </div>
  )
}

function PreviewBox({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{title}</Label>
      <div className="rounded-md border bg-muted/30 p-3 text-sm leading-6 text-muted-foreground">{children}</div>
    </div>
  )
}

function PreviewList({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="space-y-2">
      <Label>{title}</Label>
      <div className="rounded-md border bg-muted/30 p-3">
        <ul className="space-y-1 text-sm text-muted-foreground">
          {items.map((item) => <li key={item}>{item}</li>)}
        </ul>
      </div>
    </div>
  )
}

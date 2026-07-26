import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import {
  ArrowRight,
  Braces,
  Check,
  CircleAlert,
  History,
  Play,
  Send,
  ShieldCheck,
} from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import {
  ASSET_TYPE_META,
  formatAssetCoordinate,
  formatDate,
  latestUsableRelease,
  parsePayload,
} from "@/features/assets/asset-format"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import { AssetBreadcrumb } from "@/features/assets/components/asset-breadcrumb"
import { AssetPageError, AssetPageLoading } from "@/features/assets/components/asset-state"
import { AssistantActionReceipt } from "@/features/assistant/components/assistant-action-receipt"
import {
  acknowledgeWorkInstructionMutation,
  getAssetOptions,
  getAssetQueryKey,
  renderAssistantPromptMutation,
  runAssistantPromptMutation,
  submitAssistantAssetFeedbackMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AssetView, Release } from "@/lib/hey-api"

type PromptVariable = {
  name: string
  type: "STRING" | "INTEGER" | "NUMBER" | "BOOLEAN" | "STRING_LIST"
  required: boolean
  defaultValue?: unknown
  sensitive?: boolean
  allowedValues?: string[]
}

type PromptPayload = {
  objective?: string
  audience?: string
  useWhen?: string[]
  doNotUseWhen?: string[]
  variables?: PromptVariable[]
  knowledgeRequirements?: string[]
  knownLimitations?: string
}

type WorkInstructionPayload = {
  purpose?: string
  audience?: string
  prerequisites?: string[]
  completionOutcome?: string
  responsibleRole?: string
  steps?: Array<{
    key: string
    title: string
    instruction: string
    expectedResult: string
    check: string
    escalation?: string
    prohibitedActions?: string[]
  }>
}

type PackPayload = {
  purpose?: string
  audience?: string
  prerequisites?: string[]
  expectedOutcome?: string
  completionCriteria?: string[]
  items?: Array<{ key: string; required: boolean; kind: string }>
}

export function AssetDetailPage({
  assetId,
  actorKey,
  releaseId,
  onReleaseChange,
}: {
  assetId: string
  actorKey: string
  releaseId?: string
  onReleaseChange: (releaseId?: string) => void
}) {
  const assetOptions = getAssetOptions({ path: { assetId } })
  const asset = useQuery({
    ...assetOptions,
    queryKey: scopeAssetQueryKey(assetOptions.queryKey, actorKey),
  })

  if (asset.isPending) return <AssetPageLoading />
  if (asset.isError || !asset.data?.id || !asset.data.type) {
    return <AssetPageError onRetry={() => void asset.refetch()} />
  }

  const selected =
    asset.data.releases?.find((release) => release.id === releaseId) ??
    latestUsableRelease(asset.data)

  return (
    <PageLayout.Root variant="wide">
      <AssetIdentityHeader
        asset={asset.data}
        release={selected}
        onReleaseChange={onReleaseChange}
      />
      {selected ? (
        <ProfilePanel asset={asset.data} release={selected} />
      ) : (
        <Card className="border-dashed">
          <CardContent className="p-8">
            <h2 className="text-section-title">No usable release yet</h2>
            <p className="mt-2 text-body text-content-secondary">
              This Asset is visible for authoring, but it has no release available for use.
            </p>
            <Button asChild variant="outline" className="mt-5">
              <Link to="/assets/$assetId/governance" params={{ assetId }}>
                Open governance workspace
              </Link>
            </Button>
          </CardContent>
        </Card>
      )}
    </PageLayout.Root>
  )
}

function AssetIdentityHeader({
  asset,
  release,
  onReleaseChange,
}: {
  asset: AssetView
  release?: Release
  onReleaseChange: (releaseId?: string) => void
}) {
  const meta = ASSET_TYPE_META[asset.type!]
  const Icon = meta.icon
  const activeAssignments = (asset.roleAssignments ?? []).filter(
    (assignment) => !assignment.validUntil || new Date(assignment.validUntil) > new Date(),
  )
  const owner = activeAssignments.find((assignment) => assignment.role === "OWNER")
  const backupOwner = activeAssignments.find((assignment) => assignment.role === "BACKUP_OWNER")
  return (
    <>
      <PageLayout.Header
        title={release?.title ?? asset.draft?.title}
        description={release?.summary ?? asset.draft?.summary}
        icon={<Icon className="size-5" aria-hidden="true" />}
        breadcrumb={
          <AssetBreadcrumb assetId={asset.id} assetTitle={release?.title ?? asset.draft?.title} />
        }
        metadata={
          <div className="flex flex-wrap items-center gap-2">
            <Badge className={meta.tone}>{meta.label}</Badge>
            <Badge variant="outline">{asset.portfolioState}</Badge>
            {asset.ownershipHealth?.orphaned ? (
              <Badge className="bg-status-danger-surface text-status-danger-content">
                Orphaned
              </Badge>
            ) : asset.ownershipHealth?.continuityAtRisk ? (
              <Badge className="bg-status-warning-surface text-status-warning-content">
                Ownership gap
              </Badge>
            ) : null}
            {release?.availability === "DEPRECATED" ? (
              <Badge className="bg-status-warning-surface text-status-warning-content">
                Deprecated
              </Badge>
            ) : null}
          </div>
        }
        actions={
          <div className="grid min-w-64 gap-2">
            <Label htmlFor="asset-release">Pinned release</Label>
            <Select
              value={release?.id}
              onValueChange={(value: string) => onReleaseChange(value)}
              disabled={!asset.releases?.length}
            >
              <SelectTrigger id="asset-release" className="w-full">
                <SelectValue placeholder="No release" />
              </SelectTrigger>
              <SelectContent>
                {asset.releases?.map((item) => (
                  <SelectItem key={item.id} value={item.id!}>
                    {item.versionLabel} · {item.availability}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button asChild variant="outline">
              <Link to="/assets/$assetId/governance" params={{ assetId: asset.id! }}>
                <History aria-hidden="true" />
                Governance
              </Link>
            </Button>
          </div>
        }
      >
        <div className="flex flex-wrap gap-2 text-metadata text-content-secondary">
          <span className="font-mono">{formatAssetCoordinate(asset)}</span>
          <span aria-hidden="true">·</span>
          <span>Owner: {owner?.principalId ?? "Unassigned"}</span>
          <span aria-hidden="true">·</span>
          <span>Backup: {backupOwner?.principalId ?? "Missing"}</span>
        </div>
      </PageLayout.Header>
      {release ? (
        <div className="grid overflow-hidden rounded-xl border border-border-subtle bg-border-subtle sm:grid-cols-3 sm:gap-px">
          <Metadata label="Version" value={release.versionLabel} />
          <Metadata label="Released" value={formatDate(release.releasedAt)} />
          <Metadata label="Digest" value={release.digest?.slice(0, 16)} mono />
        </div>
      ) : null}
    </>
  )
}

function Metadata({
  label,
  value,
  mono = false,
}: {
  label: string
  value?: string
  mono?: boolean
}) {
  return (
    <div className="bg-surface-subtle px-5 py-3">
      <p className="text-metadata text-content-muted">{label}</p>
      <p
        className={`mt-1 truncate text-supporting text-content-primary ${mono ? "font-mono" : ""}`}
      >
        {value ?? "—"}
      </p>
    </div>
  )
}

function ProfilePanel({ asset, release }: { asset: AssetView; release: Release }) {
  if (!asset.id || !release.id) return null
  if (asset.type === "PROMPT_TEMPLATE") {
    return <PromptPanel assetId={asset.id} release={release} />
  }
  if (asset.type === "WORK_INSTRUCTION") {
    return <WorkInstructionPanel assetId={asset.id} release={release} />
  }
  return <PackPanel assetId={asset.id} release={release} />
}

function PromptPanel({ assetId, release }: { assetId: string; release: Release }) {
  const payload = parsePayload<PromptPayload>(release.payload)
  const [variables, setVariables] = useState<Record<string, unknown>>(() =>
    Object.fromEntries(
      (payload?.variables ?? [])
        .filter((variable) => variable.defaultValue !== undefined)
        .map((variable) => [variable.name, variable.defaultValue]),
    ),
  )
  const [knowledgeQuery, setKnowledgeQuery] = useState("")
  const render = useMutation(renderAssistantPromptMutation())
  const run = useMutation(runAssistantPromptMutation())
  const result = run.data?.result

  if (!payload) return <InvalidPayload />

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-3">
            <div>
              <CardTitle>Use this prompt</CardTitle>
              <p className="mt-1 text-supporting text-content-secondary">{payload.objective}</p>
            </div>
            <Braces className="size-5 text-content-muted" aria-hidden="true" />
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          {(payload.variables ?? []).map((variable) => (
            <PromptVariableField
              key={variable.name}
              variable={variable}
              value={variables[variable.name]}
              onChange={(value) =>
                setVariables((current) => ({ ...current, [variable.name]: value }))
              }
            />
          ))}
          <div className="space-y-2">
            <Label htmlFor="knowledge-query">Optional grounding question</Label>
            <Textarea
              id="knowledge-query"
              value={knowledgeQuery}
              onChange={(event) => setKnowledgeQuery(event.currentTarget.value)}
              placeholder="What company knowledge should ground this run?"
              rows={3}
            />
          </div>
          <Alert>
            <ShieldCheck aria-hidden="true" />
            <AlertTitle>Exact, permission-aware execution</AlertTitle>
            <AlertDescription>
              Variables and retrieved Knowledge are treated as untrusted input. The run pins this
              release digest and the resolved model route.
            </AlertDescription>
          </Alert>
          <div className="flex flex-wrap gap-3">
            <Button
              variant="outline"
              disabled={render.isPending}
              onClick={() =>
                render.mutate({
                  path: { assetId, releaseId: release.id! },
                  body: { variables },
                })
              }
            >
              <Braces aria-hidden="true" />
              Validate & render
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button disabled={run.isPending}>
                  <Play aria-hidden="true" />
                  Review and run
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Run this exact release once?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Approved Prompt content and the values entered here will be sent to the
                    configured external model provider. OrgMemory will pin release{" "}
                    <span className="font-mono">{release.versionLabel}</span> and trace a sanitized
                    outcome without retaining raw variable values.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <div className="grid gap-3 rounded-lg border border-border-default bg-surface-subtle p-4 text-supporting">
                  <Definition
                    label="Variables"
                    value={`${Object.keys(variables).length} supplied`}
                  />
                  <Definition
                    label="Grounding"
                    value={knowledgeQuery ? "Permission-aware Knowledge search" : "Not requested"}
                  />
                  <Definition label="Release digest" value={release.digest?.slice(0, 20)} />
                </div>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={() =>
                      run.mutate({
                        path: { assetId, releaseId: release.id! },
                        body: {
                          variables,
                          knowledgeQuery: knowledgeQuery || undefined,
                          requestId: crypto.randomUUID(),
                          confirmedExternalProvider: true,
                        },
                      })
                    }
                  >
                    Run once
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
          {render.isError || run.isError ? (
            <p role="alert" className="text-supporting text-status-danger-content">
              Check required variables and your current access, then try again.
            </p>
          ) : null}
          {render.isPending ? (
            <AssistantActionReceipt
              action="Validate and render Prompt"
              status="running"
              summary={`Resolving ${release.versionLabel ?? "exact release"}`}
              releaseDigest={release.digest}
            />
          ) : null}
          {render.isSuccess ? (
            <AssistantActionReceipt
              action="Validate and render Prompt"
              status="complete"
              summary="Variables validated; deterministic render prepared"
              traceId={render.data.traceId}
              releaseDigest={render.data.result?.releaseDigest ?? release.digest}
            />
          ) : null}
          {run.isPending ? (
            <AssistantActionReceipt
              action="Run Prompt once"
              status="running"
              summary="External provider call confirmed for this run"
              releaseDigest={release.digest}
            />
          ) : null}
          {run.isError ? (
            <AssistantActionReceipt
              action="Run Prompt once"
              status="failed"
              summary="The action did not complete"
              releaseDigest={release.digest}
            />
          ) : null}
        </CardContent>
      </Card>
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>Release contract</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 text-body">
            <Definition label="Audience" value={payload.audience} />
            <Definition
              label="Grounding"
              value={(payload.knowledgeRequirements ?? []).join(" · ") || "Not required"}
            />
            <Definition
              label="Known limitations"
              value={payload.knownLimitations || "None stated"}
            />
          </CardContent>
        </Card>
        {result ? (
          <Card className="border-status-success-border">
            <CardHeader>
              <CardTitle>Run output</CardTitle>
            </CardHeader>
            <CardContent>
              <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-sunken p-4 text-supporting">
                {result.output}
              </pre>
              <p className="mt-3 font-mono text-metadata text-content-muted">
                trace {run.data?.traceId?.slice(0, 8)} · {result.modelRoute?.modelId}
              </p>
            </CardContent>
          </Card>
        ) : null}
        <FeedbackCard assetId={assetId} release={release} />
      </div>
    </div>
  )
}

function PromptVariableField({
  variable,
  value,
  onChange,
}: {
  variable: PromptVariable
  value: unknown
  onChange: (value: unknown) => void
}) {
  const id = `prompt-variable-${variable.name}`
  if (variable.type === "BOOLEAN") {
    return (
      <div className="flex items-center justify-between gap-4 rounded-lg border p-4">
        <div>
          <Label htmlFor={id}>{variable.name}</Label>
          <p className="text-metadata text-content-muted">
            {variable.required ? "Required" : "Optional"}
          </p>
        </div>
        <Switch id={id} checked={Boolean(value)} onCheckedChange={onChange} />
      </div>
    )
  }
  if (variable.allowedValues?.length) {
    return (
      <div className="space-y-2">
        <Label htmlFor={id}>{variable.name}</Label>
        <Select value={String(value ?? "")} onValueChange={onChange}>
          <SelectTrigger id={id}>
            <SelectValue placeholder="Select a value" />
          </SelectTrigger>
          <SelectContent>
            {variable.allowedValues.map((item) => (
              <SelectItem key={item} value={item}>
                {item}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    )
  }
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={id}>{variable.name}</Label>
        <span className="text-metadata text-content-muted">
          {variable.sensitive ? "Sensitive · " : ""}
          {variable.type.toLocaleLowerCase()}
        </span>
      </div>
      <Input
        id={id}
        required={variable.required}
        type={variable.type === "INTEGER" || variable.type === "NUMBER" ? "number" : "text"}
        value={String(value ?? "")}
        onChange={(event) => {
          const raw = event.currentTarget.value
          if (variable.type === "INTEGER")
            onChange(raw === "" ? undefined : Number.parseInt(raw, 10))
          else if (variable.type === "NUMBER") onChange(raw === "" ? undefined : Number(raw))
          else if (variable.type === "STRING_LIST") {
            onChange(
              raw
                .split(",")
                .map((item) => item.trim())
                .filter(Boolean),
            )
          } else onChange(raw)
        }}
      />
    </div>
  )
}

function WorkInstructionPanel({ assetId, release }: { assetId: string; release: Release }) {
  const payload = parsePayload<WorkInstructionPayload>(release.payload)
  const queryClient = useQueryClient()
  const acknowledge = useMutation({
    ...acknowledgeWorkInstructionMutation(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: getAssetQueryKey({ path: { assetId } }) })
      toast.success("Work instruction acknowledged")
    },
  })
  if (!payload) return <InvalidPayload />

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
      <Card>
        <CardHeader>
          <CardTitle>{payload.purpose}</CardTitle>
          <p className="text-supporting text-content-secondary">
            Responsible role: {payload.responsibleRole}
          </p>
        </CardHeader>
        <CardContent>
          <ol className="space-y-5">
            {(payload.steps ?? []).map((step, index) => (
              <li key={step.key} className="grid gap-4 sm:grid-cols-[2.5rem_1fr]">
                <span className="grid size-9 place-items-center rounded-full border border-border-default bg-surface-subtle font-mono text-supporting">
                  {index + 1}
                </span>
                <div className="rounded-xl border border-border-default p-5">
                  <h2 className="text-section-title">{step.title}</h2>
                  <p className="mt-3 whitespace-pre-wrap text-body text-content-secondary">
                    {step.instruction}
                  </p>
                  <Separator className="my-4" />
                  <Definition label="Expected result" value={step.expectedResult} />
                  <div className="mt-3">
                    <Definition label="Check" value={step.check} />
                  </div>
                  {step.escalation ? (
                    <Alert className="mt-4">
                      <CircleAlert aria-hidden="true" />
                      <AlertTitle>Escalation</AlertTitle>
                      <AlertDescription>{step.escalation}</AlertDescription>
                    </Alert>
                  ) : null}
                </div>
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>Completion</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-body text-content-secondary">{payload.completionOutcome}</p>
            <Button
              className="mt-5 w-full"
              disabled={acknowledge.isPending}
              onClick={() =>
                acknowledge.mutate({
                  path: { assetId, releaseId: release.id! },
                })
              }
            >
              <Check aria-hidden="true" />
              Acknowledge exact release
            </Button>
          </CardContent>
        </Card>
        <FeedbackCard assetId={assetId} release={release} />
      </div>
    </div>
  )
}

function PackPanel({ assetId, release }: { assetId: string; release: Release }) {
  const payload = parsePayload<PackPayload>(release.payload)
  if (!payload) return <InvalidPayload />
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
      <Card>
        <CardHeader>
          <CardTitle>{payload.expectedOutcome}</CardTitle>
          <p className="text-supporting text-content-secondary">{payload.audience}</p>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 sm:grid-cols-3">
            <Metadata label="Purpose" value={payload.purpose} />
            <Metadata label="Pinned items" value={String(payload.items?.length ?? 0)} />
            <Metadata
              label="Required"
              value={String(payload.items?.filter((item) => item.required).length ?? 0)}
            />
          </div>
          <Button asChild className="mt-6">
            <Link
              to="/assets/$assetId/packs/$releaseId"
              params={{ assetId, releaseId: release.id! }}
            >
              Start or resume journey
              <ArrowRight aria-hidden="true" />
            </Link>
          </Button>
        </CardContent>
      </Card>
      <FeedbackCard assetId={assetId} release={release} />
    </div>
  )
}

function FeedbackCard({ assetId, release }: { assetId: string; release: Release }) {
  const [type, setType] = useState<"HELPFUL" | "OUTDATED" | "INCORRECT" | "OTHER">("HELPFUL")
  const [comment, setComment] = useState("")
  const submit = useMutation({
    ...submitAssistantAssetFeedbackMutation(),
    onSuccess: () => {
      setComment("")
      toast.success("Feedback sent to the Asset owner")
    },
  })
  return (
    <Card>
      <CardHeader>
        <CardTitle>Release feedback</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Select value={type} onValueChange={(value: string) => setType(value as typeof type)}>
          <SelectTrigger aria-label="Feedback type">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="HELPFUL">Helpful</SelectItem>
            <SelectItem value="OUTDATED">Outdated</SelectItem>
            <SelectItem value="INCORRECT">Incorrect</SelectItem>
            <SelectItem value="OTHER">Other</SelectItem>
          </SelectContent>
        </Select>
        <Textarea
          value={comment}
          onChange={(event) => setComment(event.currentTarget.value)}
          placeholder="What should the owner know?"
          rows={3}
        />
        <Button
          variant="outline"
          className="w-full"
          disabled={!comment.trim() || submit.isPending}
          onClick={() =>
            submit.mutate({
              path: { assetId, releaseId: release.id! },
              body: { type, comment, confirmed: true },
            })
          }
        >
          <Send aria-hidden="true" />
          {submit.isPending ? "Sending feedback..." : "Send feedback"}
        </Button>
        {submit.isSuccess ? (
          <AssistantActionReceipt
            action="Submit release feedback"
            status="complete"
            summary="Feedback stored for the Asset owner"
            traceId={submit.data.traceId}
            releaseDigest={release.digest}
          />
        ) : null}
      </CardContent>
    </Card>
  )
}

function Definition({ label, value }: { label: string; value?: string }) {
  return (
    <div>
      <dt className="text-metadata font-medium uppercase tracking-wide text-content-muted">
        {label}
      </dt>
      <dd className="mt-1 text-body text-content-primary">{value || "—"}</dd>
    </div>
  )
}

function InvalidPayload() {
  return (
    <Alert variant="destructive">
      <CircleAlert aria-hidden="true" />
      <AlertTitle>Release payload cannot be displayed</AlertTitle>
      <AlertDescription>
        The immutable release is still pinned, but this client cannot parse its profile.
      </AlertDescription>
    </Alert>
  )
}

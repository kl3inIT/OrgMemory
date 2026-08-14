import { useMutation, useQuery } from "@tanstack/react-query"
import { Rocket, Save } from "lucide-react"
import { useMemo, useState, type FormEvent } from "react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { PromptTemplateEditor } from "@/features/assets/components/prompt-template-editor"
import { GovernanceDecisionDialog } from "@/features/assets/components/governance-decision-dialog"
import {
  buildPromptAssetDraft,
  parsePromptDraft,
  type PromptTemplateFormValue,
} from "@/features/assets/prompt-template-form"
import { apiErrorMessage } from "@/lib/api-error"
import type { AssetGovernanceActions, AssetView, Draft } from "@/lib/hey-api"
import {
  listKnowledgeSpaceUploadTargetsOptions,
  publishAssetDraftMutation,
  updateAssetDraftMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

export function PromptDraftWorkspace({
  asset,
  actions,
  canPublish,
  onChanged,
  onPublished,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  canPublish: boolean
  onChanged: () => Promise<unknown>
  onPublished: () => void
}) {
  const draft = asset.draft!
  const parsed = useMemo(() => parsePromptDraft(draft.payload, draft), [draft])
  const spaces = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const canEdit = Boolean(actions?.canEdit)
  const [value, setValue] = useState<PromptTemplateFormValue>(() =>
    parsed.kind === "text" ? withPlacement(parsed.value, asset) : fallback(asset, draft),
  )
  const [error, setError] = useState<string>()
  const [refreshError, setRefreshError] = useState<string>()
  const [refreshIntent, setRefreshIntent] = useState<"save" | "publish">()
  const [refreshing, setRefreshing] = useState(false)
  const [versionLabel, setVersionLabel] = useState("")
  const update = useMutation(updateAssetDraftMutation())
  const publish = useMutation(publishAssetDraftMutation())

  async function refreshAfterMutation(intent: "save" | "publish") {
    try {
      await onChanged()
      setRefreshError(undefined)
      setRefreshIntent(undefined)
      return true
    } catch {
      setRefreshIntent(intent)
      setRefreshError(
        intent === "publish"
          ? "The release was published, but the latest release history could not be loaded."
          : "The working copy was saved, but the latest revision could not be loaded.",
      )
      return false
    }
  }

  async function retryRefresh() {
    const intent = refreshIntent
    if (!intent) return
    setRefreshing(true)
    const refreshed = await refreshAfterMutation(intent)
    setRefreshing(false)
    if (refreshed && intent === "publish") onPublished()
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canEdit) {
      setError("You no longer have permission to edit this working copy.")
      return
    }
    const built = buildPromptAssetDraft(value)
    if (!built.ok) {
      setError(built.message)
      return
    }
    setError(undefined)
    setRefreshError(undefined)
    try {
      await update.mutateAsync({
        path: { assetId: asset.id! },
        body: { expectedLockVersion: draft.lockVersion ?? 0, ...built.update },
      })
    } catch (failure) {
      setError(
        apiErrorMessage(
          failure,
          "The working copy changed or could not be saved. Your local content is still here; copy it before reloading.",
        ),
      )
      return
    }
    toast.success("Prompt working copy saved")
    await refreshAfterMutation("save")
  }

  async function publishRelease() {
    setError(undefined)
    setRefreshError(undefined)
    try {
      await publish.mutateAsync({
        path: { assetId: asset.id! },
        body: { versionLabel: versionLabel.trim() },
      })
    } catch (failure) {
      setError(apiErrorMessage(failure, "The working copy could not be published."))
      return
    }
    setVersionLabel("")
    toast.success("Immutable Release published")
    if (await refreshAfterMutation("publish")) onPublished()
  }

  const publishCard = canPublish ? (
    <Card className="gap-0 bg-surface-raised py-0 shadow-none">
      <CardHeader className="border-b border-border-subtle px-5 py-4">
        <CardTitle>Publish release</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 p-5">
        <p className="text-xs leading-5 text-content-muted">
          Snapshots the saved working copy. Publication does not change sharing.
        </p>
        <div className="space-y-2">
          <Label htmlFor="prompt-version-label">Version</Label>
          <Input
            id="prompt-version-label"
            value={versionLabel}
            className="font-mono"
            placeholder="1.0.0"
            onChange={(event) => setVersionLabel(event.currentTarget.value)}
          />
        </div>
        <GovernanceDecisionDialog
          label="Publish release"
          description={`Publish the saved working copy as immutable version ${versionLabel.trim() || "—"} with DIRECT provenance?`}
          disabled={!versionLabel.trim() || publish.isPending || update.isPending || refreshing || Boolean(refreshError)}
          icon={Rocket}
          onConfirm={() => void publishRelease()}
        />
      </CardContent>
    </Card>
  ) : null

  if (parsed.kind === "messages") {
    return (
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_23rem]">
        <Card className="gap-0 bg-surface-raised py-0 shadow-none">
          <CardHeader className="border-b border-border-subtle px-5 py-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div><Badge variant="outline">Message Prompt</Badge><CardTitle className="mt-3">{draft.title}</CardTitle></div>
              <Badge variant="outline">Read only</Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-4 p-5">
            <Alert>
              <AlertTitle>Message editing is not supported yet</AlertTitle>
              <AlertDescription>
                This working copy remains valid and can be published unchanged. The text editor will not replace its message payload.
              </AlertDescription>
            </Alert>
            {parsed.messages.map((message, index) => (
              <section key={`${message.role}-${index}`} className="border-l-2 border-border-default pl-4">
                <p className="font-mono text-metadata text-content-muted">{message.role}</p>
                <pre className="mt-2 whitespace-pre-wrap font-sans text-body text-content-primary">{message.content}</pre>
              </section>
            ))}
          </CardContent>
        </Card>
        <aside className="space-y-5">{error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}{publishCard}</aside>
      </div>
    )
  }

  if (parsed.kind === "invalid") {
    return (
      <Alert variant="destructive">
        <AlertTitle>Prompt working copy cannot be edited</AlertTitle>
        <AlertDescription>The saved payload is invalid or unsupported. No changes were made.</AlertDescription>
      </Alert>
    )
  }

  const targetError = spaces.isError ? "Creation targets could not be loaded." : undefined
  const accessError = canEdit ? undefined : "You can view this working copy but cannot edit it."
  const editorError = error ?? refreshError ?? targetError ?? accessError
  const refreshErrorAction = refreshError
    ? {
        label: refreshing ? "Retrying" : "Retry refresh",
        onClick: () => void retryRefresh(),
        disabled: refreshing,
      }
    : undefined
  const targetErrorAction = !error && !refreshError && spaces.isError
    ? {
        label: spaces.isFetching ? "Retrying" : "Try again",
        onClick: () => void spaces.refetch(),
        disabled: spaces.isFetching,
      }
    : undefined

  return (
    <PromptTemplateEditor
      value={value}
      onChange={setValue}
      onSubmit={save}
      submitLabel={update.isPending ? "Saving working copy" : "Save working copy"}
      submitting={update.isPending}
      disabled={!canEdit || Boolean(refreshError)}
      error={editorError}
      errorAction={refreshErrorAction ?? targetErrorAction}
      spaces={spaces.data}
      spacesLoading={spaces.isPending}
      placementLocked
      asideAction={publishCard ? <div className="space-y-2"><div className="flex items-center gap-2 text-xs text-content-muted"><Save className="size-3.5" aria-hidden="true" />Publish always uses the last successful save.</div>{publishCard}</div> : null}
    />
  )
}

function withPlacement(value: PromptTemplateFormValue, asset: AssetView): PromptTemplateFormValue {
  return {
    ...value,
    namespace: asset.namespace ?? "",
    slug: asset.slug ?? "",
    knowledgeSpaceId: asset.knowledgeSpaceId ?? "",
  }
}

function fallback(asset: AssetView, draft: Draft): PromptTemplateFormValue {
  return {
    title: draft.title ?? "",
    summary: draft.summary ?? "",
    namespace: asset.namespace ?? "",
    slug: asset.slug ?? "",
    knowledgeSpaceId: asset.knowledgeSpaceId ?? "",
    classification: "INTERNAL",
    objective: "",
    audience: "",
    textTemplate: "",
    variables: [],
    evaluationCases: [],
    grounding: "NONE",
    knowledgeRequirements: "",
    useWhen: "",
    doNotUseWhen: "",
    knownLimitations: "",
    outputContract: "",
  }
}

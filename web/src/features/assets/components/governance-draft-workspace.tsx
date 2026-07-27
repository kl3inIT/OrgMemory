import { useMutation } from "@tanstack/react-query"
import { Archive, Send } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { formatDate, parsePayload } from "@/features/assets/asset-format"
import { GovernanceDecisionDialog } from "@/features/assets/components/governance-decision-dialog"
import { submitAssetRevisionMutation } from "@/lib/hey-api/@tanstack/react-query.gen"
import type {
  AssetGovernanceActions,
  AssetView,
  Draft,
} from "@/lib/hey-api"

type SkillDraftPayload = {
  compatibility?: string
  allowedTools?: string
  artifact?: {
    sha256?: string
    contentLength?: number
  }
  files?: Array<{
    path?: string
    size?: number
  }>
}

export function GovernanceDraftWorkspace({
  asset,
  actions,
  onChanged,
  onSubmitted,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
  onSubmitted: () => void
}) {
  const draft = asset.draft!
  const [changeNote, setChangeNote] = useState("")
  const submit = useMutation({
    ...submitAssetRevisionMutation(),
    onSuccess: async () => {
      setChangeNote("")
      await onChanged()
      onSubmitted()
      toast.success("Draft submitted for exact-digest review")
    },
    onError: () => toast.error("The Draft could not be submitted"),
  })
  const canSubmit = Boolean(actions?.canSubmitReview)

  return (
    <div className={canSubmit ? "grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]" : ""}>
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <Badge variant="outline">Draft</Badge>
                <CardTitle className="mt-3">{draft.title}</CardTitle>
              </div>
              <p className="text-supporting text-content-secondary">
                Updated {formatDate(draft.updatedAt)}
              </p>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-body text-content-secondary">{draft.summary}</p>
            <div className="mt-6 grid gap-px overflow-hidden rounded-xl border border-border-default bg-border-default sm:grid-cols-3">
              <DraftMetric label="Classification" value={draft.classification} />
              <DraftMetric label="Schema" value={draft.schemaVersion} mono />
              <DraftMetric
                label="Draft version"
                value={String(draft.lockVersion ?? 0)}
                mono
              />
            </div>
          </CardContent>
        </Card>
        {asset.type === "SKILL" ? <SkillDraftPackage draft={draft} /> : null}
      </div>

      {canSubmit ? (
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Submit for review</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="draft-change-note">Change note</Label>
              <Textarea
                id="draft-change-note"
                value={changeNote}
                onChange={(event) => setChangeNote(event.currentTarget.value)}
                rows={5}
                placeholder="What should the reviewer verify?"
              />
            </div>
            <GovernanceDecisionDialog
              label="Submit for review"
              description="This creates an immutable revision from the current Draft and opens an exact-digest review case."
              disabled={!changeNote.trim() || submit.isPending}
              icon={Send}
              onConfirm={() =>
                submit.mutate({
                  path: { assetId: asset.id! },
                  body: { changeNote: changeNote.trim() },
                })
              }
            />
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

function SkillDraftPackage({ draft }: { draft: Draft }) {
  const skill = parsePayload<SkillDraftPayload>(draft.payload)
  if (!skill?.artifact) {
    return (
      <Card className="border-dashed">
        <CardContent className="p-8">
          <p className="text-label">Skill package metadata is unavailable</p>
        </CardContent>
      </Card>
    )
  }
  const files = skill.files ?? []
  const visibleFiles = files.slice(0, 8)

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Archive className="size-5 text-content-muted" aria-hidden="true" />
          <CardTitle>Skill package</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="grid gap-px overflow-hidden rounded-xl border border-border-default bg-border-default sm:grid-cols-3">
          <DraftMetric label="Files" value={String(files.length)} mono />
          <DraftMetric
            label="Archive"
            value={formatBytes(skill.artifact.contentLength)}
          />
          <DraftMetric
            label="Compatibility"
            value={skill.compatibility || "Not declared"}
          />
        </div>
        <div>
          <p className="text-metadata text-content-muted">Package SHA-256</p>
          <p className="mt-2 break-all font-mono text-metadata">
            {skill.artifact.sha256}
          </p>
        </div>
        {skill.allowedTools ? (
          <div>
            <p className="text-metadata text-content-muted">
              Declared tools (portability metadata only)
            </p>
            <p className="mt-2 font-mono text-metadata">{skill.allowedTools}</p>
          </div>
        ) : null}
        <div className="overflow-hidden rounded-xl border border-border-default">
          {visibleFiles.map((file, index) => (
            <div
              key={`${file.path ?? "unnamed"}:${index}`}
              className="flex items-center justify-between gap-4 border-b border-border-default px-4 py-3 last:border-b-0"
            >
              <span className="min-w-0 truncate font-mono text-metadata">
                {file.path}
              </span>
              <span className="shrink-0 text-metadata text-content-muted">
                {formatBytes(file.size)}
              </span>
            </div>
          ))}
          {files.length > visibleFiles.length ? (
            <div className="px-4 py-3 text-metadata text-content-muted">
              {files.length - visibleFiles.length} more files
            </div>
          ) : null}
        </div>
      </CardContent>
    </Card>
  )
}

function DraftMetric({
  label,
  value,
  mono = false,
}: {
  label: string
  value?: string
  mono?: boolean
}) {
  return (
    <div className="bg-surface-subtle p-4">
      <p className="text-metadata text-content-muted">{label}</p>
      <p className={`mt-2 text-label ${mono ? "font-mono" : ""}`}>{value || "—"}</p>
    </div>
  )
}

function formatBytes(value?: number) {
  if (value === undefined) return "—"
  if (value < 1_024) return `${value} B`
  const kibibytes = value / 1_024
  if (kibibytes < 1_024) return `${kibibytes.toFixed(1)} KB`
  return `${(kibibytes / 1_024).toFixed(1)} MB`
}

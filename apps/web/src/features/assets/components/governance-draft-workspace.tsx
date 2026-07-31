import { useMutation } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { Archive, Pencil, Rocket, Send } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { parsePayload } from "@/features/assets/asset-format"
import { GovernanceDecisionDialog } from "@/features/assets/components/governance-decision-dialog"
import { canPublishSkillDirectly } from "@/features/assets/governance-policy"
import {
  publishSkillReleaseMutation,
  submitAssetRevisionMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type {
  AssetGovernanceActions,
  AssetView,
  Draft,
} from "@/lib/hey-api"
import { formatBytes, formatDate } from "@/lib/format"

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
  onPublished,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
  onSubmitted: () => void
  onPublished: () => void
}) {
  const draft = asset.draft!
  const [changeNote, setChangeNote] = useState("")
  const [versionLabel, setVersionLabel] = useState("")
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
  const publishSkill = useMutation({
    ...publishSkillReleaseMutation(),
    onSuccess: async () => {
      setVersionLabel("")
      await onChanged()
      onPublished()
      toast.success("Immutable Skill release published")
    },
    onError: () => toast.error("The Skill could not be published"),
  })
  const canPublishSkill = canPublishSkillDirectly(asset, actions)
  const canSubmit = Boolean(
    actions?.canSubmitReview &&
      (asset.type !== "SKILL" || !actions?.canPublishSkill),
  )
  const hasAction = canPublishSkill || canSubmit

  return (
    <div className={hasAction ? "grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]" : ""}>
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
        {asset.type === "SKILL" ? (
          <SkillDraftPackage
            assetId={asset.id!}
            draft={draft}
            canEdit={Boolean(actions?.canEdit)}
          />
        ) : null}
      </div>

      {canPublishSkill ? (
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Publish Skill</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-supporting text-content-secondary">
              Publish this validated package directly for people who already have access to the
              Asset.
            </p>
            <div className="space-y-2">
              <Label htmlFor="skill-version-label">Version</Label>
              <Input
                id="skill-version-label"
                value={versionLabel}
                onChange={(event) => setVersionLabel(event.currentTarget.value)}
                placeholder="1.0.0"
                className="font-mono"
              />
            </div>
            <GovernanceDecisionDialog
              label="Publish Skill"
              description="This creates an immutable release from the structurally validated package. No independent content review is recorded."
              disabled={!versionLabel.trim() || publishSkill.isPending}
              icon={Rocket}
              onConfirm={() =>
                publishSkill.mutate({
                  path: { assetId: asset.id! },
                  body: { versionLabel: versionLabel.trim() },
                })
              }
            />
          </CardContent>
        </Card>
      ) : canSubmit ? (
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

function SkillDraftPackage({
  assetId,
  draft,
  canEdit,
}: {
  assetId: string
  draft: Draft
  canEdit: boolean
}) {
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
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Archive className="size-5 text-content-muted" aria-hidden="true" />
            <CardTitle>Skill package</CardTitle>
          </div>
          {canEdit ? (
            <Button variant="outline" size="sm" asChild>
              <Link to="/assets/$assetId/skill-package" params={{ assetId }}>
                <Pencil aria-hidden="true" />Edit package
              </Link>
            </Button>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="grid gap-px overflow-hidden rounded-xl border border-border-default bg-border-default sm:grid-cols-3">
          <DraftMetric label="Files" value={String(files.length)} mono />
          <DraftMetric
            label="Archive"
            value={formatBytes(skill.artifact.contentLength, "—")}
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
                {formatBytes(file.size, "—")}
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

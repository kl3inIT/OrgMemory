import { useMutation } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { Archive, Pencil, Rocket } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { parsePayload } from "@/features/assets/asset-format"
import { GovernanceDecisionDialog } from "@/features/assets/components/governance-decision-dialog"
import { MetadataTile } from "@/features/assets/components/metadata-tile"
import { PromptDraftWorkspace } from "@/features/assets/components/prompt-draft-workspace"
import { canPublishDirectly } from "@/features/assets/governance-policy"
import {
  publishAssetDraftMutation,
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
  onPublished,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
  onPublished: () => void
}) {
  if (asset.type === "PROMPT_TEMPLATE") {
    return (
      <PromptDraftWorkspace
        asset={asset}
        actions={actions}
        onChanged={onChanged}
        onPublished={onPublished}
      />
    )
  }
  return (
    <GenericDraftWorkspace
      asset={asset}
      actions={actions}
      onChanged={onChanged}
      onPublished={onPublished}
    />
  )
}

function GenericDraftWorkspace({
  asset,
  actions,
  onChanged,
  onPublished,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
  onPublished: () => void
}) {
  const draft = asset.draft!
  const [versionLabel, setVersionLabel] = useState("")
  const publish = useMutation({
    ...publishAssetDraftMutation(),
    onSuccess: async () => {
      setVersionLabel("")
      await onChanged()
      onPublished()
      toast.success("Immutable Release published")
    },
    onError: () => toast.error("The working copy could not be published"),
  })
  const canPublish = canPublishDirectly(asset, actions)

  return (
    <div className={canPublish ? "grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]" : ""}>
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <Badge variant="outline">Working copy</Badge>
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
              <MetadataTile label="Classification" value={draft.classification} />
              <MetadataTile label="Schema" value={draft.schemaVersion} mono />
              <MetadataTile
                label="Working copy version"
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

      {canPublish ? (
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Publish update</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-supporting text-content-secondary">
              Snapshot the current working copy into an immutable Release. Existing Releases do
              not change.
            </p>
            <div className="space-y-2">
              <Label htmlFor="asset-version-label">Version</Label>
              <Input
                id="asset-version-label"
                value={versionLabel}
                onChange={(event) => setVersionLabel(event.currentTarget.value)}
                placeholder="1.0.0"
                className="font-mono"
              />
            </div>
            <GovernanceDecisionDialog
              label="Publish update"
              description="This creates an immutable Release directly from the current working copy."
              disabled={!versionLabel.trim() || publish.isPending}
              icon={Rocket}
              onConfirm={() =>
                publish.mutate({
                  path: { assetId: asset.id! },
                  body: { versionLabel: versionLabel.trim() },
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
          <MetadataTile label="Files" value={String(files.length)} mono />
          <MetadataTile
            label="Archive"
            value={formatBytes(skill.artifact.contentLength, "—")}
          />
          <MetadataTile
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

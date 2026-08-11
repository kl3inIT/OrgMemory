import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  CircleAlert,
  GitCompareArrows,
  History,
  Rocket,
  TriangleAlert,
} from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { formatAssetCoordinate } from "@/features/assets/asset-format"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import { AssetBreadcrumb } from "@/features/assets/components/asset-breadcrumb"
import { AssetPageError, AssetPageLoading } from "@/features/assets/components/asset-state"
import { GovernanceDecisionDialog } from "@/features/assets/components/governance-decision-dialog"
import { GovernanceDraftWorkspace } from "@/features/assets/components/governance-draft-workspace"
import {
  initialGovernanceTab,
  type GovernanceTab,
} from "@/features/assets/governance-policy"
import {
  decideAssetReviewMutation,
  getAssetGovernanceActionsOptions,
  getAssetGovernanceActionsQueryKey,
  getAssetOptions,
  getAssetQueryKey,
  publishAssetReleaseMutation,
  shareAssetMutation,
  unshareAssetMutation,
  transferAssetOwnershipMutation,
  contextOptions,
  withdrawAssetMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type {
  AssetGovernanceActions,
  AssetView,
  Release,
  Review,
  Revision,
} from "@/lib/hey-api"
import { formatDate } from "@/lib/format"

export function GovernanceWorkspacePage({
  assetId,
  actorKey,
}: {
  assetId: string
  actorKey: string
}) {
  const assetOptions = getAssetOptions({ path: { assetId } })
  const asset = useQuery({
    ...assetOptions,
    queryKey: scopeAssetQueryKey(assetOptions.queryKey, actorKey),
  })
  const actionOptions = getAssetGovernanceActionsOptions({ path: { assetId } })
  const actions = useQuery({
    ...actionOptions,
    queryKey: scopeAssetQueryKey(actionOptions.queryKey, actorKey),
  })
  const [selectedTab, setSelectedTab] = useState<GovernanceTab>()
  const queryClient = useQueryClient()
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: scopeAssetQueryKey(
          getAssetQueryKey({ path: { assetId } }),
          actorKey,
        ),
      }),
      queryClient.invalidateQueries({
        queryKey: scopeAssetQueryKey(
          getAssetGovernanceActionsQueryKey({ path: { assetId } }),
          actorKey,
        ),
      }),
    ])
  }

  if (asset.isPending) return <AssetPageLoading />
  if (asset.isError || !asset.data?.id) {
    return (
      <AssetPageError
        title="Governance workspace is unavailable"
        onRetry={() => void asset.refetch()}
      />
    )
  }
  const activeTab = selectedTab ?? initialGovernanceTab(asset.data)
  const showReviewTab = Boolean(asset.data.reviews?.length)

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Asset workspace"
        description={
          <span className="font-mono text-metadata">{formatAssetCoordinate(asset.data)}</span>
        }
        breadcrumb={
          <AssetBreadcrumb
            assetId={assetId}
            assetTitle={asset.data.draft?.title}
            current="Manage"
          />
        }
        metadata={
          <div className="flex items-center gap-2">
            <Badge variant="outline">{asset.data.type}</Badge>
            <Badge variant="outline">{asset.data.portfolioState}</Badge>
          </div>
        }
      />

      <PageLayout.Body>
        <AssetSharingPanel
          asset={asset.data}
          actions={actions.data}
          onChanged={refresh}
        />
        <Tabs
          value={activeTab}
          onValueChange={(value: string) => setSelectedTab(value as GovernanceTab)}
          className="gap-6"
        >
          <PageLayout.Tabs>
            <TabsList aria-label="Governance sections">
              {asset.data.draft ? <TabsTrigger value="draft">Working copy</TabsTrigger> : null}
              <TabsTrigger value="changes">Revision history</TabsTrigger>
              {showReviewTab ? <TabsTrigger value="review">Legacy reviews</TabsTrigger> : null}
              <TabsTrigger value="releases">Release history</TabsTrigger>
            </TabsList>
          </PageLayout.Tabs>
          {asset.data.draft ? (
            <TabsContent value="draft">
              <GovernanceDraftWorkspace
                asset={asset.data}
                actions={actions.data}
                onChanged={refresh}
                onPublished={() => setSelectedTab("releases")}
              />
            </TabsContent>
          ) : null}
          <TabsContent value="changes">
            <RevisionDiff asset={asset.data} />
          </TabsContent>
          <TabsContent value="review">
            <ReviewWorkspace
              asset={asset.data}
              actions={actions.data}
              onChanged={refresh}
            />
          </TabsContent>
          <TabsContent value="releases">
            <ReleaseHistory
              asset={asset.data}
              actions={actions.data}
              onChanged={refresh}
            />
          </TabsContent>
        </Tabs>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

function AssetSharingPanel({
  asset,
  actions,
  onChanged,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
}) {
  const [principalType, setPrincipalType] = useState<"user" | "group" | "organization">("user")
  const [principalId, setPrincipalId] = useState("")
  const [role, setRole] = useState<"VIEWER" | "EDITOR">("VIEWER")
  const [nextOwnerUserId, setNextOwnerUserId] = useState("")
  const organization = useQuery(contextOptions())
  const share = useMutation({
    ...shareAssetMutation(),
    onSuccess: async () => {
      setPrincipalId("")
      await onChanged()
      toast.success("Asset access updated")
    },
    onError: () => toast.error("Asset access could not be updated"),
  })
  const unshare = useMutation({
    ...unshareAssetMutation(),
    onSuccess: async () => {
      await onChanged()
      toast.success("Asset access removed")
    },
    onError: () => toast.error("Asset access could not be removed"),
  })
  const transfer = useMutation({
    ...transferAssetOwnershipMutation(),
    onSuccess: async () => {
      setNextOwnerUserId("")
      await onChanged()
      toast.success("Ownership transferred")
    },
    onError: () => toast.error("Ownership could not be transferred"),
  })
  const activeShares = (asset.roleAssignments ?? []).filter(
    (assignment) =>
      !assignment.validUntil && (assignment.role === "VIEWER" || assignment.role === "EDITOR"),
  )

  return (
    <Card className="mb-6">
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Ownership and access</CardTitle>
            <p className="mt-1 text-supporting text-content-secondary">
              One owner controls publishing and sharing. Editors change only the working copy;
              Viewers receive immutable Releases.
            </p>
          </div>
          <div className="flex gap-2">
            <Badge variant="outline">{asset.sharingState ?? "PRIVATE"}</Badge>
            <Badge variant="outline" className="font-mono">
              Owner {asset.ownerUserId?.slice(0, 8) ?? "vacant"}
            </Badge>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        {activeShares.length ? (
          <div className="divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle">
            {activeShares.map((assignment) => (
              <div key={assignment.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="truncate font-mono text-metadata text-content-primary">
                    {assignment.principalType}:{assignment.principalId}
                  </p>
                  <p className="text-metadata text-content-muted">{assignment.role}</p>
                </div>
                {actions?.canManageSharing ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    disabled={unshare.isPending}
                    onClick={() =>
                      unshare.mutate({
                        path: { assetId: asset.id! },
                        body: {
                          principalType: assignment.principalType,
                          principalId: assignment.principalId,
                          role: assignment.role,
                        },
                      })
                    }
                  >
                    Remove
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <p className="rounded-lg border border-dashed border-border-default px-4 py-3 text-supporting text-content-secondary">
            Private — no Viewer or Editor audience yet.
          </p>
        )}

        {actions?.canManageSharing ? (
          <div className="grid gap-3 lg:grid-cols-[10rem_10rem_minmax(16rem,1fr)_auto]">
            <Select
              value={principalType}
              onValueChange={(value) => {
                const type = value as "user" | "group" | "organization"
                setPrincipalType(type)
                setPrincipalId(type === "organization" ? (organization.data?.organizationId ?? "") : "")
                if (type === "organization") setRole("VIEWER")
              }}
            >
              <SelectTrigger aria-label="Audience type"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="user">Person</SelectItem>
                <SelectItem value="group">Group</SelectItem>
                <SelectItem value="organization">Company</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={role}
              onValueChange={(value) => setRole(value as "VIEWER" | "EDITOR")}
              disabled={principalType === "organization"}
            >
              <SelectTrigger aria-label="Access role"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="VIEWER">Viewer</SelectItem>
                <SelectItem value="EDITOR">Editor</SelectItem>
              </SelectContent>
            </Select>
            {principalType === "user" ? (
              <Select value={principalId} onValueChange={setPrincipalId}>
                <SelectTrigger aria-label="Person"><SelectValue placeholder="Choose a person" /></SelectTrigger>
                <SelectContent>
                  {(organization.data?.users ?? []).map((user) => (
                    <SelectItem key={user.id} value={user.id!}>
                      {user.name ?? user.email ?? user.id}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : principalType === "group" ? (
              <Input
                aria-label="Group identifier"
                placeholder="Group UUID"
                value={principalId}
                onChange={(event) => setPrincipalId(event.currentTarget.value)}
              />
            ) : (
              <Input
                aria-label="Company identifier"
                value={principalId}
                readOnly
                className="font-mono"
              />
            )}
            <Button
              type="button"
              disabled={!principalId.trim() || share.isPending}
              onClick={() =>
                share.mutate({
                  path: { assetId: asset.id! },
                  body: {
                    principalType,
                    principalId: principalId.trim(),
                    role: principalType === "organization" ? "VIEWER" : role,
                  },
                })
              }
            >
              Share
            </Button>
          </div>
        ) : null}

        {actions?.canTransferOwnership ? (
          <div className="grid gap-3 border-t border-border-subtle pt-5 sm:grid-cols-[minmax(16rem,1fr)_auto]">
            <Select value={nextOwnerUserId} onValueChange={setNextOwnerUserId}>
              <SelectTrigger aria-label="Next owner"><SelectValue placeholder="Choose the next owner" /></SelectTrigger>
              <SelectContent>
                {(organization.data?.users ?? [])
                  .filter((user) => user.id && user.id !== asset.ownerUserId)
                  .map((user) => (
                    <SelectItem key={user.id} value={user.id!}>
                      {user.name ?? user.email ?? user.id}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant="outline"
              disabled={!nextOwnerUserId.trim() || transfer.isPending}
              onClick={() =>
                transfer.mutate({
                  path: { assetId: asset.id! },
                  body: { nextOwnerUserId: nextOwnerUserId.trim() },
                })
              }
            >
              Transfer ownership
            </Button>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

function RevisionDiff({ asset }: { asset: AssetView }) {
  const revisions = asset.revisions ?? []
  const [leftId, setLeftId] = useState(revisions.at(1)?.id ?? revisions.at(0)?.id)
  const [rightId, setRightId] = useState(revisions.at(0)?.id)
  const left = revisions.find((revision) => revision.id === leftId)
  const right = revisions.find((revision) => revision.id === rightId)
  const lines = useMemo(() => lineDiff(left?.payload ?? "", right?.payload ?? ""), [left, right])

  if (revisions.length === 0) {
    return <EmptyGovernance title="No submitted revisions" />
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[18rem_minmax(0,1fr)]">
      <Card>
        <CardHeader>
          <CardTitle>Compare revisions</CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <RevisionSelect
            label="Before"
            revisions={revisions}
            value={leftId}
            onChange={setLeftId}
          />
          <RevisionSelect
            label="After"
            revisions={revisions}
            value={rightId}
            onChange={setRightId}
          />
          <div className="rounded-lg bg-surface-subtle p-4">
            <p className="text-metadata text-content-muted">Digest-bound review</p>
            <p className="mt-2 font-mono text-metadata">{right?.digest?.slice(0, 20) ?? "—"}</p>
          </div>
        </CardContent>
      </Card>
      <Card className="min-w-0">
        <CardHeader>
          <div className="flex items-center gap-2">
            <GitCompareArrows className="size-5 text-content-muted" aria-hidden="true" />
            <CardTitle>Payload diff</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <pre
            className="max-h-[38rem] overflow-auto rounded-xl border border-border-default bg-surface-sunken p-4 font-mono text-metadata"
            aria-label="Revision payload diff"
          >
            {lines.map((line, index) => (
              <span
                key={`${index}:${line.text}`}
                className={`block min-w-max px-2 ${
                  line.kind === "added"
                    ? "bg-status-success-surface text-status-success-content"
                    : line.kind === "removed"
                      ? "bg-status-danger-surface text-status-danger-content"
                      : "text-content-secondary"
                }`}
              >
                {line.kind === "added" ? "+ " : line.kind === "removed" ? "− " : "  "}
                {line.text || " "}
              </span>
            ))}
          </pre>
        </CardContent>
      </Card>
    </div>
  )
}

function RevisionSelect({
  label,
  revisions,
  value,
  onChange,
}: {
  label: string
  revisions: Revision[]
  value?: string
  onChange: (value: string) => void
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={`revision-${label}`}>{label}</Label>
      <select
        id={`revision-${label}`}
        value={value}
        onChange={(event) => onChange(event.currentTarget.value)}
        className="h-9 w-full rounded-md border border-control-border bg-control-surface px-3 text-label outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
      >
        {revisions.map((revision) => (
          <option key={revision.id} value={revision.id}>
            r{revision.sequence} · {revision.digest?.slice(0, 8)}
          </option>
        ))}
      </select>
    </div>
  )
}

function ReviewWorkspace({
  asset,
  actions,
  onChanged,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
}) {
  const reviews = asset.reviews ?? []
  const [versionLabel, setVersionLabel] = useState("")
  const decide = useMutation({
    ...decideAssetReviewMutation(),
    onSuccess: async () => {
      await onChanged()
      toast.success("Review decision recorded against the exact digest")
    },
  })
  const publish = useMutation({
    ...publishAssetReleaseMutation(),
    onSuccess: async () => {
      setVersionLabel("")
      await onChanged()
      toast.success("Immutable release published")
    },
  })

  if (reviews.length === 0) return <EmptyGovernance title="No review cases yet" />

  return (
    <div className="space-y-5">
      {reviews.map((review) => {
        const open = review.state === "IN_REVIEW"
        const canApprove = Boolean(open && actions?.canApprove)
        const canRequestChanges = Boolean(open && actions?.canRequestChanges)
        const canReject = Boolean(open && actions?.canReject)
        const canCancel = Boolean(open && actions?.canCancel)
        const decidable = canApprove || canRequestChanges || canReject || canCancel
        const publishable = Boolean(
          review.state === "APPROVED" &&
            review.revisionId &&
            actions?.canPublish,
        )
        return (
          <Card key={review.id}>
            <CardContent
              className={
                decidable || publishable
                  ? "grid gap-6 p-6 lg:grid-cols-[1fr_auto] lg:items-start"
                  : "p-6"
              }
            >
              <div>
                <div className="flex flex-wrap gap-2">
                  <Badge className={reviewTone(review.state)}>{review.state}</Badge>
                  <Badge variant="outline">policy {review.policyVersion}</Badge>
                </div>
                <h2 className="mt-4 text-section-title">
                  Revision {review.revisionId?.slice(0, 8)}
                </h2>
                <p className="mt-2 font-mono text-metadata text-content-muted">
                  digest {review.revisionDigest}
                </p>
                <p className="mt-2 text-supporting text-content-secondary">
                  Requested {formatDate(review.createdAt)}
                </p>
                {review.decisions?.length ? (
                  <div className="mt-5 space-y-3 border-l border-border-default pl-4">
                    {review.decisions.map((decision, index) => (
                      <div key={`${decision.reviewerUserId}:${index}`}>
                        <p className="text-label">{decision.decision}</p>
                        <p className="text-supporting text-content-secondary">
                          {decision.comment}
                        </p>
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>
              <div className="grid min-w-64 gap-3">
                {canApprove ? (
                    <GovernanceDecisionDialog
                      label="Approve exact digest"
                      description="This records approval only for the immutable digest shown on this review case."
                      onConfirm={() =>
                        decide.mutate({
                          path: { assetId: asset.id!, reviewCaseId: review.id! },
                          body: {
                            decision: "APPROVE",
                            comment: "Approved from governance workspace",
                          },
                        })
                      }
                    />
                ) : null}
                {canRequestChanges ? (
                    <GovernanceDecisionDialog
                      label="Request changes"
                      description="The submitted revision remains immutable. The author must create a new draft change."
                      variant="outline"
                      onConfirm={() =>
                        decide.mutate({
                          path: { assetId: asset.id!, reviewCaseId: review.id! },
                          body: {
                            decision: "REQUEST_CHANGES",
                            comment: "Changes requested from governance workspace",
                          },
                        })
                      }
                    />
                ) : null}
                {canReject ? (
                  <GovernanceDecisionDialog
                    label="Reject revision"
                    description="This rejects the submitted immutable revision and closes the review case."
                    variant="destructive"
                    onConfirm={() =>
                      decide.mutate({
                        path: { assetId: asset.id!, reviewCaseId: review.id! },
                        body: {
                          decision: "REJECT",
                          comment: "Rejected from governance workspace",
                        },
                      })
                    }
                  />
                ) : null}
                {canCancel ? (
                  <GovernanceDecisionDialog
                    label="Cancel review"
                    description="This closes the review request without changing the immutable revision."
                    variant="outline"
                    onConfirm={() =>
                      decide.mutate({
                        path: { assetId: asset.id!, reviewCaseId: review.id! },
                        body: {
                          decision: "CANCEL",
                          comment: "Cancelled from governance workspace",
                        },
                      })
                    }
                  />
                ) : null}
                {publishable ? (
                  <div className="space-y-3">
                    <Label htmlFor={`release-version-${review.id}`}>
                      Release version
                    </Label>
                    <Input
                      id={`release-version-${review.id}`}
                      value={versionLabel}
                      onChange={(event) => setVersionLabel(event.currentTarget.value)}
                      placeholder="1.0.0"
                    />
                    <GovernanceDecisionDialog
                      label="Publish release"
                      description="Publishing creates an immutable release from this exact approved revision."
                      disabled={!versionLabel.trim()}
                      icon={Rocket}
                      onConfirm={() =>
                        publish.mutate({
                          path: { assetId: asset.id! },
                          body: {
                            revisionId: review.revisionId,
                            versionLabel,
                          },
                        })
                      }
                    />
                  </div>
                ) : null}
              </div>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}

function ReleaseHistory({
  asset,
  actions,
  onChanged,
}: {
  asset: AssetView
  actions?: AssetGovernanceActions
  onChanged: () => Promise<unknown>
}) {
  const [reason, setReason] = useState("")
  const withdraw = useMutation({
    ...withdrawAssetMutation(),
    onSuccess: async () => {
      await onChanged()
      toast.success("Asset withdrawn and retired")
    },
  })
  if (!asset.releases?.length) return <EmptyGovernance title="No release history yet" />

  return (
    <div className="space-y-5">
      {actions?.canWithdraw && asset.portfolioState !== "RETIRED" ? (
        <Card className="border-status-danger-border">
          <CardContent className="grid gap-4 p-6 lg:grid-cols-[1fr_20rem]">
            <div>
              <h2 className="text-section-title">Withdraw Asset</h2>
              <p className="mt-2 text-supporting text-content-secondary">
                Withdraw every current Release and retire this Asset. Immutable history remains
                available here, but no new use can start.
              </p>
            </div>
            <div className="space-y-3">
              <Label htmlFor="asset-withdrawal-reason">Reason</Label>
              <Textarea
                id="asset-withdrawal-reason"
                value={reason}
                onChange={(event) => setReason(event.currentTarget.value)}
                rows={3}
                placeholder="Why is this Asset being withdrawn?"
              />
              <GovernanceDecisionDialog
                label="Withdraw Asset"
                description="This withdraws every non-withdrawn Release and permanently retires the Asset."
                variant="destructive"
                disabled={!reason.trim() || withdraw.isPending}
                icon={TriangleAlert}
                onConfirm={() =>
                  withdraw.mutate({
                    path: { assetId: asset.id! },
                    body: { reason: reason.trim() },
                  })
                }
              />
            </div>
          </CardContent>
        </Card>
      ) : null}
      {asset.releases.map((release) => (
        <Card key={release.id}>
          <CardContent className="p-6">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline" className="font-mono">
                  {release.versionLabel}
                </Badge>
                <Badge className={availabilityTone(release.availability)}>
                  {release.availability}
                </Badge>
                <Badge variant="outline">
                  {release.publicationMode === "DIRECT" ? "Direct" : "Reviewed"}
                </Badge>
              </div>
              <h2 className="mt-4 text-section-title">{release.title}</h2>
              <p className="mt-2 text-body text-content-secondary">{release.summary}</p>
              <p className="mt-3 font-mono text-metadata text-content-muted">
                {release.digest} · {formatDate(release.releasedAt)}
              </p>
              {release.availabilityHistory?.length ? (
                <div className="mt-5 space-y-3">
                  {release.availabilityHistory.map((event, index) => (
                    <div
                      key={`${event.effectiveAt}:${index}`}
                      className="flex gap-3 text-supporting"
                    >
                      <History className="mt-0.5 size-4 shrink-0 text-content-muted" />
                      <span>
                        {event.availability} · {event.reason} · {formatDate(event.effectiveAt)}
                      </span>
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function EmptyGovernance({ title }: { title: string }) {
  return (
    <Card className="border-dashed">
      <CardContent className="p-10 text-center">
        <CircleAlert className="mx-auto size-6 text-content-muted" aria-hidden="true" />
        <h2 className="mt-3 text-section-title">{title}</h2>
      </CardContent>
    </Card>
  )
}

function reviewTone(state?: Review["state"]) {
  if (state === "APPROVED") return "bg-status-success-surface text-status-success-content"
  if (state === "REJECTED" || state === "CANCELLED") {
    return "bg-status-danger-surface text-status-danger-content"
  }
  return "bg-status-warning-surface text-status-warning-content"
}

function availabilityTone(state?: Release["availability"]) {
  if (state === "AVAILABLE") return "bg-status-success-surface text-status-success-content"
  if (state === "WITHDRAWN") return "bg-status-danger-surface text-status-danger-content"
  return "bg-status-warning-surface text-status-warning-content"
}

function lineDiff(before: string, after: string) {
  const left = prettyLines(before)
  const right = prettyLines(after)
  const result: Array<{ kind: "same" | "added" | "removed"; text: string }> = []
  const length = Math.max(left.length, right.length)
  for (let index = 0; index < length; index += 1) {
    if (left[index] === right[index]) {
      result.push({ kind: "same", text: left[index] ?? "" })
    } else {
      if (left[index] !== undefined) result.push({ kind: "removed", text: left[index] })
      if (right[index] !== undefined) result.push({ kind: "added", text: right[index] })
    }
  }
  return result
}

function prettyLines(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2).split("\n")
  } catch {
    return value.split("\n")
  }
}

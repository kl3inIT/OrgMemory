import { Link, useParams } from "@tanstack/react-router"
import {
  ArrowLeft,
  Check,
  Copy,
  FileText,
  ShieldAlert,
  ShieldCheck,
  UserRoundCheck,
} from "lucide-react"
import { toast } from "sonner"
import { PageTitle } from "@/components/layout/page-title"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { formatAssetType } from "@/features/assets/asset-type"
import { StatusBadge } from "@/features/assets/status-badge"
import {
  useAsset,
  useAssetStatusAction,
  useAssetVersions,
  useAssignBackupOwner,
  useRecordUsage,
} from "@/features/assets/use-assets"
import { WorkflowDiagram } from "@/features/assets/workflow-diagram"
import {
  departmentName,
  useOrganizationLookups,
  userName,
} from "@/features/organization/use-organization-context"
import type { AssetVersion, CapabilityAsset } from "@/lib/api"

export function AssetDetailPage() {
  const { assetId } = useParams({ strict: false }) as { assetId: string }
  const asset = useAsset(assetId)
  const versions = useAssetVersions(assetId)
  const usage = useRecordUsage()
  const statusAction = useAssetStatusAction()
  const assignBackupOwner = useAssignBackupOwner()
  const { departmentById, users, userById } = useOrganizationLookups()

  if (asset.isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-12 w-72" />
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <Skeleton className="h-80" />
          <Skeleton className="h-80" />
        </div>
      </div>
    )
  }

  if (asset.isError || !asset.data) {
    return (
      <div className="space-y-6">
        <PageTitle title="Asset Detail" subtitle="The requested capability asset could not be loaded." />
        <Alert variant="destructive">
          <ShieldAlert />
          <AlertTitle>Asset unavailable</AlertTitle>
          <AlertDescription>Check that the asset still exists, then return to the registry.</AlertDescription>
        </Alert>
        <Button asChild variant="outline">
          <Link to="/registry"><ArrowLeft /> Back to registry</Link>
        </Button>
      </div>
    )
  }

  const currentAsset = asset.data
  const currentVersion = versions.data?.[0]
  const backupCandidate = users.find((user) => user.id !== currentAsset.ownerUserId)?.id
  const canReview = currentAsset.status !== "APPROVED" && currentAsset.status !== "DEPRECATED"

  function runStatusAction(action: "submit-review" | "approve" | "deprecate") {
    statusAction.mutate(
      { assetId, action },
      { onSuccess: () => toast.success(`Asset ${action.replace("-", " ")} completed.`) },
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <PageTitle title={currentAsset.title} subtitle={currentAsset.summary} />
        <Button asChild variant="outline">
          <Link to="/registry"><ArrowLeft /> Registry</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={currentAsset.status} />
                <Badge variant="outline">{formatAssetType(currentAsset.assetType)}</Badge>
                {currentAsset.riskLevel ? <Badge variant={currentAsset.riskLevel === "HIGH" ? "destructive" : "secondary"}>{currentAsset.riskLevel} risk</Badge> : null}
                <Badge variant="outline">{currentAsset.visibility.toLowerCase()}</Badge>
              </div>
              <CardDescription className="max-w-4xl text-base">{currentAsset.summary}</CardDescription>
            </div>
            <CardAction className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                disabled={usage.isPending}
                onClick={() => usage.mutate(currentAsset.id, { onSuccess: () => toast.success("Asset usage recorded.") })}
              >
                <Copy /> Use Asset
              </Button>
              {currentAsset.status === "DRAFT" ? (
                <Button disabled={statusAction.isPending} onClick={() => runStatusAction("submit-review")}>
                  <ShieldCheck /> Submit Review
                </Button>
              ) : null}
              {canReview ? (
                <Button disabled={statusAction.isPending} onClick={() => runStatusAction("approve")}>
                  <Check /> Approve
                </Button>
              ) : null}
            </CardAction>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetaCard label="Department" value={departmentName(departmentById, currentAsset.departmentId)} />
          <MetaCard label="Owner" value={userName(userById, currentAsset.ownerUserId)} />
          <MetaCard label="Backup Owner" value={currentAsset.backupOwnerUserId ? userName(userById, currentAsset.backupOwnerUserId) : "Missing"} destructive={!currentAsset.backupOwnerUserId} />
          <MetaCard label="Usage" value={`${currentAsset.usageCount} persisted uses`} />
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Workflow</CardTitle>
              <CardDescription>Current version visualized from persisted workflow steps.</CardDescription>
            </CardHeader>
            <CardContent>
              <WorkflowDiagram steps={currentVersion?.workflowStepsJson ?? currentAsset.summary} />
            </CardContent>
          </Card>

          <Card>
            <Tabs defaultValue="prompt">
              <CardHeader>
                <TabsList className="grid w-full grid-cols-4">
                  <TabsTrigger value="prompt">Prompt</TabsTrigger>
                  <TabsTrigger value="schemas">Inputs</TabsTrigger>
                  <TabsTrigger value="governance">Governance</TabsTrigger>
                  <TabsTrigger value="versions">Versions</TabsTrigger>
                </TabsList>
              </CardHeader>
              <CardContent>
                <TabsContent value="prompt" className="mt-0">
                  <CodeBlock value={currentVersion?.promptTemplate ?? "No prompt template captured."} />
                </TabsContent>
                <TabsContent value="schemas" className="mt-0 grid gap-4 lg:grid-cols-2">
                  <DetailPanel title="Input Schema" value={currentVersion?.inputSchemaJson} />
                  <DetailPanel title="Output Schema" value={currentVersion?.outputSchemaJson} />
                  <DetailPanel title="Example Input" value={currentVersion?.exampleInput} />
                  <DetailPanel title="Example Output" value={currentVersion?.exampleOutput} />
                </TabsContent>
                <TabsContent value="governance" className="mt-0">
                  <GovernanceTable asset={currentAsset} department={departmentName(departmentById, currentAsset.departmentId)} />
                </TabsContent>
                <TabsContent value="versions" className="mt-0">
                  <VersionTable versions={versions.data ?? []} userById={userById} />
                </TabsContent>
              </CardContent>
            </Tabs>
          </Card>
        </div>

        <aside className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Ownership</CardTitle>
              <CardDescription>Reuse requires an accountable owner and backup.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <OwnerLine label="Owner" value={userName(userById, currentAsset.ownerUserId)} />
              <OwnerLine label="Backup" value={currentAsset.backupOwnerUserId ? userName(userById, currentAsset.backupOwnerUserId) : "Missing"} destructive={!currentAsset.backupOwnerUserId} />
              <Separator />
              <Button
                className="w-full"
                variant={currentAsset.backupOwnerUserId ? "outline" : "default"}
                disabled={!backupCandidate || assignBackupOwner.isPending}
                onClick={() =>
                  assignBackupOwner.mutate(
                    { assetId: currentAsset.id, backupOwnerUserId: backupCandidate ?? "" },
                    { onSuccess: () => toast.success("Backup owner assigned.") },
                  )
                }
              >
                <UserRoundCheck /> {currentAsset.backupOwnerUserId ? "Reassign Backup" : "Assign Backup"}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Operational Fit</CardTitle>
              <CardDescription>What this asset is for.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <OwnerLine label="Use case" value={currentAsset.useCase ?? "Not captured"} />
              <OwnerLine label="Process" value={currentAsset.businessProcess ?? "Not captured"} />
              <OwnerLine label="Tool" value={currentAsset.aiTool ?? "Tool agnostic"} />
              <OwnerLine label="Tags" value={currentAsset.tagNames ?? "No tags"} />
            </CardContent>
          </Card>

          <Alert>
            <FileText />
            <AlertTitle>Presentation path</AlertTitle>
            <AlertDescription>Registry opens this detail, detail shows workflow, and Use Asset records a backend usage event.</AlertDescription>
          </Alert>
        </aside>
      </div>
    </div>
  )
}

function MetaCard({ label, value, destructive }: { label: string; value: string; destructive?: boolean }) {
  return (
    <div className="rounded-lg border p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className={destructive ? "mt-1 font-semibold text-destructive" : "mt-1 font-semibold"}>{value}</p>
    </div>
  )
}

function DetailPanel({ title, value }: { title: string; value?: string | null }) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">{title}</h3>
      <CodeBlock value={value ?? "Not captured."} compact />
    </div>
  )
}

function CodeBlock({ value, compact }: { value: string; compact?: boolean }) {
  return (
    <ScrollArea className={compact ? "h-36 rounded-md border bg-muted/30" : "h-72 rounded-md border bg-muted/30"}>
      <pre className="whitespace-pre-wrap p-4 text-xs leading-6">{value}</pre>
    </ScrollArea>
  )
}

function GovernanceTable({ asset, department }: { asset: CapabilityAsset; department: string }) {
  const rows = [
    ["Department", department],
    ["Business process", asset.businessProcess ?? "Not captured"],
    ["AI tool", asset.aiTool ?? "Tool agnostic"],
    ["Risk level", asset.riskLevel ?? "Not assessed"],
    ["Visibility", asset.visibility],
    ["Tags", asset.tagNames ?? "No tags"],
  ]

  return (
    <Table>
      <TableBody>
        {rows.map(([label, value]) => (
          <TableRow key={label}>
            <TableCell className="w-48 text-muted-foreground">{label}</TableCell>
            <TableCell className="font-medium">{value}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function VersionTable({ versions, userById }: { versions: AssetVersion[]; userById: Map<string, { name: string }> }) {
  if (!versions.length) {
    return <p className="text-sm text-muted-foreground">No persisted versions loaded.</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Version</TableHead>
          <TableHead>Change Note</TableHead>
          <TableHead>Created By</TableHead>
          <TableHead className="text-right">Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {versions.map((version) => (
          <TableRow key={version.id}>
            <TableCell>v{version.versionNumber}</TableCell>
            <TableCell>{version.changeNote ?? "No note"}</TableCell>
            <TableCell>{version.createdByUserId ? userById.get(version.createdByUserId)?.name ?? "Unassigned" : "Unassigned"}</TableCell>
            <TableCell className="text-right">{new Date(version.createdAt).toLocaleDateString()}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function OwnerLine({ label, value, destructive }: { label: string; value: string; destructive?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className={destructive ? "text-right font-medium text-destructive" : "text-right font-medium"}>{value}</span>
    </div>
  )
}

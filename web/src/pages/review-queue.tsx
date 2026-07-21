import { AlertTriangle, Bot, Check, Clock } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { toast } from "sonner"
import { PageTitle } from "@/components/layout/page-title"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils"
import { StatusBadge } from "@/features/assets/status-badge"
import { useAssetStatusAction, useAssetVersions, useAssets } from "@/features/assets/use-assets"
import { WorkflowDiagram } from "@/features/assets/workflow-diagram"
import { departmentName, useOrganizationLookups, userName } from "@/features/organization/use-organization-context"

export function ReviewQueuePage() {
  const { data, isError, isLoading } = useAssets()
  const { departmentById, userById } = useOrganizationLookups()
  const assets = data ?? []
  const queueRows = useMemo(() => assets.filter((asset) => asset.status === "IN_REVIEW"), [assets])
  const draftRows = useMemo(() => assets.filter((asset) => asset.status === "DRAFT"), [assets])
  const approvedRows = useMemo(() => assets.filter((asset) => asset.status === "APPROVED"), [assets])
  const [activeTab, setActiveTab] = useState<"queue" | "drafts" | "approved">("queue")
  const rows = activeTab === "queue" ? queueRows : activeTab === "drafts" ? draftRows : approvedRows
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const active = rows.find((asset) => asset.id === selectedId) ?? rows[0]
  const activeIsPersisted = Boolean(active?.id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(active.id))
  const versions = useAssetVersions(activeIsPersisted ? active?.id : null)
  const statusAction = useAssetStatusAction()
  const canReview = Boolean(active && activeIsPersisted && active.status !== "APPROVED" && active.status !== "DEPRECATED")

  useEffect(() => {
    if (!selectedId && rows[0]?.id) {
      setSelectedId(rows[0].id)
    }
    if (selectedId && rows.length && !rows.some((asset) => asset.id === selectedId)) {
      setSelectedId(rows[0].id)
    }
  }, [rows, selectedId])

  function runStatusAction(action: "approve" | "reject" | "deprecate") {
    if (!active) return
    statusAction.mutate(
      { assetId: active.id, action },
      { onSuccess: () => toast.success(`Asset ${action.replace("-", " ")} completed.`) },
    )
  }

  return (
    <div className="space-y-6">
      <PageTitle title="Review Queue" subtitle="Approve, improve, and publish AI capability assets." />

      <div className="grid gap-4 xl:grid-cols-[24rem_minmax(0,1fr)]">
        <Card>
          <Tabs
            value={activeTab}
            onValueChange={(value: string) => {
              setActiveTab(value as "queue" | "drafts" | "approved")
              setSelectedId(null)
            }}
          >
            <CardHeader>
              <TabsList className="w-full">
                <TabsTrigger value="queue" className="flex-1">Queue ({queueRows.length})</TabsTrigger>
                <TabsTrigger value="drafts" className="flex-1">Drafts ({draftRows.length})</TabsTrigger>
                <TabsTrigger value="approved" className="flex-1">Approved ({approvedRows.length})</TabsTrigger>
              </TabsList>
            </CardHeader>
            <CardContent>
              <TabsContent value={activeTab} className="mt-0 space-y-4">
                <Select defaultValue="newest">
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectItem value="newest">Sort by: Newest</SelectItem></SelectContent>
                </Select>
                <div className="space-y-3">
                  {isLoading ? <p className="text-sm text-muted-foreground">Loading real review queue...</p> : null}
                  {isError ? <p className="text-sm text-destructive">Could not load review queue from backend.</p> : null}
                  {!isLoading && !isError && rows.length === 0 ? <p className="text-sm text-muted-foreground">No assets in this tab.</p> : null}
                  {rows.map((asset) => (
                    <button
                      className={cn(
                        "flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors hover:bg-muted/50",
                        active?.id === asset.id && "border-primary bg-primary/5",
                      )}
                      key={asset.id}
                      type="button"
                      onClick={() => setSelectedId(asset.id)}
                    >
                      <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
                        <Bot className="size-4" />
                      </div>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">{asset.title}</span>
                        <span className="block text-xs text-muted-foreground">
                          {departmentName(departmentById, asset.departmentId)} · {userName(userById, asset.ownerUserId)}
                        </span>
                      </span>
                      <StatusBadge status={asset.status} />
                    </button>
                  ))}
                </div>
              </TabsContent>
            </CardContent>
          </Tabs>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle>{active?.title ?? "Select an asset"}</CardTitle>
                <p className="mt-1 text-sm text-muted-foreground">
                  {active ? `${departmentName(departmentById, active.departmentId)} · ${userName(userById, active.ownerUserId)}` : "No asset selected"}
                </p>
              </div>
              {active ? <StatusBadge status={active.status} /> : null}
            </CardHeader>
            <CardContent className="grid gap-4 lg:grid-cols-3">
              <ReviewBox title="Overview">{active?.summary ?? "No summary available."}</ReviewBox>
              <div className="lg:col-span-2">
                <ReviewBox title="Prompt / Workflow">
                  <WorkflowDiagram steps={versions.data?.[0]?.workflowStepsJson ?? active?.summary} />
                </ReviewBox>
              </div>
              <Checklist title="Required Inputs" items={["Customer feedback text", "Source channel", "Time range", "Product / feature"]} />
              <Checklist title="Expected Output" items={["Sentiment score", "Top themes", "Key quotes", "Recommended actions"]} />
              <ReviewBox title="Version History">
                <div className="space-y-2">
                  {(versions.data ?? []).slice(0, 3).map((version) => (
                    <div className="flex items-center gap-2 text-sm" key={version.id}>
                      <Badge variant="outline">v{version.versionNumber}</Badge>
                      <span className="text-muted-foreground">{version.promptTemplate ? "Prompt captured" : "Initial version"}</span>
                    </div>
                  ))}
                  {!versions.data?.length ? <p className="text-sm text-muted-foreground">No persisted version loaded yet.</p> : null}
                </div>
              </ReviewBox>
              <ReviewBox title="Usage Snapshot">
                <div className="text-3xl font-semibold tracking-tight">{active?.usageCount ?? 0}</div>
                <p className="text-sm text-muted-foreground">Total persisted usage events</p>
              </ReviewBox>
              <ReviewBox title="Approval Checklist">
                <div className="space-y-3">
                  <RiskItem icon={<AlertTriangle />} title="Missing backup owner" text="Please assign a backup owner." />
                  <RiskItem icon={<Clock />} title="Quality score: 78/100" text="Consider improving prompt clarity." />
                </div>
              </ReviewBox>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Approval Actions</CardTitle></CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-3">
              <Button disabled={!canReview || statusAction.isPending} onClick={() => runStatusAction("approve")}><Check /> Approve</Button>
              <Button disabled={!canReview || statusAction.isPending} variant="outline" onClick={() => runStatusAction("reject")}>Request Changes</Button>
              <Button variant="outline" className="border-destructive text-destructive hover:bg-destructive/10" disabled={!active || !activeIsPersisted || active.status === "DEPRECATED" || statusAction.isPending} onClick={() => runStatusAction("deprecate")}>
                Deprecate
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

function ReviewBox({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border bg-muted/20 p-4">
      <h3 className="mb-3 text-sm font-semibold">{title}</h3>
      <div className="text-sm leading-6 text-muted-foreground">{children}</div>
    </div>
  )
}

function Checklist({ title, items }: { title: string; items: string[] }) {
  return (
    <ReviewBox title={title}>
      <div className="space-y-2">
        {items.map((item) => (
          <div className="flex items-center gap-2" key={item}>
            <Check className="size-4 text-emerald-600 dark:text-emerald-400" />
            <span>{item}</span>
          </div>
        ))}
      </div>
    </ReviewBox>
  )
}

function RiskItem({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <Alert>
      {icon}
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{text}</AlertDescription>
    </Alert>
  )
}

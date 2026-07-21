import { Link } from "@tanstack/react-router"
import { Copy, FileText, MoreHorizontal, Plus, Search } from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"
import { PageTitle } from "@/components/layout/page-title"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { buildMetrics } from "@/features/assets/demo-data"
import { assetTypes, formatAssetType } from "@/features/assets/asset-type"
import { StatusBadge } from "@/features/assets/status-badge"
import { useAssets, useRecordUsage } from "@/features/assets/use-assets"
import { departmentName, useOrganizationLookups, userName } from "@/features/organization/use-organization-context"
import type { AssetStatus, AssetType } from "@/lib/api"

export function RegistryPage() {
  const [query, setQuery] = useState(() => sessionStorage.getItem("orgmemory:registry-query") ?? "")
  const [status, setStatus] = useState<AssetStatus | "">("")
  const [department, setDepartment] = useState("ALL")
  const [tool, setTool] = useState("All Tools")
  const [assetType, setAssetType] = useState<AssetType | "">("")
  const { data, isError, isLoading } = useAssets(status, query, assetType)
  const { departments, departmentById, userById } = useOrganizationLookups()
  const usage = useRecordUsage()
  const rows = (data ?? []).filter((asset) => {
    const matchesDepartment = department === "ALL" || asset.departmentId === department
    const matchesTool = tool === "All Tools" || (asset.aiTool ?? "").toLowerCase().includes(tool.toLowerCase())
    return matchesDepartment && matchesTool
  })
  const metrics = useMemo(() => buildMetrics(rows), [rows])
  const toolOptions = useMemo(() => {
    return Array.from(new Set((data ?? []).map((asset) => asset.aiTool).filter(Boolean) as string[])).sort()
  }, [data])
  const mostReusedAsset = [...rows].sort((a, b) => b.usageCount - a.usageCount)[0]

  return (
    <div className="space-y-6">
      <PageTitle title="Capability Registry" subtitle="Search, filter, and manage reusable AI capability assets across the organization." />

      <div className="grid gap-4 2xl:grid-cols-[minmax(0,1fr)_18rem]">
        <Card>
          <CardHeader className="border-b">
            <CardTitle>Registry</CardTitle>
            <CardDescription>Approved, draft, and review-ready AI capability assets.</CardDescription>
            <CardAction>
              <Button asChild>
                <Link to="/create"><Plus /> New Asset</Link>
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
              <div className="grid flex-1 gap-2">
                <Label>Search</Label>
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    className="pl-9"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search assets by name, keyword, or description..."
                  />
                </div>
              </div>
              <div className="grid gap-2 lg:w-44">
                <Label>Department</Label>
                <Select value={department} onValueChange={setDepartment}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">All Departments</SelectItem>
                    {departments.map((department) => (
                      <SelectItem key={department.id} value={department.id}>{department.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2 lg:w-52">
                <Label>Type</Label>
                <Select value={assetType || "ALL"} onValueChange={(value: string) => setAssetType(value === "ALL" ? "" : (value as AssetType))}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">All Types</SelectItem>
                    {assetTypes.map((type) => <SelectItem key={type} value={type}>{formatAssetType(type)}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2 lg:w-44">
                <Label>Status</Label>
                <Select value={status || "ALL"} onValueChange={(value: string) => setStatus(value === "ALL" ? "" : (value as AssetStatus))}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">All Statuses</SelectItem>
                    <SelectItem value="DRAFT">Draft</SelectItem>
                    <SelectItem value="IN_REVIEW">Needs Review</SelectItem>
                    <SelectItem value="APPROVED">Approved</SelectItem>
                    <SelectItem value="DEPRECATED">Deprecated</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <FilterSelect label="Tool" value={tool} onValueChange={setTool} values={["All Tools", ...toolOptions]} />
            </div>

            <div className="overflow-x-auto">
              <Table className="min-w-[760px]">
                <TableHeader>
                  <TableRow>
                    <TableHead>Asset Name</TableHead>
                    <TableHead>Department</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Owner</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Usage</TableHead>
                    <TableHead className="w-20 text-right">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {isLoading ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-muted-foreground">Loading real capability assets from API...</TableCell>
                    </TableRow>
                  ) : null}
                  {isError ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-destructive">Could not load assets from the backend.</TableCell>
                    </TableRow>
                  ) : null}
                  {!isLoading && !isError && rows.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-muted-foreground">No assets match the current filters.</TableCell>
                    </TableRow>
                  ) : null}
                  {rows.slice(0, 10).map((asset) => {
                    const count = asset.usageCount
                    return (
                      <TableRow key={asset.id}>
                        <TableCell>
                          <div className="flex min-w-72 items-center gap-3">
                            <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
                              <FileText className="size-4" />
                            </div>
                            <div className="min-w-0">
                              <div className="truncate font-medium">{asset.title}</div>
                              <div className="truncate text-xs text-muted-foreground">{asset.useCase ?? "Capability workflow"}</div>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>{departmentName(departmentById, asset.departmentId)}</TableCell>
                        <TableCell><Badge variant="outline">{formatAssetType(asset.assetType)}</Badge></TableCell>
                        <TableCell>{userName(userById, asset.ownerUserId)}</TableCell>
                        <TableCell><StatusBadge status={asset.status} /></TableCell>
                        <TableCell>
                          <div className="flex w-28 items-center gap-2">
                            <span className="w-8 tabular-nums">{count}</span>
                            <Progress value={Math.min(100, count)} />
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              disabled={usage.isPending}
                              onClick={() => usage.mutate(asset.id, { onSuccess: () => toast.success("Asset usage recorded.") })}
                              aria-label="Use asset"
                            >
                              <Copy className="size-4" />
                            </Button>
                            <Button asChild variant="ghost" size="icon-sm" aria-label={`Open ${asset.title}`}>
                              <Link to="/assets/$assetId" params={{ assetId: asset.id }}>
                                <MoreHorizontal className="size-4" />
                              </Link>
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>

            <div className="text-sm text-muted-foreground">
              Showing {rows.length ? 1 : 0} to {Math.min(rows.length, 10)} of {rows.length} assets
            </div>
          </CardContent>
        </Card>

        <aside className="grid content-start gap-4 md:grid-cols-3 2xl:grid-cols-1">
          <InsightCard title="Most Reused Asset" value={`${mostReusedAsset?.usageCount ?? 0} uses`} text={mostReusedAsset?.title ?? "No assets loaded"} />
          <InsightCard title="Assets Pending Approval" value={String(metrics.inReview)} text="Review required across departments" tone="destructive" />
          <InsightCard title="Assets Without Backup Owner" value={String(metrics.missingBackup)} text="Assign backup owners" tone="warning" />
          <Button asChild variant="link" className="justify-start px-0"><Link to="/analytics">View all insights</Link></Button>
        </aside>
      </div>
    </div>
  )
}

function FilterSelect({
  label,
  value,
  values,
  onValueChange,
}: {
  label: string
  value: string
  values: string[]
  onValueChange: (value: string) => void
}) {
  return (
    <div className="grid gap-2 lg:w-44">
      <Label>{label}</Label>
      <Select value={value} onValueChange={onValueChange}>
        <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
        <SelectContent>
          {values.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  )
}

function InsightCard({
  title,
  value,
  text,
  tone = "default",
}: {
  title: string
  value: string
  text: string
  tone?: "default" | "warning" | "destructive"
}) {
  const variant = tone === "destructive" ? "destructive" : tone === "warning" ? "warning" : "secondary"

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">{title}</CardTitle>
        <CardAction>
          <Badge variant={variant}>{value}</Badge>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
          <FileText className="size-5" />
        </div>
        <div>
          <div className="text-sm font-medium">{text}</div>
          <p className="text-xs text-muted-foreground">Registry insight</p>
        </div>
        <div className={tone === "destructive" ? "text-xs font-medium text-destructive" : "text-xs font-medium text-emerald-600 dark:text-emerald-400"}>
          ↑ {tone === "default" ? "16%" : "3"} vs previous 30 days
        </div>
      </CardContent>
    </Card>
  )
}

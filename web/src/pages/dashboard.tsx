import { Link } from "@tanstack/react-router"
import { AlertTriangle, ArrowUpRight, Layers, ShieldCheck, UserMinus, UsersRound } from "lucide-react"
import { toast } from "sonner"
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, XAxis, YAxis } from "recharts"
import { PageTitle } from "@/components/layout/page-title"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { buildMetrics } from "@/features/assets/demo-data"
import { StatusBadge } from "@/features/assets/status-badge"
import { useAssets } from "@/features/assets/use-assets"
import { departmentName, useOrganizationLookups, userName } from "@/features/organization/use-organization-context"

export function DashboardPage() {
  const { data } = useAssets()
  const { departments, departmentById, userById } = useOrganizationLookups()
  const assets = data ?? []
  const metrics = buildMetrics(assets)
  const activeContributors = new Set(assets.flatMap((asset) => [asset.ownerUserId, asset.createdByUserId].filter(Boolean))).size
  const topAssets = [...assets].sort((a, b) => b.usageCount - a.usageCount).slice(0, 5)
  const maturity = departments.map((department) => {
    const departmentAssets = assets.filter((asset) => asset.departmentId === department.id)
    const approvedAssets = departmentAssets.filter((asset) => asset.status === "APPROVED").length
    return {
      department: department.name,
      score: departmentAssets.length ? Math.round((approvedAssets / departmentAssets.length) * 100) : 0,
    }
  })
  const usageDistribution = topAssets.map((asset) => ({
    asset: asset.title.length > 24 ? `${asset.title.slice(0, 24)}...` : asset.title,
    uses: asset.usageCount,
  }))
  const health = [
    { name: "approved", value: metrics.approved },
    { name: "draft", value: assets.filter((asset) => asset.status === "DRAFT").length },
    { name: "review", value: metrics.inReview },
  ]

  return (
    <div className="space-y-6">
      <PageTitle title="Capability Dashboard" subtitle="Your organization's AI capabilities at a glance." />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard icon={<Layers />} label="Total Assets" value={metrics.total} delta="live" />
        <StatCard icon={<ShieldCheck />} label="Approved Assets" value={metrics.approved} delta="live" tone="success" />
        <StatCard icon={<UsersRound />} label="Active Contributors" value={activeContributors} delta="live" tone="violet" />
        <StatCard icon={<UserMinus />} label="Missing Backup Owner" value={metrics.missingBackup} delta="live" tone="warning" negative />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_minmax(0,1fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Department Capability Maturity</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <ChartContainer config={{ score: { label: "Maturity", color: "var(--chart-1)" } }} className="h-[220px]">
              <BarChart data={maturity} layout="vertical" margin={{ left: 20, right: 18 }}>
                <CartesianGrid horizontal={false} />
                <XAxis type="number" hide domain={[0, 100]} />
                <YAxis dataKey="department" type="category" width={118} tickLine={false} axisLine={false} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Bar dataKey="score" fill="var(--color-score)" radius={4} />
              </BarChart>
            </ChartContainer>
            <Button asChild variant="link" className="px-0">
              <Link to="/analytics">View full maturity report</Link>
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Recently Created Assets</CardTitle>
            <Button asChild variant="link" size="sm">
              <Link to="/registry">View all</Link>
            </Button>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Asset</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead>Process</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Last Used</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {assets.slice(0, 5).map((asset, index) => (
                  <TableRow key={asset.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
                          <Layers className="size-4" />
                        </div>
                        <span className="font-medium">{asset.title}</span>
                      </div>
                    </TableCell>
                      <TableCell>{userName(userById, asset.ownerUserId)}</TableCell>
                    <TableCell>{departmentName(departmentById, asset.departmentId)}</TableCell>
                    <TableCell><StatusBadge status={asset.status} /></TableCell>
                    <TableCell className="text-right">{index + 2}h ago</TableCell>
                  </TableRow>
                ))}
                {assets.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-muted-foreground">Loading real capability assets...</TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Risk Alerts</CardTitle>
            <Button asChild variant="link" size="sm">
              <Link to="/review">View all</Link>
            </Button>
          </CardHeader>
          <CardContent className="space-y-3">
            <RiskAlert title={`${metrics.missingBackup} assets have no backup owner`} text="Assign backup owners to reduce risk" />
            <RiskAlert title={`${assets.filter((asset) => asset.status === "DRAFT" && asset.usageCount > 0).length} used assets are still in draft`} text="Review and publish to unlock value" />
            <RiskAlert title={`${assets.filter((asset) => asset.assetType === "HANDOVER_PACK").length} handover packs tracked`} text="Ensure knowledge is captured" />
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Top Reused Assets</CardTitle>
            <Button asChild variant="link" size="sm">
              <Link to="/registry">View all</Link>
            </Button>
          </CardHeader>
          <CardContent className="space-y-3">
            {topAssets.map((asset, index) => (
              <div className="flex items-center gap-3" key={asset.id}>
                <div className="grid size-6 place-items-center rounded-full bg-muted text-xs font-medium text-muted-foreground">
                  {index + 1}
                </div>
                <Layers className="size-4 text-primary" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{asset.title}</div>
                </div>
                <div className="text-sm text-muted-foreground">{asset.usageCount} uses</div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="xl:col-span-1">
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle>Usage Distribution</CardTitle>
              <CardDescription>Reuse volume from backend usage events.</CardDescription>
            </div>
            <Button variant="outline" size="sm" onClick={() => toast.info("Showing live usage counts aggregated per asset.")}>Live usage</Button>
          </CardHeader>
          <CardContent className="space-y-4">
            <ChartContainer config={{ uses: { label: "Uses", color: "var(--chart-1)" } }} className="h-[220px]">
              <BarChart data={usageDistribution}>
                <CartesianGrid vertical={false} />
                <XAxis dataKey="asset" tickLine={false} axisLine={false} hide />
                <YAxis tickLine={false} axisLine={false} width={36} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Bar dataKey="uses" fill="var(--color-uses)" radius={4} />
              </BarChart>
            </ChartContainer>
            <div className="grid gap-4 border-t pt-4 sm:grid-cols-2">
              <SummaryMetric label="Total asset uses" value={metrics.usage.toLocaleString()} delta="live" />
              <SummaryMetric label="Unique assets used" value={assets.filter((asset) => asset.usageCount > 0).length.toLocaleString()} delta="live" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Asset Health Overview</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <ChartContainer
              config={{
                approved: { label: "Approved", color: "var(--chart-3)" },
                draft: { label: "Draft", color: "var(--chart-2)" },
                review: { label: "Needs Review", color: "var(--chart-5)" },
              }}
              className="mx-auto h-[230px] max-w-[260px]"
            >
              <PieChart>
                <ChartTooltip content={<ChartTooltipContent />} />
                <Pie data={health} innerRadius={64} outerRadius={92} dataKey="value">
                  <Cell fill="var(--color-approved)" />
                  <Cell fill="var(--color-draft)" />
                  <Cell fill="var(--color-review)" />
                </Pie>
              </PieChart>
            </ChartContainer>
            <Button asChild variant="link" className="px-0">
              <Link to="/analytics">View full analytics</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function StatCard({
  icon,
  label,
  value,
  delta,
  tone = "primary",
  negative,
}: {
  icon: React.ReactNode
  label: string
  value: number
  delta: string
  tone?: "primary" | "success" | "violet" | "warning"
  negative?: boolean
}) {
  const toneClass = {
    primary: "bg-primary/10 text-primary",
    success: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
    violet: "bg-violet-500/10 text-violet-600 dark:text-violet-400",
    warning: "bg-amber-500/10 text-amber-600 dark:text-amber-400",
  }[tone]

  return (
    <Card>
      <CardContent className="flex items-center gap-4 pt-6">
        <div className={`grid size-12 place-items-center rounded-lg ${toneClass}`}>{icon}</div>
        <div>
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <div className="mt-1 text-3xl font-semibold tracking-tight">{value.toLocaleString()}</div>
          <div className={negative ? "mt-1 text-xs font-medium text-destructive" : "mt-1 text-xs font-medium text-emerald-600 dark:text-emerald-400"}>
            {delta === "live" ? "Live backend value" : `↑ ${delta} vs last 30 days`}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function RiskAlert({ title, text }: { title: string; text: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border p-3">
      <div className="grid size-9 place-items-center rounded-md bg-amber-500/10 text-amber-600 dark:text-amber-400">
        <AlertTriangle className="size-4" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium">{title}</div>
        <p className="text-xs text-muted-foreground">{text}</p>
      </div>
      <ArrowUpRight className="size-4 text-muted-foreground" />
    </div>
  )
}

function SummaryMetric({ label, value, delta }: { label: string; value: string; delta: string }) {
  return (
    <div>
      <p className="text-sm text-muted-foreground">{label}</p>
      <div className="mt-1 text-2xl font-semibold tracking-tight">{value}</div>
      <div className="text-xs font-medium text-emerald-600 dark:text-emerald-400">
        {delta === "live" ? "Live backend value" : `↑ ${delta} vs previous 30 days`}
      </div>
    </div>
  )
}

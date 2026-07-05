import { AlertTriangle, Check, FileText, UserMinus } from "lucide-react"
import { toast } from "sonner"
import { PageTitle } from "@/components/layout/page-title"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useAssignBackupOwner, useAssets } from "@/features/assets/use-assets"
import {
  departmentName,
  useOrganizationLookups,
  userInitials,
  userName,
} from "@/features/organization/use-organization-context"
import type { CapabilityAsset, OrgUser } from "@/lib/api"

export function KnowledgeTransferPage() {
  const { data, isError, isLoading } = useAssets()
  const { departments, departmentById, users, userById } = useOrganizationLookups()
  const assets = data ?? []
  const approved = assets.filter((asset) => asset.status === "APPROVED")
  const newHire = users.find((user) => user.role === "EMPLOYEE") ?? users[0]
  const manager = users.find((user) => user.role === "TEAM_LEAD") ?? users[1]
  const onboardingCompleted = Math.min(approved.length, 8)
  const onboardingProgress = Math.round((onboardingCompleted / 8) * 100)
  const missingBackupCount = assets.filter((asset) => !asset.backupOwnerUserId).length

  return (
    <div className="space-y-6">
      <PageTitle title="Knowledge Transfer" subtitle="Help employees inherit and hand over organizational AI capabilities." />
      <Tabs defaultValue="onboarding">
        <TabsList>
          <TabsTrigger value="onboarding">Onboarding</TabsTrigger>
          <TabsTrigger value="offboarding">Offboarding</TabsTrigger>
        </TabsList>

        <TabsContent value="onboarding" className="mt-4">
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(18rem,0.8fr)]">
            <Card>
              <CardContent className="flex flex-col gap-4 pt-6 md:flex-row md:items-center">
                <Avatar className="size-24">
                  <AvatarFallback className="text-2xl">{userInitials(newHire)}</AvatarFallback>
                </Avatar>
                <div className="space-y-3">
                  <div>
                    <h2 className="text-2xl font-semibold tracking-tight">{newHire?.name ?? "New team member"} <Badge variant="secondary">New Hire</Badge></h2>
                    <p className="text-muted-foreground">{newHire?.role?.replace("_", " ") ?? "Employee"}</p>
                  </div>
                  <div className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-3">
                    <span>Manager: {manager?.name ?? "Unassigned"}</span>
                    <span>Department: {departmentName(departmentById, newHire?.departmentId)}</span>
                    <span>Departments tracked: {departments.length}</span>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>Onboarding Progress</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <div className="text-4xl font-semibold tracking-tight">{onboardingProgress}%</div>
                <Progress value={onboardingProgress} />
                <div className="grid gap-2 text-sm">
                  <div className="flex justify-between"><span className="text-muted-foreground">Completed</span><strong>{onboardingCompleted} / 8</strong></div>
                  <div className="flex justify-between"><span className="text-muted-foreground">In Progress</span><strong>{Math.max(0, assets.length - onboardingCompleted)}</strong></div>
                  <div className="flex justify-between"><span className="text-muted-foreground">Missing backup</span><strong>{missingBackupCount}</strong></div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Suggested Starter Assets</CardTitle>
                <CardDescription>Recommended assets to help ramp up quickly.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {isLoading ? <p className="text-sm text-muted-foreground">Loading approved assets...</p> : null}
                {isError ? <p className="text-sm text-destructive">Could not load approved assets.</p> : null}
                {!isLoading && !isError && approved.length === 0 ? <p className="text-sm text-muted-foreground">No approved assets yet.</p> : null}
                {approved.slice(0, 6).map((asset, index) => (
                  <StarterAsset key={asset.id} asset={asset} label={index < 2 ? "Essential" : "Recommended"} owner={userName(userById, asset.ownerUserId)} />
                ))}
              </CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>First 2 Weeks</CardTitle></CardHeader>
              <CardContent><Timeline assets={approved} /></CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>Offboarding Preview</CardTitle></CardHeader>
              <CardContent><RiskCallout missingBackupCount={missingBackupCount} /></CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="offboarding" className="mt-4">
          <OffboardingPanel assets={assets} users={users} userById={userById} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function StarterAsset({ asset, label, owner }: { asset: CapabilityAsset; label: string; owner: string }) {
  return (
    <div className="flex items-center gap-3 border-b pb-3 last:border-b-0 last:pb-0">
      <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
        <FileText className="size-4" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium">{asset.title}</div>
        <p className="truncate text-xs text-muted-foreground">{owner} · {asset.summary}</p>
      </div>
      <Badge variant={label === "Essential" ? "success" : "secondary"}>{label}</Badge>
    </div>
  )
}

function Timeline({ assets }: { assets: CapabilityAsset[] }) {
  const items = assets.slice(0, 5).map((asset) => `Review ${asset.title}`)
  return (
    <div className="space-y-4">
      {(items.length ? items : ["Review approved starter assets"]).map((item, index) => (
        <div className="flex gap-3" key={item}>
          <div className="grid size-6 place-items-center rounded-full bg-primary text-xs font-medium text-primary-foreground">
            {index < 2 ? <Check className="size-4" /> : index + 1}
          </div>
          <div>
            <div className="text-sm font-medium">{item}</div>
            <p className="text-xs text-muted-foreground">{index < 2 ? "Understand how it works and best practices." : "Continue the ramp plan."}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

function RiskCallout({ missingBackupCount }: { missingBackupCount: number }) {
  return (
    <Alert>
      <UserMinus />
      <AlertTitle>{missingBackupCount} assets require backup owner review</AlertTitle>
      <AlertDescription>Live count from persisted capability assets.</AlertDescription>
    </Alert>
  )
}

function OffboardingPanel({
  assets,
  users,
  userById,
}: {
  assets: CapabilityAsset[]
  users: OrgUser[]
  userById: Map<string, OrgUser>
}) {
  const assignBackupOwner = useAssignBackupOwner()
  const rows = assets
  const missingBackupCount = assets.filter((asset) => !asset.backupOwnerUserId).length
  return (
    <div className="space-y-4">
      <Alert>
        <AlertTriangle />
        <AlertTitle>{missingBackupCount} assets are missing backup owner</AlertTitle>
        <AlertDescription>These persisted assets could become inaccessible during handover.</AlertDescription>
      </Alert>

      <Card>
        <CardHeader><CardTitle>Assets Requiring Handover</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Asset Name</TableHead>
                <TableHead>Usage</TableHead>
                <TableHead>Backup Owner</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} className="text-muted-foreground">No real assets loaded yet.</TableCell>
                </TableRow>
              ) : null}
              {rows.slice(0, 8).map((asset) => {
                const backupCandidate = users.find((user) => user.id !== asset.ownerUserId)?.id
                return (
                <TableRow key={asset.id}>
                  <TableCell>
                    <div className="flex min-w-72 items-center gap-3">
                      <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
                        <FileText className="size-4" />
                      </div>
                      <span className="font-medium">{asset.title}</span>
                    </div>
                  </TableCell>
                  <TableCell>{asset.usageCount >= 3 ? "High" : asset.usageCount > 0 ? "Medium" : "Low"}</TableCell>
                  <TableCell>{asset.backupOwnerUserId ? userName(userById, asset.backupOwnerUserId) : <span className="text-destructive">No backup owner</span>}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={assignBackupOwner.isPending || !backupCandidate}
                      onClick={() =>
                        assignBackupOwner.mutate(
                          { assetId: asset.id, backupOwnerUserId: backupCandidate ?? "" },
                          { onSuccess: () => toast.success("Backup owner assigned.") },
                        )
                      }
                    >
                      {asset.backupOwnerUserId ? "Reassign" : "Assign Owner"}
                    </Button>
                  </TableCell>
                </TableRow>
              )})}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}

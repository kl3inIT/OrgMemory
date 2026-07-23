import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { AdminPage } from "@/features/admin/components/admin-page"

/**
 * SCIM is not built yet, so this page states the model that is actually in force rather
 * than showing a control that does nothing.
 */
export function AdminScimPage() {
  return (
    <AdminPage
      title="SCIM"
      description="Automatic provisioning from the identity provider is not enabled for this deployment."
    >
      <Card className="max-w-3xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Provisioning status
            <Badge variant="outline">Not configured</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 text-sm">
          <dl className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <dt className="text-muted-foreground">Account authority</dt>
              <dd className="font-medium">Identity provider</dd>
            </div>
            <Separator />
            <div className="flex flex-wrap items-center justify-between gap-2">
              <dt className="text-muted-foreground">Automatic user and group sync</dt>
              <dd className="font-medium">Not enabled</dd>
            </div>
            <Separator />
            <div className="flex flex-wrap items-center justify-between gap-2">
              <dt className="text-muted-foreground">In-app registration</dt>
              <dd className="font-medium">Disabled</dd>
            </div>
          </dl>
          <p className="rounded-lg bg-surface-subtle p-3 text-muted-foreground">
            Until SCIM is available, resolve external people through{" "}
            <span className="font-medium text-foreground">Source mappings</span>. Unresolved identities
            remain denied.
          </p>
        </CardContent>
      </Card>
    </AdminPage>
  )
}

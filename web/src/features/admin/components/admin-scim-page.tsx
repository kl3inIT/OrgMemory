import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Provisioning
            <Badge variant="outline">Not configured</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 text-sm text-muted-foreground">
          <p>
            Users are created in the identity provider and become usable in OrgMemory once their account is
            linked to an organization member. There is no in-application invitation or registration, so the
            directory can never disagree with the identity provider.
          </p>
          <p>
            Until SCIM is available, an administrator confirms an external identity on the{" "}
            <span className="font-medium text-foreground">Source mappings</span> screen, or marks a whole
            connection SSO verified so its identities resolve on their own.
          </p>
        </CardContent>
      </Card>
    </AdminPage>
  )
}

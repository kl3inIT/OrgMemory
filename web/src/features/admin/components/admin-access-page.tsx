import { AccessInspector } from "@/features/admin/components/access-inspector"
import { AdminPage } from "@/features/admin/components/admin-page"

export function AdminAccessPage() {
  return (
    <AdminPage
      title="Access check"
      description="Ask whether one person holds one permission on one resource, and see the relationships that decided it."
    >
      <AccessInspector />
    </AdminPage>
  )
}

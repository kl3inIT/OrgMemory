import { createFileRoute } from "@tanstack/react-router"

import { AdminUserPermissionsPage } from "@/features/admin/components/admin-user-permissions-page"

export const Route = createFileRoute("/admin/users/$userId")({
  component: RouteComponent,
  staticData: { title: "Permissions" },
})

function RouteComponent() {
  const { userId } = Route.useParams()
  const { session } = Route.useRouteContext()
  return <AdminUserPermissionsPage userId={userId} currentUserId={session.userId} />
}

import { createFileRoute } from "@tanstack/react-router"

import { AdminUsersPage } from "@/features/admin/components/admin-users-page"

export const Route = createFileRoute("/admin/users")({
  component: RouteComponent,
  staticData: { title: "Users" },
})

function RouteComponent() {
  const { session } = Route.useRouteContext()
  return <AdminUsersPage currentUserId={session.userId} />
}

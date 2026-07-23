import { createFileRoute, Outlet, redirect } from "@tanstack/react-router"

import { AdminSidebar } from "@/components/app-shell/admin-sidebar"
import { AppShell } from "@/components/app-shell/app-shell"
import { isAdministrator, requireBrowserSession } from "@/features/session/require-session"

export const Route = createFileRoute("/admin")({
  beforeLoad: async ({ context, location }) => {
    const session = await requireBrowserSession(context.queryClient, location.href)

    // A rendering guard only. Every /api/admin endpoint re-decides through OpenFGA,
    // so passing this check is not what grants anything.
    if (!isAdministrator(session)) {
      throw redirect({ to: "/", replace: true })
    }

    return { session }
  },
  component: AdminLayout,
})

function AdminLayout() {
  const { session } = Route.useRouteContext()
  return (
    <AppShell identity={session} sidebar={<AdminSidebar />}>
      <Outlet />
    </AppShell>
  )
}

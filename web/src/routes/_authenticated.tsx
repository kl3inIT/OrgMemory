import { createFileRoute, Outlet } from "@tanstack/react-router"

import { AppShell } from "@/components/app-shell/app-shell"
import { requireBrowserSession } from "@/features/session/require-session"

export const Route = createFileRoute("/_authenticated")({
  beforeLoad: async ({ context, location }) => ({
    session: await requireBrowserSession(context.queryClient, location.href),
  }),
  component: AuthenticatedLayout,
})

function AuthenticatedLayout() {
  const { session } = Route.useRouteContext()
  return (
    <AppShell identity={session}>
      <Outlet />
    </AppShell>
  )
}

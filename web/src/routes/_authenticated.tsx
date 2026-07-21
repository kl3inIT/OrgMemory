import { createFileRoute, Outlet, redirect } from "@tanstack/react-router"

import { AppShell } from "@/components/app-shell/app-shell"
import { browserLoginUrl } from "@/features/session/browser-login"
import { browserSessionQueryOptions } from "@/features/session/session-query"

export const Route = createFileRoute("/_authenticated")({
  beforeLoad: async ({ context, location }) => {
    const session = await context.queryClient.ensureQueryData(browserSessionQueryOptions())

    if (!session.authenticated) {
      throw redirect({ href: browserLoginUrl(location.href), replace: true })
    }

    return { session }
  },
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

import { useQueryErrorResetBoundary, type QueryClient } from "@tanstack/react-query"
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  type ErrorComponentProps,
  lazyRouteComponent,
  Link,
  useRouter,
} from "@tanstack/react-router"

import { ApplicationError } from "@/components/states/application-error"
import { PageLoading } from "@/components/states/page-loading"
import { Button } from "@/components/ui/button"

type RouterContext = {
  queryClient: QueryClient
}

type LoginSearch = {
  error?: string
  loggedOut?: boolean
}

const rootRoute = createRootRouteWithContext<RouterContext>()()

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  validateSearch: (search: Record<string, unknown>): LoginSearch => ({
    error: typeof search.error === "string" ? search.error : undefined,
    loggedOut: search.loggedOut === true || search.loggedOut === "true" || undefined,
  }),
  component: lazyRouteComponent(() => import("@/pages/login"), "LoginPage"),
})

const workspaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: lazyRouteComponent(() => import("@/pages/workspace"), "WorkspacePage"),
})

const routeTree = rootRoute.addChildren([loginRoute, workspaceRoute])

function RouteError({ error, reset }: ErrorComponentProps) {
  const router = useRouter()
  const queryErrorResetBoundary = useQueryErrorResetBoundary()

  function retry() {
    queryErrorResetBoundary.reset()
    reset()
    void router.invalidate()
  }

  return (
    <ApplicationError
      title="This page could not be loaded"
      description="The route or its data failed to load. Try the request again."
      error={error}
      onRetry={retry}
    />
  )
}

function RouteNotFound() {
  return (
    <main className="grid min-h-dvh place-items-center p-6">
      <section className="space-y-4 text-center" aria-labelledby="not-found-title">
        <p className="text-sm font-medium text-muted-foreground">404</p>
        <div className="space-y-2">
          <h1 id="not-found-title" className="text-xl font-semibold tracking-tight">
            Page not found
          </h1>
          <p className="text-sm text-muted-foreground">This address is not part of the current OrgMemory workspace.</p>
        </div>
        <Button asChild>
          <Link to="/">Return to workspace</Link>
        </Button>
      </section>
    </main>
  )
}

export const router = createRouter({
  routeTree,
  context: { queryClient: undefined! },
  defaultPreload: "intent",
  defaultPendingMs: 200,
  defaultPendingMinMs: 300,
  defaultPendingComponent: PageLoading,
  defaultErrorComponent: RouteError,
  defaultNotFoundComponent: RouteNotFound,
})

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}

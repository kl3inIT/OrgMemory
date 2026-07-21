import { createRootRoute, createRoute, createRouter, lazyRouteComponent, Link, Outlet } from "@tanstack/react-router"
import { AlertTriangle, Loader2 } from "lucide-react"
import { AppShell } from "@/components/app-shell"
import { AuthGate } from "@/components/auth-gate"
import { Button } from "@/components/ui/button"

const rootRoute = createRootRoute({ component: Outlet })

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "login",
  component: lazyRouteComponent(() => import("@/pages/login"), "LoginPage"),
})

const authenticatedRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "authenticated",
  component: () => (
    <AuthGate>
      <AppShell />
    </AuthGate>
  ),
})

const dashboardRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/",
  component: lazyRouteComponent(() => import("@/pages/dashboard"), "DashboardPage"),
})

const registryRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "registry",
  component: lazyRouteComponent(() => import("@/pages/registry"), "RegistryPage"),
})

const assetDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "assets/$assetId",
  component: lazyRouteComponent(() => import("@/pages/asset-detail"), "AssetDetailPage"),
})

const createRoutePage = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "create",
  component: lazyRouteComponent(() => import("@/pages/create-asset"), "CreateAssetPage"),
})

const reviewRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "review",
  component: lazyRouteComponent(() => import("@/pages/review-queue"), "ReviewQueuePage"),
})

const transferRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "transfer",
  component: lazyRouteComponent(() => import("@/pages/knowledge-transfer"), "KnowledgeTransferPage"),
})

const askRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "ask",
  component: lazyRouteComponent(() => import("@/pages/ask-memory"), "AskMemoryPage"),
})

const graphRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "graph",
  component: lazyRouteComponent(() => import("@/pages/knowledge-graph"), "KnowledgeGraphPage"),
})

const analyticsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "analytics",
  component: lazyRouteComponent(() => import("@/pages/analytics"), "AnalyticsPage"),
})

const settingsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "settings",
  component: lazyRouteComponent(() => import("@/pages/settings"), "SettingsPage"),
})

const routeTree = rootRoute.addChildren([
  loginRoute,
  authenticatedRoute.addChildren([
    dashboardRoute,
    registryRoute,
    assetDetailRoute,
    createRoutePage,
    reviewRoute,
    transferRoute,
    askRoute,
    graphRoute,
    analyticsRoute,
    settingsRoute,
  ]),
])

function RouteError({ error }: { error: Error }) {
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
      <AlertTriangle className="size-8 text-destructive" />
      <p className="text-sm font-medium">This page failed to render.</p>
      <p className="max-w-md truncate text-xs text-muted-foreground">{error.message}</p>
      <Button size="sm" variant="outline" onClick={() => window.location.reload()}>
        Reload
      </Button>
    </div>
  )
}

function RouteNotFound() {
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
      <p className="text-3xl font-bold">404</p>
      <p className="text-sm text-muted-foreground">Page not found.</p>
      <Button asChild size="sm" variant="outline">
        <Link to="/">Back to dashboard</Link>
      </Button>
    </div>
  )
}

function RoutePending() {
  return (
    <div className="flex w-full flex-1 items-center justify-center p-10">
      <Loader2 className="size-5 animate-spin text-muted-foreground" />
    </div>
  )
}

export const router = createRouter({
  routeTree,
  defaultPreload: "intent",
  defaultErrorComponent: RouteError,
  defaultNotFoundComponent: RouteNotFound,
  defaultPendingComponent: RoutePending,
})

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}

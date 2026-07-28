import { createRouter } from "@tanstack/react-router"

import { RouteError, RouteNotFound, RoutePending } from "@/components/states/route-error"
import { routeTree } from "@/routeTree.gen"

export const router = createRouter({
  routeTree,
  context: { queryClient: undefined! },
  defaultPreload: "intent",
  defaultPendingMs: 200,
  defaultPendingMinMs: 300,
  defaultPendingComponent: RoutePending,
  defaultErrorComponent: RouteError,
  defaultNotFoundComponent: RouteNotFound,
})

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}

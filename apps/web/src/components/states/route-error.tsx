import { useQueryErrorResetBoundary } from "@tanstack/react-query"
import { Link, useRouter, type ErrorComponentProps } from "@tanstack/react-router"

import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { Button } from "@/components/ui/button"

export function RouteError({ error, reset }: ErrorComponentProps) {
  const router = useRouter()
  const queryErrorResetBoundary = useQueryErrorResetBoundary()

  function retry() {
    queryErrorResetBoundary.reset()
    reset()
    void router.invalidate()
  }

  return (
    <div className="grid min-h-full flex-1 place-items-center p-6">
      <ErrorState
        title="This page could not be loaded"
        description="The route or its data failed to load. Try the request again."
        error={error}
        onRetry={retry}
      />
    </div>
  )
}

export function RoutePending() {
  return <LoadingState className="min-h-full flex-1" label="Loading page" />
}

export function RouteNotFound() {
  return (
    <section className="grid min-h-full flex-1 place-items-center p-6" aria-labelledby="not-found-title">
      <div className="space-y-4 text-center">
        <p className="text-sm font-medium text-muted-foreground">404</p>
        <div className="space-y-2">
          <h1 id="not-found-title" className="text-xl font-semibold tracking-tight">
            Page not found
          </h1>
          <p className="text-sm text-muted-foreground">This address is not part of OrgMemory.</p>
        </div>
        <Button asChild>
          <Link to="/">Return to Ask</Link>
        </Button>
      </div>
    </section>
  )
}

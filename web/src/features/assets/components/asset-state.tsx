import { AlertTriangle, RefreshCw } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

export function AssetPageLoading() {
  return (
    <div className="mx-auto grid w-full max-w-7xl gap-4 p-4 md:p-8" aria-label="Loading assets">
      <Skeleton className="h-10 w-56" />
      <Skeleton className="h-28 w-full" />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-48 w-full" />
        ))}
      </div>
    </div>
  )
}

export function AssetPageError({
  title = "This asset workspace is unavailable",
  onRetry,
}: {
  title?: string
  onRetry: () => void
}) {
  return (
    <div className="mx-auto w-full max-w-3xl p-4 md:p-8">
      <Card className="border-status-danger-border bg-status-danger-surface">
        <CardContent className="flex items-start gap-4 p-6">
          <AlertTriangle className="mt-0.5 size-5 text-status-danger-content" aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <h1 className="text-section-title text-content-primary">{title}</h1>
            <p className="mt-1 text-body text-content-secondary">
              Access may have changed, or the service may be temporarily unavailable.
            </p>
            <Button className="mt-4" variant="outline" onClick={onRetry}>
              <RefreshCw aria-hidden="true" />
              Retry
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

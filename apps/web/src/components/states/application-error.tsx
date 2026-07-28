import { AlertTriangle } from "lucide-react"
import type { ComponentProps } from "react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export type ErrorStateProps = ComponentProps<"section"> & {
  title?: string
  description?: string
  onRetry?: () => void
  error?: unknown
}

function errorDetails(error: unknown) {
  if (!import.meta.env.DEV || !(error instanceof Error)) return null
  return error.message
}

export function ErrorState({
  className,
  title = "Something went wrong",
  description = "OrgMemory could not complete this request. Try again.",
  onRetry,
  error,
  ...props
}: ErrorStateProps) {
  const details = errorDetails(error)

  return (
    <section
      className={cn("w-full max-w-md space-y-4 text-center", className)}
      aria-labelledby="application-error-title"
      {...props}
    >
      <AlertTriangle className="mx-auto size-7 text-destructive" aria-hidden="true" />
      <div className="space-y-2">
        <h1 id="application-error-title" className="text-xl font-semibold tracking-tight">
          {title}
        </h1>
        <p className="text-sm text-muted-foreground">{description}</p>
        {details ? <p className="break-words font-mono text-xs text-muted-foreground">{details}</p> : null}
      </div>
      {onRetry ? <Button onClick={onRetry}>Try again</Button> : null}
    </section>
  )
}

export function ApplicationError(props: ErrorStateProps) {
  return (
    <main className="grid min-h-dvh place-items-center p-6">
      <ErrorState {...props} />
    </main>
  )
}

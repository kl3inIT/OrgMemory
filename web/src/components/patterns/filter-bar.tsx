import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/lib/utils"

export function FilterBar({
  search,
  filters,
  result,
  actions,
  className,
  ...props
}: ComponentProps<"div"> & {
  search?: ReactNode
  filters?: ReactNode
  result?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div
      data-slot="filter-bar"
      className={cn("flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center", className)}
      {...props}
    >
      {search ? <div className="min-w-0 flex-1">{search}</div> : null}
      {filters ? <div className="flex flex-wrap items-center gap-2">{filters}</div> : null}
      {result ? (
        <div className="whitespace-nowrap text-sm text-content-muted" aria-live="polite">
          {result}
        </div>
      ) : null}
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  )
}

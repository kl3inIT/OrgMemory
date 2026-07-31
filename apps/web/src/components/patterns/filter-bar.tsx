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
      {search ? (
        <div data-slot="filter-bar-search" className="min-w-0 flex-1">
          {search}
        </div>
      ) : null}
      {filters ? (
        <div data-slot="filter-bar-filters" className="flex flex-wrap items-center gap-2">
          {filters}
        </div>
      ) : null}
      {result || actions ? (
        <div
          data-slot="filter-bar-trailing"
          className="flex w-full items-center gap-3 sm:ml-auto sm:w-auto"
        >
          {result ? (
            <div
              data-slot="filter-bar-result"
              className="mr-auto whitespace-nowrap text-sm text-content-muted sm:mr-0"
              aria-live="polite"
            >
              {result}
            </div>
          ) : null}
          {actions ? (
            <div data-slot="filter-bar-actions" className="flex items-center gap-2">
              {actions}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

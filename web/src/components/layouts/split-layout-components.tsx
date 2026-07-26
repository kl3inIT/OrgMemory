import type { ComponentProps } from "react"

import { cn } from "@/lib/utils"

export function SplitRoot({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="split-layout"
      className={cn("flex min-h-0 min-w-0 flex-1 overflow-hidden", className)}
      {...props}
    />
  )
}

export function SplitMain({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="split-layout-main"
      className={cn("min-h-0 min-w-0 flex-1 overflow-auto", className)}
      {...props}
    />
  )
}

export function SplitAside({ className, ...props }: ComponentProps<"aside">) {
  return (
    <aside
      data-slot="split-layout-aside"
      className={cn(
        "min-h-0 w-(--detail-panel-width) shrink-0 overflow-y-auto border-l border-border-subtle bg-surface-base",
        className,
      )}
      {...props}
    />
  )
}

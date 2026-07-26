import type { ComponentProps } from "react"

import { cn } from "@/lib/utils"

export function SplitRoot({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="split-layout"
      className={cn(
        "relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden lg:flex-row",
        className,
      )}
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
        "min-h-0 max-h-1/2 w-full shrink-0 overflow-y-auto border-t border-border-subtle bg-surface-base lg:max-h-none lg:w-(--detail-panel-width) lg:border-l lg:border-t-0",
        className,
      )}
      {...props}
    />
  )
}

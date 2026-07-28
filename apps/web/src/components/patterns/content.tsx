import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/lib/utils"

export function Content({
  title,
  description,
  icon,
  metadata,
  size = "default",
  className,
  ...props
}: Omit<ComponentProps<"div">, "title"> & {
  title: ReactNode
  description?: ReactNode
  icon?: ReactNode
  metadata?: ReactNode
  size?: "compact" | "default" | "section"
}) {
  return (
    <div
      data-slot="content"
      data-size={size}
      className={cn("flex min-w-0 items-start gap-3", className)}
      {...props}
    >
      {icon ? (
        <span
          className={cn(
            "grid shrink-0 place-items-center text-content-muted",
            size === "compact" ? "size-5" : size === "section" ? "size-7" : "size-6",
          )}
        >
          {icon}
        </span>
      ) : null}
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <div
            className={cn(
              "min-w-0 text-content-primary",
              size === "compact"
                ? "text-sm font-medium"
                : size === "section"
                  ? "text-section-title"
                  : "text-label",
            )}
          >
            {title}
          </div>
          {metadata}
        </div>
        {description ? (
          <div
            className={cn("mt-1 text-content-muted", size === "compact" ? "text-xs" : "text-sm")}
          >
            {description}
          </div>
        ) : null}
      </div>
    </div>
  )
}

export function ContentAction({
  action,
  className,
  contentClassName,
  ...props
}: ComponentProps<typeof Content> & {
  action?: ReactNode
  contentClassName?: string
}) {
  return (
    <div
      data-slot="content-action"
      className={cn("flex min-w-0 items-start justify-between gap-4", className)}
    >
      <Content className={cn("flex-1", contentClassName)} {...props} />
      {action ? <div className="flex shrink-0 items-center gap-2">{action}</div> : null}
    </div>
  )
}

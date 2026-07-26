import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/lib/utils"

export function EmptyState({
  title,
  description,
  icon,
  action,
  className,
  ...props
}: Omit<ComponentProps<"section">, "title"> & {
  title: ReactNode
  description?: ReactNode
  icon?: ReactNode
  action?: ReactNode
}) {
  return (
    <section
      data-slot="empty-state"
      className={cn("grid min-h-64 place-items-center px-6 py-10 text-center", className)}
      {...props}
    >
      <div className="max-w-md space-y-4">
        {icon ? (
          <span className="mx-auto grid size-11 place-items-center rounded-full border border-border-default bg-surface-subtle text-content-muted">
            {icon}
          </span>
        ) : null}
        <div className="space-y-1.5">
          <h2 className="text-sm font-medium text-content-primary">{title}</h2>
          {description ? <p className="text-sm text-content-muted">{description}</p> : null}
        </div>
        {action ? <div className="flex justify-center">{action}</div> : null}
      </div>
    </section>
  )
}

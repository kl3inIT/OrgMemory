import { cva, type VariantProps } from "class-variance-authority"
import type { ComponentProps, ReactNode } from "react"

import { cn } from "@/lib/utils"

const pageContainerVariants = cva(
  "mx-auto flex w-full min-w-0 flex-1 flex-col px-(--page-gutter-compact) py-4 md:px-(--page-gutter) md:py-8",
  {
    variants: {
      variant: {
        narrow: "max-w-(--page-width-narrow)",
        standard: "max-w-(--page-width-standard)",
        wide: "max-w-(--page-width-wide)",
        full: "max-w-none",
        canvas: "min-h-0 max-w-none gap-4 px-(--workspace-gutter) py-(--workspace-gutter)",
      },
    },
    defaultVariants: {
      variant: "standard",
    },
  },
)

type PageLayoutVariant = NonNullable<VariantProps<typeof pageContainerVariants>["variant"]>

export function PageRoot({
  variant = "standard",
  className,
  children,
  ...props
}: ComponentProps<"main"> & { variant?: PageLayoutVariant }) {
  const canvas = variant === "canvas"

  return (
    <main
      data-slot="page-layout"
      data-variant={variant}
      className={cn(
        "min-h-0 min-w-0 flex-1",
        canvas ? "flex overflow-hidden" : "overflow-y-auto",
        className,
      )}
      {...props}
    >
      <div className={pageContainerVariants({ variant })}>{children}</div>
    </main>
  )
}

export function PageHeader({
  title,
  description,
  icon,
  breadcrumb,
  metadata,
  actions,
  className,
  children,
  ...props
}: Omit<ComponentProps<"header">, "title"> & {
  title: ReactNode
  description?: ReactNode
  icon?: ReactNode
  breadcrumb?: ReactNode
  metadata?: ReactNode
  actions?: ReactNode
}) {
  return (
    <header
      data-slot="page-header"
      className={cn("flex shrink-0 flex-col gap-4", className)}
      {...props}
    >
      {breadcrumb}
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row">
        <div className="min-w-0 space-y-1.5">
          <div className="flex min-w-0 flex-wrap items-center gap-3">
            {icon ? (
              <span className="grid size-8 shrink-0 place-items-center text-content-secondary">
                {icon}
              </span>
            ) : null}
            <h1 className="min-w-0 break-words text-page-title text-content-primary">{title}</h1>
            {metadata}
          </div>
          {description ? (
            <p className="max-w-3xl text-sm text-content-muted">{description}</p>
          ) : null}
        </div>
        {actions ? (
          <div
            data-slot="page-header-actions"
            className="flex w-full flex-wrap items-center justify-start gap-2 sm:w-auto sm:shrink-0 sm:justify-end"
          >
            {actions}
          </div>
        ) : null}
      </div>
      {children}
    </header>
  )
}

export function PageTabs({ className, ...props }: ComponentProps<"div">) {
  return (
    <div data-slot="page-tabs" className={cn("flex shrink-0 items-center", className)} {...props} />
  )
}

export function PageToolbar({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="page-toolbar"
      className={cn(
        "flex shrink-0 flex-col gap-3 sm:flex-row sm:items-center sm:justify-between",
        className,
      )}
      {...props}
    />
  )
}

export function PageBody({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="page-body"
      className={cn("flex min-w-0 flex-col gap-6 pt-6", className)}
      {...props}
    />
  )
}

export function PageCanvas({ className, ...props }: ComponentProps<"section">) {
  return (
    <section
      data-slot="page-canvas"
      className={cn(
        "relative flex min-h-0 min-w-0 flex-1 overflow-hidden rounded-xl border border-border-default bg-surface-raised",
        className,
      )}
      {...props}
    />
  )
}

export type { PageLayoutVariant }

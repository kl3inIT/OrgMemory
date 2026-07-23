import { ListFilterPlus } from "lucide-react"
import type { ReactNode } from "react"

import { Card, CardContent } from "@/components/ui/card"

/**
 * The shared frame for every administration screen: one heading, an optional
 * explanation of what the screen governs, and a scrollable body. Pages compose this
 * rather than repeating page chrome.
 */
export function AdminPage({
  title,
  description,
  icon,
  actions,
  children,
}: {
  title: string
  description?: string
  /** A mark for the thing this screen governs, when the screen is about one specific source. */
  icon?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-7xl space-y-6 p-4 md:p-8">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2.5">
              {icon ? <span className="grid size-7 shrink-0 place-items-center">{icon}</span> : null}
              <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            </div>
            {description ? <p className="max-w-2xl text-sm text-muted-foreground">{description}</p> : null}
          </div>
          {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
        </header>
        {children}
      </div>
    </div>
  )
}

/** A row of counts that summarizes what the table below it contains. */
export function AdminStats({
  stats,
}: {
  stats: {
    label: string
    value: number | string
    hint?: string
    active?: boolean
    onSelect?: () => void
  }[]
}) {
  return (
    <Card className="overflow-hidden py-0">
      <CardContent className="grid grid-cols-2 p-0 sm:auto-cols-fr sm:grid-flow-col sm:grid-cols-none">
        {stats.map((stat) => (
          <div
            key={stat.label}
            className="group/stat relative border-r border-b border-border-subtle last:border-r-0 sm:border-b-0"
          >
            {stat.onSelect ? (
              <button
                type="button"
                className="flex min-h-20 w-full flex-col items-start justify-center gap-0.5 px-4 py-3 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring data-[active=true]:bg-surface-raised"
                data-active={stat.active}
                aria-pressed={stat.active}
                onClick={stat.onSelect}
              >
                <span className="text-xl font-semibold tabular-nums">{stat.value}</span>
                <span className="text-xs font-medium text-muted-foreground">{stat.label}</span>
                <ListFilterPlus
                  className="absolute right-3 top-3 size-3.5 text-muted-foreground opacity-0 transition-opacity group-hover/stat:opacity-100 group-focus-within/stat:opacity-100"
                  aria-hidden="true"
                />
              </button>
            ) : (
              <div className="flex min-h-20 flex-col items-start justify-center gap-0.5 px-4 py-3">
                <span className="text-xl font-semibold tabular-nums">{stat.value}</span>
                <span className="text-xs font-medium text-muted-foreground">{stat.label}</span>
                {stat.hint ? (
                  <span className="text-xs text-muted-foreground">{stat.hint}</span>
                ) : null}
              </div>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

/** A titled table container, so each concern on a page reads as its own block. */
export function AdminSection({
  title,
  description,
  icon,
  actions,
  toolbar,
  footer,
  children,
}: {
  title: string
  description?: string
  icon?: ReactNode
  actions?: ReactNode
  toolbar?: ReactNode
  footer?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="flex items-center gap-2 text-base font-semibold tracking-tight">
            {icon ? <span className="grid size-4 shrink-0 place-items-center">{icon}</span> : null}
            {title}
          </h2>
          {description ? <p className="max-w-2xl text-sm text-muted-foreground">{description}</p> : null}
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </div>
      <Card className="overflow-hidden py-0">
        {toolbar ? (
          <div className="flex flex-wrap items-center gap-2 border-b border-border-subtle p-3">
            {toolbar}
          </div>
        ) : null}
        <div className="overflow-x-auto">{children}</div>
        {footer ? (
          <div className="flex items-center border-t border-border-subtle px-3 py-2">
            {footer}
          </div>
        ) : null}
      </Card>
    </section>
  )
}

export function AdminEmpty({ title, description }: { title: string; description: string }) {
  return (
    <div className="grid min-h-52 place-items-center px-6 text-center">
      <div className="space-y-1">
        <p className="text-sm font-medium">{title}</p>
        <p className="max-w-md text-sm text-muted-foreground">{description}</p>
      </div>
    </div>
  )
}

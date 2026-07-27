import { ListFilterPlus } from "lucide-react"
import type { ReactNode } from "react"

import { PageLayout } from "@/components/layouts/page-layout"
import { ContentAction } from "@/components/patterns/content"
import { EmptyState } from "@/components/patterns/empty-state"
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
    <PageLayout.Root variant="wide">
      <PageLayout.Header title={title} description={description} icon={icon} actions={actions} />
      <PageLayout.Body>{children}</PageLayout.Body>
    </PageLayout.Root>
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
      <ContentAction
        title={title}
        description={description}
        icon={icon}
        size="section"
        action={actions}
      />
      <Card className="overflow-hidden py-0">
        {toolbar ? (
          <div className="flex flex-wrap items-center gap-2 border-b border-border-subtle p-3">
            {toolbar}
          </div>
        ) : null}
        <div className="overflow-x-auto">{children}</div>
        {footer ? (
          <div className="flex items-center border-t border-border-subtle px-3 py-2">{footer}</div>
        ) : null}
      </Card>
    </section>
  )
}

export function AdminEmpty({ title, description }: { title: string; description: string }) {
  return <EmptyState className="min-h-52" title={title} description={description} />
}

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
  actions,
  children,
}: {
  title: string
  description?: string
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <main className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-6xl space-y-6 p-4 md:p-8">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            {description ? <p className="max-w-2xl text-sm text-muted-foreground">{description}</p> : null}
          </div>
          {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
        </header>
        {children}
      </div>
    </main>
  )
}

/** A row of counts that summarizes what the table below it contains. */
export function AdminStats({ stats }: { stats: { label: string; value: number | string; hint?: string }[] }) {
  return (
    <Card>
      <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="space-y-0.5">
            <p className="text-2xl font-semibold tabular-nums">{stat.value}</p>
            <p className="text-sm font-medium">{stat.label}</p>
            {stat.hint ? <p className="text-xs text-muted-foreground">{stat.hint}</p> : null}
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
  actions,
  children,
}: {
  title: string
  description?: string
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="text-base font-semibold tracking-tight">{title}</h2>
          {description ? <p className="max-w-2xl text-sm text-muted-foreground">{description}</p> : null}
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </div>
      <Card className="overflow-hidden py-0">
        <div className="overflow-x-auto">{children}</div>
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

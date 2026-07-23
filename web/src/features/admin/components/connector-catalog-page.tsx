import { Link } from "@tanstack/react-router"
import { ArrowLeft } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { CONNECTOR_CATALOG, type ConnectorCatalogEntry } from "@/features/admin/connector-catalog"
import { AdminPage } from "@/features/admin/components/admin-page"

export function ConnectorCatalogPage() {
  return (
    <AdminPage
      title="Add a source"
      description="Where evidence comes from. A source only reaches retrieval through the same governed ledger, whichever of these it arrived by."
      actions={
        <Button variant="outline" asChild>
          <Link to="/admin/connectors">
            <ArrowLeft aria-hidden="true" />
            Configured sources
          </Link>
        </Button>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {CONNECTOR_CATALOG.map((entry) => (
          <SourceTile key={entry.id} entry={entry} />
        ))}
      </div>
    </AdminPage>
  )
}

function SourceTile({ entry }: { entry: ConnectorCatalogEntry }) {
  const body = (
    <CardContent className="flex h-full flex-col gap-3 p-5">
      <div className="flex items-center gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-surface-raised">
          <entry.icon className="size-4" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate font-medium">{entry.name}</div>
          <div className="text-xs text-muted-foreground">{entry.kind}</div>
        </div>
        {entry.unavailable ? <Badge variant="muted">Not yet</Badge> : null}
      </div>
      <p className="text-sm text-muted-foreground">{entry.description}</p>
      {entry.unavailable ? (
        <p className="mt-auto text-xs text-muted-foreground">{entry.unavailable}</p>
      ) : null}
    </CardContent>
  )

  if (!entry.to) {
    return (
      <Card className="h-full border-dashed py-0 opacity-70" aria-disabled="true">
        {body}
      </Card>
    )
  }

  return (
    <Link to={entry.to} className="rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-focus-ring">
      <Card className="h-full py-0 transition-colors hover:bg-surface-subtle">{body}</Card>
    </Link>
  )
}

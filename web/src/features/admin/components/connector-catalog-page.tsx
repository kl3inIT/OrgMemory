import { useQuery } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { ArrowLeft, TriangleAlert } from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { adminConnectorSourcesQueryOptions } from "@/features/admin/admin-queries"
import {
  CATALOG_GROUPS,
  CONNECTOR_CATALOG,
  type ConnectorCatalogEntry,
} from "@/features/admin/connector-catalog"
import { AdminPage } from "@/features/admin/components/admin-page"
import { SourceIcon } from "@/features/admin/components/source-icon"

/**
 * Whether a tile can be picked, and if not, whose fact that is.
 *
 * <p>`missing` is the product not having built it; `uninstalled` is this deployment not
 * carrying the adapter. Collapsing the two would let a catalogue promise Slack on a build with
 * no Slack adapter, which fails later as an unexplained error on the configuration screen
 * rather than here as a sentence.
 */
type Availability =
  | { state: "ready" }
  | { state: "missing"; reason: string }
  | { state: "uninstalled" }

function availabilityOf(entry: ConnectorCatalogEntry, installed: Set<string> | undefined): Availability {
  if (entry.unavailable) return { state: "missing", reason: entry.unavailable }
  // Nothing is crawled for this source, so there is no adapter that could be absent.
  if (!entry.sourceSystem) return { state: "ready" }
  // While the answer is unknown the tile stays pickable rather than claiming absence, and the
  // page says so above. Guessing "unavailable" from a failed request would be a worse lie than
  // letting the configuration screen refuse.
  if (!installed) return { state: "ready" }
  return installed.has(entry.sourceSystem) ? { state: "ready" } : { state: "uninstalled" }
}

export function ConnectorCatalogPage() {
  const installed = useQuery(adminConnectorSourcesQueryOptions())

  const installedSystems = installed.data
    ? new Set(
        installed.data
          .map((source) => source.sourceSystem)
          .filter((name): name is string => typeof name === "string"),
      )
    : undefined

  return (
    <AdminPage
      title="Add a source"
      actions={
        <Button variant="outline" asChild>
          <Link to="/admin/connectors">
            <ArrowLeft aria-hidden="true" />
            Configured sources
          </Link>
        </Button>
      }
    >
      {installed.isError ? (
        <Alert variant="destructive">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>Which sources this deployment can ingest could not be read</AlertTitle>
          <AlertDescription>
            Everything below is still listed, but a source shown here may have no adapter
            installed, in which case configuring it will be refused.
          </AlertDescription>
        </Alert>
      ) : null}

      {CATALOG_GROUPS.map((group) => {
        const entries = CONNECTOR_CATALOG.filter((entry) => entry.aclAuthority === group.aclAuthority)
        if (entries.length === 0) return null
        return (
          <section key={group.aclAuthority} className="space-y-3">
            <div className="space-y-1">
              <h2 className="text-base font-semibold tracking-tight">{group.title}</h2>
              <p className="max-w-2xl text-sm text-muted-foreground">{group.description}</p>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {entries.map((entry) => (
                <SourceTile
                  key={entry.id}
                  entry={entry}
                  availability={availabilityOf(entry, installedSystems)}
                />
              ))}
            </div>
          </section>
        )
      })}
    </AdminPage>
  )
}

function SourceTile({
  entry,
  availability,
}: {
  entry: ConnectorCatalogEntry
  availability: Availability
}) {
  const body = (
    <CardContent className="flex h-full flex-col gap-3 p-5">
      <div className="flex items-center gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-surface-raised">
          <SourceIcon name={entry.icon} className="size-4" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate font-medium">{entry.name}</div>
        </div>
        {availability.state === "missing" ? <Badge variant="muted">Not yet</Badge> : null}
        {availability.state === "uninstalled" ? <Badge variant="muted">Not installed</Badge> : null}
      </div>
      <p className="text-sm text-muted-foreground">{entry.description}</p>
      {availability.state === "missing" ? (
        <p className="mt-auto text-xs text-muted-foreground">{availability.reason}</p>
      ) : null}
      {availability.state === "uninstalled" ? (
        <p className="mt-auto text-xs text-muted-foreground">
          This build carries no adapter for {entry.name}, so there is nothing to configure.
        </p>
      ) : null}
    </CardContent>
  )

  if (availability.state !== "ready" || !entry.to) {
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

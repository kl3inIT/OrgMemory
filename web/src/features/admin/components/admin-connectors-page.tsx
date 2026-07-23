import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { ChevronRight, Plus, TriangleAlert } from "lucide-react"
import { useState, type ReactNode } from "react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { formatDay, formatTimestamp } from "@/features/admin/admin-labels"
import {
  adminConnectionsQueryOptions,
  adminConnectorSourcesQueryOptions,
  invalidateAdminData,
  knowledgeSpacesQueryOptions,
} from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage } from "@/features/admin/components/admin-page"
import { SourceIcon, type SourceIconName } from "@/features/admin/components/source-icon"
import { CONNECTOR_CATALOG } from "@/features/admin/connector-catalog"
import { probeIsGood, probeReason } from "@/features/admin/connector-probe"
import {
  forgetAdminConnectionCredentialMutation,
  testAdminConnectionMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminConnectionResponse, AdminConnectorProbeResponse } from "@/lib/hey-api"

/**
 * What a connection is actually doing, which is not the same as what it was set to.
 *
 * <p>The state worth naming is `Invalid`. A connection can be switched on, pointed at a Space,
 * and still read nothing, because nobody stored a credential for it — and it reads as healthy
 * on a screen that only shows the switch. Onyx names the same state and its badge says what to
 * do about it rather than only that it is broken, which is the part worth copying.
 *
 * <p>The vocabulary is Onyx's: a screen answers "can this content be found yet", and that is
 * indexing. Crawling is the half of the work that fetches, and it keeps that name in the code
 * and the ledger, where the distinction is real.
 */
function connectionState(connection: AdminConnectionResponse) {
  if (!connection.crawlEnabled) {
    return { label: "Paused", variant: "outline" as const, blocked: false }
  }
  if (!connection.credentialSet) {
    return { label: "Invalid", variant: "warning" as const, blocked: true }
  }
  return { label: "Indexing", variant: "success" as const, blocked: false }
}

/** A source's mark and display name, from the catalogue, falling back to what the API said. */
function presentation(sourceSystem: string, displayName?: string) {
  const catalogued = CONNECTOR_CATALOG.find((entry) => entry.sourceSystem === sourceSystem)
  return {
    name: catalogued?.name ?? displayName ?? sourceSystem,
    icon: catalogued?.icon as SourceIconName | undefined,
  }
}

export function AdminConnectorsPage() {
  const queryClient = useQueryClient()
  const [checked, setChecked] = useState<{ key: string; result: AdminConnectorProbeResponse }>()

  // Which sources exist is the deployment's answer, not this file's. Everything below is
  // driven by it, so a second adapter appears here without a line changing.
  const sources = useQuery(adminConnectorSourcesQueryOptions())
  const spaces = useQuery(knowledgeSpacesQueryOptions())

  const installed: { system: string; displayName?: string }[] = (sources.data ?? []).flatMap(
    (source) => (source.sourceSystem ? [{ system: source.sourceSystem, displayName: source.displayName }] : []),
  )

  const connectionQueries = useQueries({
    queries: installed.map((source) => adminConnectionsQueryOptions(source.system)),
  })

  const forget = useMutation({
    ...forgetAdminConnectionCredentialMutation(),
    onSuccess: async () => {
      await invalidateAdminData(queryClient)
      toast.success("Credential forgotten. This connection can no longer authenticate.")
    },
    onError: () => toast.error("The credential could not be removed."),
  })

  const check = useMutation({
    ...testAdminConnectionMutation(),
    onSuccess: (result, variables) => setChecked({ key: String(variables.path.connectionKey), result }),
    onError: () => toast.error("The stored credential could not be checked."),
  })

  if (sources.isPending || spaces.isPending || connectionQueries.some((query) => query.isPending)) {
    return <LoadingState label="Loading sources" className="min-h-full flex-1" />
  }

  const failed = [sources, spaces, ...connectionQueries].find((query) => query.isError)
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Sources could not be loaded"
          description="Administration requires organization administrator permission."
          error={failed.error}
          onRetry={() => {
            void sources.refetch()
            void spaces.refetch()
            connectionQueries.forEach((query) => void query.refetch())
          }}
        />
      </div>
    )
  }

  const spaceRows = spaces.data ?? []
  const groups = installed
    .map((source, index) => ({ ...source, rows: connectionQueries[index]?.data ?? [] }))
    // A source nobody has connected anything to is not a heading over an empty table. Onyx
    // shows one empty state for the whole page rather than one per source it supports.
    .filter((group) => group.rows.length > 0)

  const allRows = groups.flatMap((group) => group.rows)
  // Carried with its source, because what it links to is that source's wizard. Flattening the
  // rows alone loses which one each came from.
  const blocked = groups.flatMap((group) =>
    group.rows
      .filter((row) => connectionState(row).blocked)
      .map((row) => ({ system: group.system, key: row.sourceConnectionKey ?? "" })),
  )

  return (
    <AdminPage
      title="Sources"
      actions={
        <Button asChild>
          <Link to="/admin/connectors/new">
            <Plus aria-hidden="true" />
            Add a source
          </Link>
        </Button>
      }
    >
      {allRows.length === 0 ? (
        <AdminEmpty
          title="Nothing is connected yet"
          description="Add a source to connect one. Uploads and edge capture reach the same governed ledger without a connection."
        />
      ) : (
        <>
          {blocked.length > 0 ? (
            <Alert variant="destructive">
              <TriangleAlert aria-hidden="true" />
              <AlertTitle>
                {blocked.length === 1
                  ? "One connection is switched on but cannot read anything"
                  : `${blocked.length} connections are switched on but cannot read anything`}
              </AlertTitle>
              <AlertDescription className="space-y-2">
                <p>Store a credential, or switch the connection off until you have one.</p>
                <div className="flex flex-wrap gap-2">
                  {blocked.map((connection) => (
                    <Button key={`${connection.system}/${connection.key}`} size="sm" variant="outline" asChild>
                      <Link
                        to="/admin/connectors/$sourceSystem"
                        params={{ sourceSystem: connection.system }}
                        search={{ connection: connection.key, step: "credential" }}
                      >
                        {connection.key}
                      </Link>
                    </Button>
                  ))}
                </div>
              </AlertDescription>
            </Alert>
          ) : null}

          {groups.map((group) => {
            const shown = presentation(group.system, group.displayName)
            return (
              <SourceGroup
                key={group.system}
                name={shown.name}
                icon={shown.icon}
                connections={group.rows.length}
                indexing={group.rows.filter((row) => connectionState(row).label === "Indexing").length}
                invalid={group.rows.filter((row) => connectionState(row).blocked).length}
              >
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead>Status</TableHead>
                      <TableHead>Connection</TableHead>
                      <TableHead>Publishes into</TableHead>
                      <TableHead>Credential</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {group.rows.map((connection) => {
                      const key = connection.sourceConnectionKey ?? ""
                      const state = connectionState(connection)
                      const space = spaceRows.find(
                        (candidate) => candidate.id === connection.knowledgeSpaceId,
                      )
                      const result = checked?.key === key ? checked.result : undefined
                      return (
                        <TableRow key={key}>
                          <TableCell>
                            <Badge variant={state.variant}>{state.label}</Badge>
                          </TableCell>
                          <TableCell>
                            <div className="max-w-52">
                              <div className="truncate font-medium" title={key}>
                                {key}
                              </div>
                              {result ? (
                                <div
                                  className={
                                    probeIsGood(result)
                                      ? "mt-0.5 text-xs text-muted-foreground"
                                      : "mt-0.5 text-xs text-destructive"
                                  }
                                >
                                  {probeReason(group.system, result)}
                                </div>
                              ) : null}
                            </div>
                          </TableCell>
                          <TableCell className="text-muted-foreground">
                            {space?.name ?? space?.key ?? "Not set"}
                          </TableCell>
                          <TableCell>
                            {connection.credentialSet ? (
                              <div className="flex items-center gap-2 whitespace-nowrap">
                                <Badge variant="secondary">Stored</Badge>
                                <span
                                  className="text-xs text-muted-foreground"
                                  title={formatTimestamp(connection.credentialSetAt)}
                                >
                                  {formatDay(connection.credentialSetAt)}
                                </span>
                              </div>
                            ) : (
                              <Badge variant="warning">None</Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-2">
                              <Button size="sm" variant="outline" asChild>
                                <Link
                                  to="/admin/connectors/$sourceSystem/$connectionKey"
                                  params={{ sourceSystem: group.system, connectionKey: key }}
                                >
                                  Open
                                </Link>
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={check.isPending}
                                onClick={() =>
                                  check.mutate({
                                    path: { sourceSystem: group.system, connectionKey: key },
                                  })
                                }
                              >
                                Test
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={!connection.credentialSet || forget.isPending}
                                onClick={() =>
                                  forget.mutate({
                                    path: { sourceSystem: group.system, connectionKey: key },
                                  })
                                }
                              >
                                Forget
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </SourceGroup>
            )
          })}
        </>
      )}
    </AdminPage>
  )
}

/**
 * One source, collapsible, with its counts on the header.
 *
 * <p>This is Onyx's arrangement for the same screen: a summary row per source carrying the
 * mark, the name and the totals, which opens onto that source's connections. The counts belong
 * here rather than in a page-level row because the question is per source — twelve Slack
 * workspaces indexing and one Drive connection invalid is not four numbers, it is two rows.
 */
function SourceGroup({
  name,
  icon,
  connections,
  indexing,
  invalid,
  children,
}: {
  name: string
  icon?: SourceIconName
  connections: number
  indexing: number
  invalid: number
  children: ReactNode
}) {
  const [open, setOpen] = useState(true)
  return (
    <Card className="overflow-hidden py-0">
      <Collapsible open={open} onOpenChange={setOpen}>
        <CollapsibleTrigger className="flex w-full flex-wrap items-center gap-x-6 gap-y-3 p-4 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring">
          <span className="flex min-w-52 flex-1 items-center gap-2.5">
            <ChevronRight
              className="size-4 shrink-0 text-muted-foreground transition-transform data-[open=true]:rotate-90"
              data-open={open}
              aria-hidden="true"
            />
            {icon ? <SourceIcon name={icon} className="size-5" /> : null}
            <span className="text-lg font-semibold tracking-tight">{name}</span>
          </span>
          <GroupCount label="Connections" value={connections} />
          <GroupCount label="Indexing" value={`${indexing}/${connections}`} />
          <GroupCount label="Invalid" value={invalid} alarming={invalid > 0} />
        </CollapsibleTrigger>
        <CollapsibleContent>
          <div className="overflow-x-auto border-t border-border-subtle">{children}</div>
        </CollapsibleContent>
      </Collapsible>
    </Card>
  )
}

function GroupCount({
  label,
  value,
  alarming,
}: {
  label: string
  value: number | string
  alarming?: boolean
}) {
  return (
    <span className="min-w-24">
      <span className="block text-xs text-muted-foreground">{label}</span>
      <span
        className={
          alarming
            ? "block text-lg font-semibold tabular-nums text-status-warning-content"
            : "block text-lg font-semibold tabular-nums"
        }
      >
        {value}
      </span>
    </span>
  )
}

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { ChevronRight, MoreHorizontal, Plus, TriangleAlert } from "lucide-react"
import { useState, type MouseEvent, type ReactNode } from "react"
import { toast } from "sonner"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { formatDay } from "@/features/admin/admin-labels"
import {
  adminQuery,
  invalidateAdminData,
} from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage } from "@/features/admin/components/admin-page"
import { SourceIcon, type SourceIconName } from "@/features/admin/components/source-icon"
import { CONNECTOR_CATALOG } from "@/features/admin/connector-catalog"
import { probeIsGood, probeReason } from "@/features/admin/connector-probe"
import {
  deleteAdminConnectionMutation,
  forgetAdminConnectionCredentialMutation,
  listAdminConnectionsOptions,
  listAdminConnectorSourcesOptions,
  listKnowledgeSpaceUploadTargetsOptions,
  requestAdminConnectionCrawlMutation,
  testAdminConnectionMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminConnectionResponse, AdminConnectorProbeResponse } from "@/lib/hey-api"
import { formatDate } from "@/lib/format"

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
  // Forgetting a credential cannot be undone, so it is held until confirmed rather than done on
  // the click that asked for it. The connection it belongs to is carried because the dialog is
  // one, at the page, rather than one mounted per row.
  const [forgetTarget, setForgetTarget] = useState<{ system: string; key: string }>()
  const [deleteTarget, setDeleteTarget] = useState<{ system: string; key: string }>()

  // Which sources exist is the deployment's answer, not this file's. Everything below is
  // driven by it, so a second adapter appears here without a line changing.
  const sources = useQuery(adminQuery(listAdminConnectorSourcesOptions()))
  const spaces = useQuery(adminQuery(listKnowledgeSpaceUploadTargetsOptions()))

  const installed: { system: string; displayName?: string }[] = (sources.data ?? []).flatMap(
    (source) => (source.sourceSystem ? [{ system: source.sourceSystem, displayName: source.displayName }] : []),
  )

  const connectionQueries = useQueries({
    queries: installed.map((source) =>
      adminQuery(listAdminConnectionsOptions({ path: { sourceSystem: source.system } })),
    ),
  })

  const forget = useMutation({
    ...forgetAdminConnectionCredentialMutation(),
    onSuccess: async () => {
      setForgetTarget(undefined)
      await invalidateAdminData(queryClient)
      toast.success("Credential forgotten. This connection can no longer authenticate.")
    },
    onError: () => toast.error("The credential could not be removed."),
  })

  const remove = useMutation({
    ...deleteAdminConnectionMutation(),
    onSuccess: async () => {
      setDeleteTarget(undefined)
      await invalidateAdminData(queryClient)
      toast.success("Connection deleted. Its credential and crawl configuration were removed.")
    },
    onError: () => toast.error("The connection could not be deleted."),
  })

  const check = useMutation({
    ...testAdminConnectionMutation(),
    onSuccess: (result, variables) => setChecked({ key: String(variables.path.connectionKey), result }),
    onError: () => toast.error("The stored credential could not be checked."),
  })

  const crawlNow = useMutation({
    ...requestAdminConnectionCrawlMutation(),
    onSuccess: () =>
      toast.success("Crawl requested. The worker reads its content on the next poll."),
    onError: () => toast.error("The crawl could not be requested."),
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
            const columns: ColumnDef<AdminConnectionResponse>[] = [
              {
                id: "status",
                accessorFn: (connection) => connectionState(connection).label,
                header: "Status",
                enableSorting: true,
                cell: ({ row }) => {
                  const state = connectionState(row.original)
                  return <Badge variant={state.variant}>{state.label}</Badge>
                },
              },
              {
                accessorKey: "sourceConnectionKey",
                header: "Connection",
                enableSorting: true,
                cell: ({ row }) => {
                  const key = row.original.sourceConnectionKey ?? ""
                  const result = checked?.key === key ? checked.result : undefined
                  return (
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
                  )
                },
              },
              {
                id: "space",
                accessorFn: (connection) => {
                  const space = spaceRows.find(
                    (candidate) => candidate.id === connection.knowledgeSpaceId,
                  )
                  return space?.name ?? space?.key ?? "Not set"
                },
                header: "Publishes into",
                enableSorting: true,
                meta: { cellClassName: "text-muted-foreground" },
                cell: ({ getValue }) => String(getValue()),
              },
              {
                id: "credential",
                accessorFn: (connection) =>
                  connection.credentialSet ? "Stored" : "None",
                header: "Credential",
                enableSorting: true,
                cell: ({ row }) =>
                  row.original.credentialSet ? (
                    <div className="flex items-center gap-2 whitespace-nowrap">
                      <Badge variant="secondary">Stored</Badge>
                      <span
                        className="text-xs text-muted-foreground"
                        title={formatDate(row.original.credentialSetAt)}
                      >
                        {formatDay(row.original.credentialSetAt)}
                      </span>
                    </div>
                  ) : (
                    <Badge variant="warning">None</Badge>
                  ),
              },
              {
                id: "actions",
                header: "Actions",
                meta: {
                  headerClassName: "text-right",
                  cellClassName: "text-right",
                },
                cell: ({ row }) => {
                  const connection = row.original
                  const key = connection.sourceConnectionKey ?? ""
                  const state = connectionState(connection)
                  return (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          size="icon"
                          variant="ghost"
                          aria-label={`Actions for ${key}`}
                        >
                          <MoreHorizontal aria-hidden="true" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem asChild>
                          <Link
                            to="/admin/connectors/$sourceSystem/$connectionKey"
                            params={{
                              sourceSystem: group.system,
                              connectionKey: key,
                            }}
                          >
                            Open
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link
                            to="/admin/connectors/$sourceSystem"
                            params={{ sourceSystem: group.system }}
                            search={{ connection: key }}
                          >
                            Configure
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          disabled={check.isPending}
                          onSelect={() =>
                            check.mutate({
                              path: {
                                sourceSystem: group.system,
                                connectionKey: key,
                              },
                            })
                          }
                        >
                          Test credential
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          disabled={state.label !== "Indexing" || crawlNow.isPending}
                          onSelect={() =>
                            crawlNow.mutate({
                              path: {
                                sourceSystem: group.system,
                                connectionKey: key,
                              },
                            })
                          }
                        >
                          Crawl now
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          variant="destructive"
                          disabled={!connection.credentialSet}
                          onSelect={() =>
                            setForgetTarget({ system: group.system, key })
                          }
                        >
                          Forget credential
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          variant="destructive"
                          onSelect={() => setDeleteTarget({ system: group.system, key })}
                        >
                          Delete connection
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )
                },
              },
            ]
            return (
              <SourceGroup
                key={group.system}
                name={shown.name}
                icon={shown.icon}
                connections={group.rows.length}
                indexing={group.rows.filter((row) => connectionState(row).label === "Indexing").length}
                invalid={group.rows.filter((row) => connectionState(row).blocked).length}
              >
                <DataTable
                  columns={columns}
                  data={group.rows}
                  getRowId={(connection, index) =>
                    connection.sourceConnectionKey ?? String(index)
                  }
                />
              </SourceGroup>
            )
          })}
        </>
      )}

      <AlertDialog
        open={Boolean(forgetTarget)}
        onOpenChange={(open: boolean) => {
          if (!open) setForgetTarget(undefined)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Forget this credential?</AlertDialogTitle>
            <AlertDialogDescription>
              {forgetTarget?.key} can no longer authenticate once its credential is removed, and
              the credential cannot be shown again — a new one has to be entered to restore it. The
              connection and everything it has already crawled stay; only the ability to read more
              is withdrawn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={forget.isPending}>Keep it</AlertDialogCancel>
            <AlertDialogAction
              disabled={forget.isPending}
              onClick={(event: MouseEvent) => {
                // The dialog would otherwise close on click, before the request it started has
                // returned; it is dismissed in onSuccess instead, once the credential is gone.
                event.preventDefault()
                if (forgetTarget) {
                  forget.mutate({
                    path: { sourceSystem: forgetTarget.system, connectionKey: forgetTarget.key },
                  })
                }
              }}
            >
              {forget.isPending ? "Forgetting…" : "Forget credential"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open: boolean) => {
          if (!open && !remove.isPending) setDeleteTarget(undefined)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this connection?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget?.key} will stop crawling and its stored credential and configuration
              will be removed. Knowledge already retained from this source keeps following its
              Knowledge Space and the organization retention policy.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>Keep connection</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={remove.isPending}
              onClick={(event: MouseEvent) => {
                event.preventDefault()
                if (deleteTarget) {
                  remove.mutate({
                    path: {
                      sourceSystem: deleteTarget.system,
                      connectionKey: deleteTarget.key,
                    },
                  })
                }
              }}
            >
              {remove.isPending ? "Deleting…" : "Delete connection"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
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

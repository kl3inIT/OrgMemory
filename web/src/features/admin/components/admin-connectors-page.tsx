import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { Plus, TriangleAlert } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { formatTimestamp } from "@/features/admin/admin-labels"
import {
  adminConnectionsQueryOptions,
  invalidateAdminData,
  knowledgeSpacesQueryOptions,
} from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import { PROBE_REASONS, probeIsGood } from "@/features/admin/connector-probe"
import {
  forgetAdminConnectionCredentialMutation,
  testAdminConnectionMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminConnectionResponse, AdminConnectorProbeResponse } from "@/lib/hey-api"

/** The one source with an adapter today. The page is otherwise not Slack-shaped. */
const SLACK = "slack"

/**
 * Channels are Slack's word, and they live in the document only Slack understands. Reading
 * them here is a rendering convenience, so an unexpected shape degrades to "all visible"
 * rather than breaking the row.
 */
function channelsOf(connection: AdminConnectionResponse): string[] {
  const configured = connection.sourceConfig?.channels
  return Array.isArray(configured) ? configured.filter((name): name is string => typeof name === "string") : []
}

function probeReason(result: AdminConnectorProbeResponse) {
  if (result.errorCode && PROBE_REASONS[result.errorCode]) return PROBE_REASONS[result.errorCode]
  if (result.errorCode) return `Slack answered: ${result.errorCode}`
  return "The token authenticates and can list channels."
}

/**
 * What a connection is actually doing, which is not the same as what it was set to.
 *
 * <p>The state worth naming is the third one. A connection can be switched on, pointed at a
 * Space, and still read nothing, because nobody stored a token for it — and it reads as
 * healthy on a screen that only shows the switch. Onyx surfaces the same class of problem
 * with a standing banner on a connector in an invalid state rather than leaving it to be
 * discovered by whoever wonders why nothing was indexed.
 */
function connectionState(connection: AdminConnectionResponse) {
  if (!connection.crawlEnabled) {
    return { label: "Off", variant: "outline" as const, blocked: false }
  }
  if (!connection.credentialSet) {
    return { label: "Cannot crawl", variant: "warning" as const, blocked: true }
  }
  return { label: "Crawling", variant: "success" as const, blocked: false }
}

export function AdminConnectorsPage() {
  const queryClient = useQueryClient()
  const [checked, setChecked] = useState<{ key: string; result: AdminConnectorProbeResponse }>()

  const [connections, spaces] = useQueries({
    queries: [adminConnectionsQueryOptions(SLACK), knowledgeSpacesQueryOptions()],
  })

  const forget = useMutation({
    ...forgetAdminConnectionCredentialMutation(),
    onSuccess: async () => {
      await invalidateAdminData(queryClient)
      toast.success("Token forgotten. This connection can no longer authenticate.")
    },
    onError: () => toast.error("The token could not be removed."),
  })

  const check = useMutation({
    ...testAdminConnectionMutation(),
    onSuccess: (result, variables) => setChecked({ key: String(variables.path.connectionKey), result }),
    onError: () => toast.error("The stored token could not be checked."),
  })

  if (connections.isPending || spaces.isPending) {
    return <LoadingState label="Loading sources" className="min-h-full flex-1" />
  }

  const failed = [connections, spaces].find((query) => query.isError)
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Sources could not be loaded"
          description="Administration requires organization administrator permission."
          error={failed.error}
          onRetry={() => {
            void connections.refetch()
            void spaces.refetch()
          }}
        />
      </div>
    )
  }

  const connectionRows = connections.data ?? []
  const spaceRows = spaces.data ?? []
  const crawling = connectionRows.filter((connection) => connectionState(connection).label === "Crawling")
  const blocked = connectionRows.filter((connection) => connectionState(connection).blocked)

  return (
    <AdminPage
      title="Sources"
      description="Where this organization's evidence comes from. A credential entered here is encrypted before it is stored and is never shown again — not in a response, not masked, not to the administrator who set it."
      actions={
        <Button asChild>
          <Link to="/admin/connectors/new">
            <Plus aria-hidden="true" />
            Add a source
          </Link>
        </Button>
      }
    >
      <AdminStats
        stats={[
          { label: "Slack workspaces", value: connectionRows.length },
          { label: "Crawling", value: crawling.length },
          {
            label: "Cannot crawl",
            value: blocked.length,
            hint: blocked.length ? "Enabled with no token" : undefined,
          },
        ]}
      />

      {blocked.length > 0 ? (
        <Alert variant="destructive">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>
            {blocked.length === 1
              ? "One workspace is switched on but cannot read anything"
              : `${blocked.length} workspaces are switched on but cannot read anything`}
          </AlertTitle>
          <AlertDescription>
            {blocked.map((connection) => connection.sourceConnectionKey).join(", ")} — the crawl is
            enabled but no token is stored, so every poll ends without contacting Slack.
          </AlertDescription>
        </Alert>
      ) : null}

      <AdminSection
        title="Slack"
        description="A workspace crawls only once it has a token, a Space to publish into, and a user to publish as."
      >
        {connectionRows.length === 0 ? (
          <AdminEmpty
            title="No Slack workspace connected"
            description="Add a source to connect one."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Status</TableHead>
                <TableHead>Workspace</TableHead>
                <TableHead>Publishes into</TableHead>
                <TableHead>Channels</TableHead>
                <TableHead>Token</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {connectionRows.map((connection) => {
                const key = connection.sourceConnectionKey ?? ""
                const state = connectionState(connection)
                const space = spaceRows.find((candidate) => candidate.id === connection.knowledgeSpaceId)
                const result = checked?.key === key ? checked.result : undefined
                return (
                  <TableRow key={key}>
                    <TableCell>
                      <Badge variant={state.variant}>{state.label}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="min-w-40">
                        <div className="font-medium">{key}</div>
                        {result ? (
                          <div
                            className={
                              probeIsGood(result)
                                ? "mt-0.5 text-xs text-muted-foreground"
                                : "mt-0.5 text-xs text-destructive"
                            }
                          >
                            {probeReason(result)}
                          </div>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {space?.name ?? space?.key ?? "Not set"}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {channelsOf(connection).length ? channelsOf(connection).join(", ") : "All visible"}
                    </TableCell>
                    <TableCell>
                      {connection.credentialSet ? (
                        <div className="min-w-32">
                          <Badge variant="secondary">Stored</Badge>
                          <div className="mt-0.5 text-xs text-muted-foreground">
                            {formatTimestamp(connection.credentialSetAt)}
                          </div>
                        </div>
                      ) : (
                        <Badge variant="warning">None</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" asChild>
                          <Link to="/admin/connectors/slack" search={{ connection: key }}>
                            Configure
                          </Link>
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={check.isPending}
                          onClick={() => check.mutate({ path: { sourceSystem: SLACK, connectionKey: key } })}
                        >
                          Test
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={!connection.credentialSet || forget.isPending}
                          onClick={() => forget.mutate({ path: { sourceSystem: SLACK, connectionKey: key } })}
                        >
                          Forget token
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </AdminSection>
    </AdminPage>
  )
}

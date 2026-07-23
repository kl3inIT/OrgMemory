import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { CheckCircle2, TriangleAlert } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { formatTimestamp } from "@/features/admin/admin-labels"
import {
  adminSlackConnectionsQueryOptions,
  adminUsersQueryOptions,
  invalidateAdminData,
  knowledgeSpacesQueryOptions,
} from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import {
  ConfigureCrawlDialog,
  type CrawlSettings,
} from "@/features/admin/components/configure-crawl-dialog"
import {
  configureAdminSlackConnectionMutation,
  forgetAdminSlackCredentialMutation,
  setAdminSlackCredentialMutation,
  testAdminSlackConnectionMutation,
  testAdminSlackTokenMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminSlackConnectionResponse, AdminSlackProbeResponse } from "@/lib/hey-api"

/** What the Slack app has to be granted before any of this works. */
const REQUIRED_SCOPES = [
  "channels:read",
  "channels:history",
  "groups:read",
  "groups:history",
  "users:read",
  "users:read.email",
]

/** Slack's vocabulary, and the one code this application adds to it. */
const PROBE_REASONS: Record<string, string> = {
  invalid_auth: "Slack does not recognise this token.",
  not_authed: "The token is missing or expired.",
  token_revoked: "This token was revoked in Slack.",
  account_inactive: "The account this token belongs to is deactivated.",
  missing_scope: "The token authenticates but was not granted the scope to list channels.",
  no_credential: "No token is stored for this connection yet.",
  unreachable: "Slack could not be reached from this server.",
}

function probeReason(result: AdminSlackProbeResponse) {
  if (result.errorCode && PROBE_REASONS[result.errorCode]) return PROBE_REASONS[result.errorCode]
  if (result.errorCode) return `Slack answered: ${result.errorCode}`
  return "The token authenticates and can list channels."
}

function probeIsGood(result: AdminSlackProbeResponse) {
  return Boolean(result.authenticated && result.canListChannels)
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
function connectionState(connection: AdminSlackConnectionResponse) {
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
  const [botToken, setBotToken] = useState("")
  const [probe, setProbe] = useState<AdminSlackProbeResponse>()
  const [checked, setChecked] = useState<{ key: string; result: AdminSlackProbeResponse }>()
  const [configuring, setConfiguring] = useState<AdminSlackConnectionResponse>()

  const [connections, users, spaces] = useQueries({
    queries: [adminSlackConnectionsQueryOptions(), adminUsersQueryOptions(), knowledgeSpacesQueryOptions()],
  })

  async function refresh(message: string) {
    await invalidateAdminData(queryClient)
    toast.success(message)
  }

  const test = useMutation({
    ...testAdminSlackTokenMutation(),
    onSuccess: (result) => setProbe(result),
    onError: () => toast.error("The token could not be checked."),
  })

  const store = useMutation({
    ...setAdminSlackCredentialMutation(),
    onSuccess: async () => {
      setBotToken("")
      setProbe(undefined)
      await refresh("Token stored, encrypted. Set the crawl settings to start indexing.")
    },
    onError: () => toast.error("The token could not be stored."),
  })

  const forget = useMutation({
    ...forgetAdminSlackCredentialMutation(),
    onSuccess: () => refresh("Token forgotten. This connection can no longer authenticate."),
    onError: () => toast.error("The token could not be removed."),
  })

  const configure = useMutation({
    ...configureAdminSlackConnectionMutation(),
    onSuccess: async () => {
      setConfiguring(undefined)
      await refresh("Crawl settings saved. They take effect on the worker's next poll.")
    },
    onError: () => toast.error("The crawl settings could not be saved."),
  })

  const check = useMutation({
    ...testAdminSlackConnectionMutation(),
    onSuccess: (result, variables) =>
      setChecked({ key: String(variables.path.connectionKey), result }),
    onError: () => toast.error("The stored token could not be checked."),
  })

  if (connections.isPending || users.isPending || spaces.isPending) {
    return <LoadingState label="Loading connectors" className="min-h-full flex-1" />
  }

  const failed = [connections, users, spaces].find((query) => query.isError)
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Connectors could not be loaded"
          description="Administration requires organization administrator permission."
          error={failed.error}
          onRetry={() => {
            void connections.refetch()
            void users.refetch()
            void spaces.refetch()
          }}
        />
      </div>
    )
  }

  const connectionRows = connections.data ?? []
  const userRows = users.data ?? []
  const spaceRows = spaces.data ?? []
  const crawling = connectionRows.filter((connection) => connectionState(connection).label === "Crawling")
  const blocked = connectionRows.filter((connection) => connectionState(connection).blocked)

  return (
    <AdminPage
      title="Connectors"
      description="Slack workspaces this organization crawls. A token entered here is encrypted before it is stored and is never shown again — not in a response, not masked, not to the administrator who set it."
    >
      <AdminStats
        stats={[
          { label: "Workspaces", value: connectionRows.length },
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
            enabled but no token is stored, so every poll ends without contacting Slack. Save a bot
            token below.
          </AlertDescription>
        </Alert>
      ) : null}

      <AdminSection
        title="Connect a workspace"
        description="Start here. Checking a token reports which workspace it belongs to, and that workspace id becomes the connection key — so the token comes before the settings, not the other way round."
      >
        <div className="space-y-4 p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1 space-y-2">
              <Label htmlFor="slack-bot-token">Bot token</Label>
              <Input
                id="slack-bot-token"
                type="password"
                autoComplete="off"
                spellCheck={false}
                value={botToken}
                placeholder="xoxb-…"
                onChange={(event) => {
                  setBotToken(event.target.value)
                  setProbe(undefined)
                }}
              />
            </div>
            <Button
              variant="outline"
              disabled={!botToken.trim() || test.isPending}
              onClick={() => test.mutate({ body: { botToken: botToken.trim() } })}
            >
              {test.isPending ? "Checking…" : "Check token"}
            </Button>
          </div>

          <p className="text-xs text-muted-foreground">
            The Slack app needs{" "}
            {REQUIRED_SCOPES.map((scope, index) => (
              <span key={scope}>
                {index > 0 ? ", " : ""}
                <code className="rounded bg-muted px-1 py-0.5">{scope}</code>
              </span>
            ))}
            , and the bot has to be invited to each channel it should read. Checking the token here
            confirms both that it authenticates and that it was granted the scope to list channels —
            which authentication alone does not.
          </p>

          {probe ? (
            <Card className="py-0">
              <CardContent className="flex items-start gap-3 p-4">
                {probeIsGood(probe) ? (
                  <CheckCircle2
                    className="mt-0.5 size-4 shrink-0 text-status-success-content"
                    aria-hidden="true"
                  />
                ) : (
                  <TriangleAlert
                    className="mt-0.5 size-4 shrink-0 text-status-warning-content"
                    aria-hidden="true"
                  />
                )}
                <div className="min-w-0 flex-1 space-y-1">
                  <p className="text-sm font-medium">
                    {probe.authenticated
                      ? `${probe.workspaceName || "Workspace"} · ${probe.workspaceId}`
                      : "Slack refused this token"}
                  </p>
                  <p className="text-sm text-muted-foreground">{probeReason(probe)}</p>
                  {probe.authenticated && probe.botName ? (
                    <p className="text-xs text-muted-foreground">Authenticated as {probe.botName}.</p>
                  ) : null}
                </div>
                {probe.authenticated && probe.workspaceId ? (
                  <Button
                    disabled={store.isPending}
                    onClick={() =>
                      store.mutate({
                        path: { connectionKey: probe.workspaceId! },
                        body: { botToken: botToken.trim() },
                      })
                    }
                  >
                    {store.isPending ? "Saving…" : "Save token"}
                  </Button>
                ) : null}
              </CardContent>
            </Card>
          ) : null}
        </div>
      </AdminSection>

      <AdminSection
        title="Workspaces"
        description="Then configure each one. A workspace crawls only once it has a token, a Space to publish into, and a user to publish as."
      >
        {connectionRows.length === 0 ? (
          <AdminEmpty
            title="No Slack workspace connected"
            description="Check a bot token above to add one."
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
                      {connection.channels?.length ? connection.channels.join(", ") : "All visible"}
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
                        <Button size="sm" variant="outline" onClick={() => setConfiguring(connection)}>
                          Configure
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={check.isPending}
                          onClick={() => check.mutate({ path: { connectionKey: key } })}
                        >
                          Test
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={!connection.credentialSet || forget.isPending}
                          onClick={() => forget.mutate({ path: { connectionKey: key } })}
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

      <ConfigureCrawlDialog
        connection={configuring}
        spaces={spaceRows}
        users={userRows}
        pending={configure.isPending}
        onOpenChange={(open) => !open && setConfiguring(undefined)}
        onSave={(settings: CrawlSettings) =>
          configuring?.sourceConnectionKey &&
          configure.mutate({
            path: { connectionKey: configuring.sourceConnectionKey },
            body: settings,
          })
        }
      />
    </AdminPage>
  )
}

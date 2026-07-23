import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { ArrowLeft, ArrowRight, Check, CheckCircle2, TriangleAlert } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { formatTimestamp } from "@/features/admin/admin-labels"
import {
  adminConnectionsQueryOptions,
  adminUsersQueryOptions,
  invalidateAdminData,
  knowledgeSpacesQueryOptions,
} from "@/features/admin/admin-queries"
import { AdminPage } from "@/features/admin/components/admin-page"
import { PROBE_REASONS, probeIsGood } from "@/features/admin/connector-probe"
import {
  configureAdminConnectionMutation,
  setAdminConnectionCredentialMutation,
  testAdminConnectorCredentialMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminConnectorProbeResponse } from "@/lib/hey-api"

/** The source system this wizard configures. */
const SLACK = "slack"

/** What the Slack app has to be granted before any of this works. */
const REQUIRED_SCOPES = [
  "channels:read",
  "channels:history",
  "groups:read",
  "groups:history",
  "users:read",
  "users:read.email",
]

const STEPS = [
  { key: "token", label: "Token", hint: "Which workspace" },
  { key: "destination", label: "Destination", hint: "Where it lands" },
  { key: "scope", label: "Scope", hint: "How much it reads" },
] as const

type StepKey = (typeof STEPS)[number]["key"]

const DEFAULT_INTERVAL_MINUTES = 60
const DEFAULT_MAX_THREADS = 500

function probeReason(result: AdminConnectorProbeResponse) {
  if (result.errorCode && PROBE_REASONS[result.errorCode]) return PROBE_REASONS[result.errorCode]
  if (result.errorCode) return `Slack answered: ${result.errorCode}`
  return "The token authenticates and can list channels."
}

/**
 * Configuring one workspace, in the order the work actually has to happen.
 *
 * <p>The token comes first because it is what reports the workspace id, and the workspace id
 * is the connection key — there is nothing to configure until that is known. After that the
 * two halves Slack has no opinion about: where crawled content lands, and how much of the
 * workspace to read.
 *
 * <p>Saving is available from the second step rather than only the last, which is Onyx's
 * arrangement and the right one: the scope step has working defaults, so making somebody walk
 * through it to finish would be ceremony rather than a decision.
 */
export function SlackConnectionWizard({ connectionKey }: { connectionKey?: string }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [step, setStep] = useState<StepKey>(connectionKey ? "destination" : "token")
  const [savedKey, setSavedKey] = useState<string | undefined>(connectionKey)
  const [botToken, setBotToken] = useState("")
  const [probe, setProbe] = useState<AdminConnectorProbeResponse>()

  const [crawlEnabled, setCrawlEnabled] = useState(true)
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState<string>()
  const [actorUserId, setActorUserId] = useState<string>()
  const [channels, setChannels] = useState("")
  const [intervalMinutes, setIntervalMinutes] = useState(String(DEFAULT_INTERVAL_MINUTES))
  const [maxThreads, setMaxThreads] = useState(String(DEFAULT_MAX_THREADS))
  const [loadedFrom, setLoadedFrom] = useState<string>()

  const [connections, users, spaces] = useQueries({
    queries: [adminConnectionsQueryOptions(SLACK), adminUsersQueryOptions(), knowledgeSpacesQueryOptions()],
  })

  const existing = (connections.data ?? []).find(
    (candidate) => candidate.sourceConnectionKey === savedKey,
  )

  // The form starts from what the connection already says, once that has arrived. Keyed on
  // the connection so reopening on a different one does not show the last one's settings.
  if (existing && loadedFrom !== existing.sourceConnectionKey) {
    setLoadedFrom(existing.sourceConnectionKey)
    setCrawlEnabled(existing.crawlEnabled ?? true)
    setKnowledgeSpaceId(existing.knowledgeSpaceId)
    setActorUserId(existing.actorUserId)
    const configured = existing.sourceConfig ?? {}
    const channelNames = Array.isArray(configured.channels)
      ? configured.channels.filter((name): name is string => typeof name === "string")
      : []
    setChannels(channelNames.join(", "))
    setIntervalMinutes(
      String(Math.max(1, Math.round((existing.contentCrawlIntervalSeconds ?? 3600) / 60))),
    )
    setMaxThreads(
      String(typeof configured.maxThreadsPerChannel === "number"
        ? configured.maxThreadsPerChannel
        : DEFAULT_MAX_THREADS),
    )
  }

  const test = useMutation({
    ...testAdminConnectorCredentialMutation(),
    onSuccess: (result) => setProbe(result),
    onError: () => toast.error("The token could not be checked."),
  })

  const store = useMutation({
    ...setAdminConnectionCredentialMutation(),
    onSuccess: async (_result, variables) => {
      setSavedKey(String(variables.path.connectionKey))
      setBotToken("")
      setProbe(undefined)
      await invalidateAdminData(queryClient)
      setStep("destination")
      toast.success("Token stored, encrypted. It is never shown again.")
    },
    onError: () => toast.error("The token could not be stored."),
  })

  const configure = useMutation({
    ...configureAdminConnectionMutation(),
    onSuccess: async () => {
      await invalidateAdminData(queryClient)
      toast.success("Saved. The worker picks this up on its next poll.")
      void navigate({ to: "/admin/connectors" })
    },
    onError: () => toast.error("The settings could not be saved."),
  })

  if (connections.isPending || users.isPending || spaces.isPending) {
    return <LoadingState label="Loading connection" className="min-h-full flex-1" />
  }

  const failed = [connections, users, spaces].find((query) => query.isError)
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="This connection could not be loaded"
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

  const userRows = users.data ?? []
  const spaceRows = spaces.data ?? []
  const parsedInterval = Number.parseInt(intervalMinutes, 10)
  const parsedMaxThreads = Number.parseInt(maxThreads, 10)
  const boundsValid = parsedInterval > 0 && parsedMaxThreads > 0
  const targetsChosen = Boolean(knowledgeSpaceId) && Boolean(actorUserId)
  const canSave = Boolean(savedKey) && boundsValid && (!crawlEnabled || targetsChosen)
  const stepIndex = STEPS.findIndex((candidate) => candidate.key === step)

  function save() {
    if (!savedKey) return
    configure.mutate({
      path: { sourceSystem: SLACK, connectionKey: savedKey },
      body: {
        crawlEnabled,
        knowledgeSpaceId,
        actorUserId,
        // Everything only Slack understands goes in one document the ledger stores unread.
        sourceConfig: {
          channels: channels
            .split(",")
            .map((channel) => channel.trim().replace(/^#/, ""))
            .filter(Boolean),
          maxThreadsPerChannel: parsedMaxThreads,
        },
        contentCrawlIntervalSeconds: parsedInterval * 60,
      },
    })
  }

  return (
    <AdminPage
      title={connectionKey ? `Slack · ${connectionKey}` : "Connect Slack"}
      description="A workspace crawls only once it has a token, a Space to publish into, and a user to publish as."
      actions={
        <Button variant="outline" asChild>
          <Link to="/admin/connectors">
            <ArrowLeft aria-hidden="true" />
            Configured sources
          </Link>
        </Button>
      }
    >
      <ol className="flex flex-wrap gap-2">
        {STEPS.map((candidate, index) => {
          const done = index < stepIndex
          const current = candidate.key === step
          return (
            <li key={candidate.key} className="flex-1 basis-52">
              <button
                type="button"
                disabled={index > 0 && !savedKey}
                onClick={() => setStep(candidate.key)}
                data-state={current ? "current" : done ? "done" : "upcoming"}
                className="w-full rounded-lg border border-border-subtle px-3 py-2 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 data-[state=current]:border-action-primary data-[state=current]:bg-surface-raised"
              >
                <div className="flex items-center gap-2 text-sm font-medium">
                  <span className="grid size-5 shrink-0 place-items-center rounded-full border text-xs tabular-nums">
                    {done ? <Check className="size-3" aria-hidden="true" /> : index + 1}
                  </span>
                  {candidate.label}
                </div>
                <div className="mt-0.5 pl-7 text-xs text-muted-foreground">{candidate.hint}</div>
              </button>
            </li>
          )
        })}
      </ol>

      <Card className="py-0">
        <CardContent className="space-y-4 p-5">
          {step === "token" ? (
            <TokenStep
              existingSetAt={existing?.credentialSet ? existing.credentialSetAt : undefined}
              botToken={botToken}
              probe={probe}
              checking={test.isPending}
              storing={store.isPending}
              onTokenChange={(value) => {
                setBotToken(value)
                setProbe(undefined)
              }}
              onCheck={() =>
                test.mutate({ path: { sourceSystem: SLACK }, body: { botToken: botToken.trim() } })
              }
              onStore={(key) =>
                store.mutate({
                  path: { sourceSystem: SLACK, connectionKey: key },
                  body: { botToken: botToken.trim() },
                })
              }
            />
          ) : null}

          {step === "destination" ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="crawl-space">Knowledge Space</Label>
                <Select value={knowledgeSpaceId} onValueChange={setKnowledgeSpaceId}>
                  <SelectTrigger id="crawl-space" className="w-full">
                    <SelectValue placeholder="Select a Space to publish into" />
                  </SelectTrigger>
                  <SelectContent>
                    {spaceRows
                      .filter((space) => space.id)
                      .map((space) => (
                        <SelectItem key={space.id} value={space.id!}>
                          {space.name ?? space.key}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="crawl-actor">Recorded as</Label>
                <Select value={actorUserId} onValueChange={setActorUserId}>
                  <SelectTrigger id="crawl-actor" className="w-full">
                    <SelectValue placeholder="Select the user the crawl publishes as" />
                  </SelectTrigger>
                  <SelectContent>
                    {userRows
                      .filter((user) => user.active && user.id)
                      .map((user) => (
                        <SelectItem key={user.id} value={user.id!}>
                          {user.name ?? user.email} · {user.email}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  Every crawled object is attributed to this user in the audit trail. Access still
                  comes from Slack channel membership, not from this choice.
                </p>
              </div>

              <div className="flex items-center justify-between gap-4 rounded-md border p-3">
                <div className="space-y-0.5">
                  <Label htmlFor="crawl-enabled">Crawl this workspace</Label>
                  <p className="text-xs text-muted-foreground">
                    Nothing is read from Slack until this is on.
                  </p>
                </div>
                <Switch id="crawl-enabled" checked={crawlEnabled} onCheckedChange={setCrawlEnabled} />
              </div>

              {crawlEnabled && !targetsChosen ? (
                <p className="text-sm text-destructive">
                  A crawl needs a Knowledge Space to publish into and a user to publish as.
                </p>
              ) : null}
            </div>
          ) : null}

          {step === "scope" ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="crawl-channels">Channels</Label>
                <Input
                  id="crawl-channels"
                  value={channels}
                  placeholder="general, engineering"
                  onChange={(event) => setChannels(event.target.value)}
                />
                <p className="text-xs text-muted-foreground">
                  Leave empty to crawl every channel the bot can see. A filter also stops the crawl
                  claiming it enumerated the workspace, so nothing is retired on its word.
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="crawl-interval">Content interval (minutes)</Label>
                  <Input
                    id="crawl-interval"
                    inputMode="numeric"
                    value={intervalMinutes}
                    onChange={(event) => setIntervalMinutes(event.target.value)}
                  />
                  <p className="text-xs text-muted-foreground">
                    Between these, only access is re-read — a call per channel rather than per thread.
                  </p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="crawl-threads">Threads per channel</Label>
                  <Input
                    id="crawl-threads"
                    inputMode="numeric"
                    value={maxThreads}
                    onChange={(event) => setMaxThreads(event.target.value)}
                  />
                  <p className="text-xs text-muted-foreground">
                    A bound on one crawl. Hitting it withdraws the completeness claim.
                  </p>
                </div>
              </div>

              {!boundsValid ? (
                <p className="text-sm text-destructive">Both bounds must be positive numbers.</p>
              ) : null}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <div className="grid grid-cols-3 items-center gap-2">
        <div>
          {stepIndex > 0 ? (
            <Button variant="outline" onClick={() => setStep(STEPS[stepIndex - 1]!.key)}>
              <ArrowLeft aria-hidden="true" />
              Back
            </Button>
          ) : null}
        </div>
        <div className="flex justify-center">
          {stepIndex > 0 ? (
            <Button disabled={!canSave || configure.isPending} onClick={save}>
              {configure.isPending ? "Saving…" : "Save connection"}
            </Button>
          ) : null}
        </div>
        <div className="flex justify-end">
          {stepIndex < STEPS.length - 1 ? (
            <Button
              variant="outline"
              disabled={!savedKey}
              onClick={() => setStep(STEPS[stepIndex + 1]!.key)}
            >
              {STEPS[stepIndex + 1]!.label}
              <ArrowRight aria-hidden="true" />
            </Button>
          ) : null}
        </div>
      </div>
    </AdminPage>
  )
}

function TokenStep({
  existingSetAt,
  botToken,
  probe,
  checking,
  storing,
  onTokenChange,
  onCheck,
  onStore,
}: {
  existingSetAt?: string
  botToken: string
  probe?: AdminConnectorProbeResponse
  checking: boolean
  storing: boolean
  onTokenChange: (value: string) => void
  onCheck: () => void
  onStore: (connectionKey: string) => void
}) {
  return (
    <div className="space-y-4">
      {existingSetAt ? (
        <div className="flex items-center gap-3 rounded-md border p-3">
          <Badge variant="secondary">Token stored</Badge>
          <span className="text-sm text-muted-foreground">
            Set {formatTimestamp(existingSetAt)}. Entering a new one below replaces it.
          </span>
        </div>
      ) : null}

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
            onChange={(event) => onTokenChange(event.target.value)}
          />
        </div>
        <Button variant="outline" disabled={!botToken.trim() || checking} onClick={onCheck}>
          {checking ? "Checking…" : "Check token"}
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
        , and the bot has to be invited to each channel it should read. Checking here confirms both
        that the token authenticates and that it was granted the scope to list channels — which
        authentication alone does not.
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
              <Button disabled={storing} onClick={() => onStore(probe.workspaceId!)}>
                {storing ? "Saving…" : "Save token"}
                <ArrowRight aria-hidden="true" />
              </Button>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

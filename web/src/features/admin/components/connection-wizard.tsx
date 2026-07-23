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
import { Textarea } from "@/components/ui/textarea"
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
import { ConnectorFields } from "@/features/admin/components/connector-fields"
import { SourceIcon, type SourceIconName } from "@/features/admin/components/source-icon"
import { CONNECTOR_CATALOG, type ConnectorCredentialDescriptor } from "@/features/admin/connector-catalog"
import {
  CONNECTOR_FORMS,
  configFrom,
  draftFrom,
  invalidFields,
  type ConnectorFieldDraft,
} from "@/features/admin/connector-forms"
import { probeIsGood, probeReason } from "@/features/admin/connector-probe"
import {
  configureAdminConnectionMutation,
  setAdminConnectionCredentialMutation,
  testAdminConnectorCredentialMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminConnectorProbeResponse } from "@/lib/hey-api"

const STEPS = [
  { key: "credential", label: "Credential", hint: "Which account" },
  { key: "destination", label: "Destination", hint: "Where it lands" },
  { key: "scope", label: "Scope", hint: "How much it reads" },
] as const

type StepKey = (typeof STEPS)[number]["key"]

const DEFAULT_INTERVAL_MINUTES = 60

/**
 * Configuring one connection, in the order the work actually has to happen.
 *
 * <p>The credential comes first because it is what reports the connection key — a Slack
 * workspace id, a Google Workspace domain — and there is nothing to configure until that is
 * known. After that come the two halves the source has no opinion about: where crawled content
 * lands, and how much to read.
 *
 * <p>Nothing here names a source. The credential step reads its label, its placeholder and what
 * has to be granted from the catalogue; the scope step reads its fields from the descriptor;
 * the destination step is true of every source. That is the whole claim of the last two
 * increments, and this component is where it either holds or does not.
 *
 * <p>Saving is available from the second step rather than only the last, which is Onyx's
 * arrangement and the right one: the scope step has working defaults, so walking through it to
 * finish would be ceremony rather than a decision.
 */
export function ConnectionWizard({
  sourceSystem,
  connectionKey,
}: {
  sourceSystem: string
  connectionKey?: string
}) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const catalogued = CONNECTOR_CATALOG.find((entry) => entry.sourceSystem === sourceSystem)
  const credentialDescriptor = catalogued?.credential
  const form = CONNECTOR_FORMS[sourceSystem] ?? { fields: [], advanced: [] }

  const [step, setStep] = useState<StepKey>(connectionKey ? "destination" : "credential")
  const [savedKey, setSavedKey] = useState<string | undefined>(connectionKey)
  const [credential, setCredential] = useState("")
  const [probe, setProbe] = useState<AdminConnectorProbeResponse>()

  const [crawlEnabled, setCrawlEnabled] = useState(true)
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState<string>()
  const [actorUserId, setActorUserId] = useState<string>()
  const [intervalMinutes, setIntervalMinutes] = useState(String(DEFAULT_INTERVAL_MINUTES))
  const [draft, setDraft] = useState<ConnectorFieldDraft>(() => draftFrom(form, {}))
  const [loadedFrom, setLoadedFrom] = useState<string>()

  const [connections, users, spaces] = useQueries({
    queries: [
      adminConnectionsQueryOptions(sourceSystem),
      adminUsersQueryOptions(),
      knowledgeSpacesQueryOptions(),
    ],
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
    setDraft(draftFrom(form, existing.sourceConfig ?? {}))
    setIntervalMinutes(
      String(Math.max(1, Math.round((existing.contentCrawlIntervalSeconds ?? 3600) / 60))),
    )
  }

  const test = useMutation({
    ...testAdminConnectorCredentialMutation(),
    onSuccess: (result) => setProbe(result),
    onError: () => toast.error("The credential could not be checked."),
  })

  const store = useMutation({
    ...setAdminConnectionCredentialMutation(),
    onSuccess: async (_result, variables) => {
      setSavedKey(String(variables.path.connectionKey))
      setCredential("")
      setProbe(undefined)
      await invalidateAdminData(queryClient)
      setStep("destination")
      toast.success("Credential stored, encrypted. It is never shown again.")
    },
    onError: () => toast.error("The credential could not be stored."),
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
          description="This deployment may have no adapter for this source, or administration may require organization administrator permission."
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

  const sourceName = catalogued?.name ?? sourceSystem
  const userRows = users.data ?? []
  const spaceRows = spaces.data ?? []
  const parsedInterval = Number.parseInt(intervalMinutes, 10)
  const invalid = invalidFields(form, draft)
  const boundsValid = parsedInterval > 0 && invalid.length === 0
  const targetsChosen = Boolean(knowledgeSpaceId) && Boolean(actorUserId)
  const canSave = Boolean(savedKey) && boundsValid && (!crawlEnabled || targetsChosen)
  const stepIndex = STEPS.findIndex((candidate) => candidate.key === step)

  function save() {
    if (!savedKey) return
    configure.mutate({
      path: { sourceSystem, connectionKey: savedKey },
      body: {
        crawlEnabled,
        knowledgeSpaceId,
        actorUserId,
        // Everything only this source understands goes in one document the ledger stores unread.
        sourceConfig: configFrom(form, draft),
        contentCrawlIntervalSeconds: parsedInterval * 60,
      },
    })
  }

  return (
    <AdminPage
      title={connectionKey ? `${sourceName} · ${connectionKey}` : `Connect ${sourceName}`}
      icon={
        catalogued ? <SourceIcon name={catalogued.icon as SourceIconName} className="size-6" /> : undefined
      }
      actions={
        <Button variant="outline" asChild>
          <Link to="/admin/connectors">
            <ArrowLeft aria-hidden="true" />
            Sources
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
          {step === "credential" ? (
            <CredentialStep
              sourceSystem={sourceSystem}
              descriptor={credentialDescriptor}
              existingSetAt={existing?.credentialSet ? existing.credentialSetAt : undefined}
              credential={credential}
              probe={probe}
              checking={test.isPending}
              storing={store.isPending}
              onCredentialChange={(value) => {
                setCredential(value)
                setProbe(undefined)
              }}
              onCheck={() =>
                test.mutate({ path: { sourceSystem }, body: { credential: credential.trim() } })
              }
              onStore={(key) =>
                store.mutate({
                  path: { sourceSystem, connectionKey: key },
                  body: { credential: credential.trim() },
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
                  comes from what {sourceName} says, not from this choice.
                </p>
              </div>

              <div className="flex items-center justify-between gap-4 rounded-md border p-3">
                <div className="space-y-0.5">
                  <Label htmlFor="crawl-enabled">Index this connection</Label>
                  <p className="text-xs text-muted-foreground">
                    Nothing is read from {sourceName} until this is on.
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
            <ConnectorFields
              descriptor={form}
              draft={draft}
              invalid={invalid}
              onChange={(name, value) => setDraft((current) => ({ ...current, [name]: value }))}
            >
              <div className="space-y-2">
                <Label htmlFor="crawl-interval">Content interval (minutes)</Label>
                <Input
                  id="crawl-interval"
                  inputMode="numeric"
                  value={intervalMinutes}
                  aria-invalid={parsedInterval > 0 ? undefined : true}
                  onChange={(event) => setIntervalMinutes(event.target.value)}
                />
                <p className="text-xs text-muted-foreground">
                  Between these, only access is re-read, which is far cheaper than re-reading
                  content. This setting is not the source's: every source is indexed on an
                  interval, and it is a column with a constraint rather than part of the source's
                  own document.
                </p>
                {parsedInterval > 0 ? null : (
                  <p className="text-sm text-destructive">
                    The interval must be a whole number of minutes, at least one.
                  </p>
                )}
              </div>
            </ConnectorFields>
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

function CredentialStep({
  sourceSystem,
  descriptor,
  existingSetAt,
  credential,
  probe,
  checking,
  storing,
  onCredentialChange,
  onCheck,
  onStore,
}: {
  sourceSystem: string
  descriptor?: ConnectorCredentialDescriptor
  existingSetAt?: string
  credential: string
  probe?: AdminConnectorProbeResponse
  checking: boolean
  storing: boolean
  onCredentialChange: (value: string) => void
  onCheck: () => void
  onStore: (connectionKey: string) => void
}) {
  if (!descriptor) {
    return (
      <p className="text-sm text-muted-foreground">
        This source does not take a credential here.
      </p>
    )
  }

  const id = "connector-credential"
  return (
    <div className="space-y-4">
      {existingSetAt ? (
        <div className="flex items-center gap-3 rounded-md border p-3">
          <Badge variant="secondary">Stored</Badge>
          <span className="text-sm text-muted-foreground">
            Set {formatTimestamp(existingSetAt)}. Entering a new one below replaces it.
          </span>
        </div>
      ) : null}

      <div
        className={
          descriptor.multiline ? "space-y-3" : "flex flex-col gap-3 sm:flex-row sm:items-end"
        }
      >
        <div className="flex-1 space-y-2">
          <Label htmlFor={id}>{descriptor.label}</Label>
          {descriptor.multiline ? (
            <Textarea
              id={id}
              rows={8}
              spellCheck={false}
              className="font-mono text-xs"
              value={credential}
              placeholder={descriptor.placeholder}
              onChange={(event) => onCredentialChange(event.target.value)}
            />
          ) : (
            <Input
              id={id}
              type="password"
              autoComplete="off"
              spellCheck={false}
              value={credential}
              placeholder={descriptor.placeholder}
              onChange={(event) => onCredentialChange(event.target.value)}
            />
          )}
        </div>
        <Button variant="outline" disabled={!credential.trim() || checking} onClick={onCheck}>
          {checking ? "Checking…" : "Check"}
        </Button>
      </div>

      <p className="text-xs text-muted-foreground">
        What you paste here is encrypted before it is stored and is never shown again — not in a
        response, not masked, not to you.
      </p>

      {descriptor.requirements?.length ? (
        <p className="text-xs text-muted-foreground">
          It has to have been granted{" "}
          {descriptor.requirements.map((requirement, index) => (
            <span key={requirement}>
              {index > 0 ? ", " : ""}
              <code className="rounded bg-muted px-1 py-0.5">{requirement}</code>
            </span>
          ))}
          . {descriptor.note} Checking here confirms both that it authenticates and that it can
          actually read something — which authentication alone does not.
        </p>
      ) : null}

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
                  ? `${probe.accountName || descriptor.keyName} · ${probe.connectionKey}`
                  : "The credential was refused"}
              </p>
              <p className="text-sm text-muted-foreground">{probeReason(sourceSystem, probe)}</p>
              {probe.authenticated && probe.identityName ? (
                <p className="text-xs text-muted-foreground">
                  Authenticated as {probe.identityName}.
                </p>
              ) : null}
            </div>
            {probe.authenticated && probe.connectionKey ? (
              <Button disabled={storing} onClick={() => onStore(probe.connectionKey!)}>
                {storing ? "Saving…" : "Save credential"}
                <ArrowRight aria-hidden="true" />
              </Button>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

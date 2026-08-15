import { useMutation, useQueries } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { ArrowLeft, History, RefreshCw, Settings2, ShieldCheck, TriangleAlert } from "lucide-react"
import { toast } from "sonner"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import {
  adminConnectionActivityQueryOptions,
  adminQuery,
} from "@/features/admin/admin-queries"
import {
  listAdminConnectionsOptions,
  listKnowledgeSpaceUploadTargetsOptions,
  requestAdminConnectionCrawlMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import { SourceIcon, type SourceIconName } from "@/features/admin/components/source-icon"
import { CONNECTOR_CATALOG } from "@/features/admin/connector-catalog"
import { allFields, CONNECTOR_FORMS, type ConnectorField } from "@/features/admin/connector-forms"
import type { AdminComponentCheckpointResponse, AdminCrawlAttemptResponse } from "@/lib/hey-api"
import { formatBytes, formatDate } from "@/lib/format"

/**
 * How each outcome reads on the screen.
 *
 * <p>The five are kept apart because they call for different actions. A rejected batch is
 * gone and will not come back; a failed one is still queued; an unavailable connection never
 * produced a batch at all, which is what a revoked credential looks like and is the only one
 * where the fix is a token rather than patience.
 */
const OUTCOMES: Record<string, { label: string; variant: "success" | "warning" | "muted"; hint: string }> = {
  SUCCEEDED: {
    label: "Reconciled",
    variant: "success",
    hint: "The batch was ingested.",
  },
  PARTIAL: {
    label: "Partial",
    variant: "warning",
    hint: "Some components advanced; failed items remain queued.",
  },
  REJECTED: {
    label: "Rejected",
    variant: "warning",
    hint: "Refused for a reason retrying cannot change, and skipped past. Whatever it held is not coming back on its own.",
  },
  FAILED: {
    label: "Failed",
    variant: "warning",
    hint: "Left for the next poll, which will try it again.",
  },
  UNAVAILABLE: {
    label: "Could not read",
    variant: "warning",
    hint: "No batch was produced at all. This is what a revoked or missing credential looks like.",
  },
}

const INCOMPLETE_REASONS: Record<string, { title: string; detail: string }> = {
  GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED: {
    title: "Content limit reached",
    detail:
      "The crawl stopped retaining text at the configured limit, then continued checking file permissions.",
  },
}

function incompleteReasonPresentation(reason?: string) {
  if (!reason) return undefined
  return (
    INCOMPLETE_REASONS[reason] ?? {
      title: "Incomplete source evidence",
      detail: "The source did not prove this component complete in its latest observation.",
    }
  )
}

export function changedBy(attempt: AdminCrawlAttemptResponse): string {
  const parts = [
    attempt.objectsMaterialized ? `${attempt.objectsMaterialized} added` : "",
    attempt.objectsRematerialized ? `${attempt.objectsRematerialized} content updated` : "",
    attempt.objectsRotated ? `${attempt.objectsRotated} permissions updated` : "",
    attempt.objectsRetired ? `${attempt.objectsRetired} retired` : "",
    attempt.objectsFailed ? `${attempt.objectsFailed} failed` : "",
  ].filter(Boolean)
  return parts.length ? parts.join(" · ") : "Nothing changed"
}

const attemptColumns: ColumnDef<AdminCrawlAttemptResponse>[] = [
  {
    accessorKey: "attemptedAt",
    header: "When",
    enableSorting: true,
    meta: { cellClassName: "whitespace-nowrap text-muted-foreground" },
    cell: ({ row }) => formatDate(row.original.attemptedAt),
  },
  {
    accessorKey: "outcome",
    header: "Outcome",
    enableSorting: true,
    cell: ({ row }) => {
      const outcome = OUTCOMES[row.original.outcome ?? ""]
      return (
        <Badge variant={outcome?.variant ?? "muted"}>
          {outcome?.label ?? row.original.outcome}
        </Badge>
      )
    },
  },
  {
    id: "changed",
    accessorFn: changedBy,
    header: "Changes",
    enableSorting: true,
    meta: { cellClassName: "text-muted-foreground" },
  },
  {
    id: "reason",
    accessorFn: (attempt) =>
      [attempt.errorCode, attempt.errorMessage].filter(Boolean).join(" "),
    header: "Details",
    meta: { cellClassName: "max-w-md text-muted-foreground" },
    cell: ({ row }) => {
      const attempt = row.original
      const reason = incompleteReasonPresentation(attempt.errorCode)
      if (!attempt.errorCode && !attempt.errorMessage) return "—"
      return (
        <div className="space-y-1">
          <p className="font-medium text-foreground">
            {reason?.title ?? attempt.errorMessage}
          </p>
          {reason && attempt.errorMessage ? (
            <p className="text-xs">{attempt.errorMessage}</p>
          ) : null}
          {attempt.errorCode ? (
            <code className="block break-all text-xs text-muted-foreground">
              {attempt.errorCode}
            </code>
          ) : null}
        </div>
      )
    },
  },
]

const COMPONENT_LABELS: Record<string, string> = {
  CONTENT: "Content",
  PERMISSION: "Permissions",
  MEMBERSHIP: "Membership",
}

function ComponentCheckpoint({ checkpoint }: { checkpoint: AdminComponentCheckpointResponse }) {
  const complete = checkpoint.captureStatus === "COMPLETE"
  const reason = incompleteReasonPresentation(checkpoint.incompleteReason)
  return (
    <div className="min-w-0 bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <dt className="font-medium">
          {COMPONENT_LABELS[checkpoint.component ?? ""] ?? checkpoint.component ?? "Component"}
        </dt>
        <Badge variant={complete ? "success" : "warning"}>
          {complete ? "Complete" : "Incomplete"}
        </Badge>
      </div>
      <dd className="mt-3 space-y-2 text-sm text-muted-foreground">
        <p>
          Last complete {formatDate(checkpoint.lastSuccessfulAt, { fallback: "Never" })}
        </p>
        {reason ? (
          <div className="space-y-1">
            <p className="font-medium text-status-warning-content">{reason.title}</p>
            <p>{reason.detail}</p>
            <code className="block break-all text-xs">{checkpoint.incompleteReason}</code>
          </div>
        ) : null}
      </dd>
    </div>
  )
}

/**
 * One connection, and what it has actually done.
 *
 * <p>Configuration and activity are shown together because neither is legible alone. A
 * connection that is enabled, holds a credential and points at a Space still reads as healthy
 * while producing nothing; the attempts are where that shows.
 */
export function ConnectionDetailPage({
  sourceSystem,
  connectionKey,
}: {
  sourceSystem: string
  connectionKey: string
}) {
  const [connections, activity, spaces] = useQueries({
    queries: [
      adminQuery(listAdminConnectionsOptions({ path: { sourceSystem } })),
      adminConnectionActivityQueryOptions(sourceSystem, connectionKey),
      adminQuery(listKnowledgeSpaceUploadTargetsOptions()),
    ],
  })

  const crawlNow = useMutation({
    ...requestAdminConnectionCrawlMutation(),
    onSuccess: () =>
      toast.success(
        "Crawl requested. The worker reads its content on the next poll — the attempt appears below when it does.",
      ),
    onError: () => toast.error("The crawl could not be requested."),
  })

  if (connections.isPending || activity.isPending || spaces.isPending) {
    return <LoadingState label="Loading connection" className="min-h-full flex-1" />
  }

  const failed = [connections, activity, spaces].find((query) => query.isError)
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="This connection could not be loaded"
          description="Administration requires organization administrator permission."
          error={failed.error}
          onRetry={() => {
            void connections.refetch()
            void activity.refetch()
            void spaces.refetch()
          }}
        />
      </div>
    )
  }

  const catalogued = CONNECTOR_CATALOG.find((entry) => entry.sourceSystem === sourceSystem)
  const sourceName = catalogued?.name ?? sourceSystem
  const connection = (connections.data ?? []).find(
    (candidate) => candidate.sourceConnectionKey === connectionKey,
  )

  if (!connection) {
    return (
      <AdminPage
        title="Connection not found"
        description={`${sourceName} connection ${connectionKey}`}
        actions={
          <Button variant="outline" asChild>
            <Link to="/admin/connectors">
              <ArrowLeft aria-hidden="true" />
              All sources
            </Link>
          </Button>
        }
      >
        <AdminEmpty
          title="This connection no longer exists"
          description="Return to Sources to choose a configured connection."
        />
      </AdminPage>
    )
  }

  const attempts = activity.data?.recentAttempts ?? []
  const componentCheckpoints = activity.data?.componentCheckpoints ?? []
  const space = (spaces.data ?? []).find((candidate) => candidate.id === connection.knowledgeSpaceId)
  const descriptor = CONNECTOR_FORMS[sourceSystem]
  const blocked = connection.crawlEnabled && !connection.credentialSet
  const lastAttempt = attempts[0]
  const stalled = lastAttempt && lastAttempt.outcome !== "SUCCEEDED"
  const latestIssue = lastAttempt?.errorCode
    ? INCOMPLETE_REASONS[lastAttempt.errorCode]
    : undefined
  const partial = lastAttempt?.outcome === "PARTIAL"
  let latestCrawlTitle = latestIssue?.title ?? "Latest crawl did not complete"
  if (!latestIssue && lastAttempt?.outcome === "UNAVAILABLE") {
    latestCrawlTitle = `${sourceName} could not be read`
  } else if (!latestIssue && lastAttempt?.outcome === "PARTIAL") {
    latestCrawlTitle = "Latest crawl completed with partial evidence"
  }
  let crawlDisabledReason: string | undefined
  if (!connection.crawlEnabled) {
    crawlDisabledReason = "Enable this connection before requesting a crawl."
  } else if (!connection.credentialSet) {
    crawlDisabledReason = "Store a credential before requesting a crawl."
  }

  return (
    <AdminPage
      title={sourceName}
      description={`Connection ${connectionKey}`}
      icon={
        catalogued ? <SourceIcon name={catalogued.icon as SourceIconName} className="size-6" /> : undefined
      }
      actions={
        <>
          <Button variant="outline" asChild>
            <Link to="/admin/connectors">
              <ArrowLeft aria-hidden="true" />
              All sources
            </Link>
          </Button>
          <Button
            variant="outline"
            disabled={Boolean(crawlDisabledReason) || crawlNow.isPending}
            title={crawlDisabledReason}
            onClick={() => crawlNow.mutate({ path: { sourceSystem, connectionKey } })}
          >
            <RefreshCw aria-hidden="true" />
            {crawlNow.isPending ? "Requesting…" : "Crawl now"}
          </Button>
          <Button asChild>
            <Link
              to="/admin/connectors/$sourceSystem"
              params={{ sourceSystem }}
              search={{ connection: connectionKey }}
            >
              Configure
            </Link>
          </Button>
        </>
      }
    >
      <AdminStats
        stats={[
          { label: "Available objects", value: activity.data?.objectsActive ?? 0 },
          { label: "Retired objects", value: activity.data?.objectsArchived ?? 0 },
          {
            label: "Last crawl",
            value: formatDate(activity.data?.lastCrawlAt, { fallback: "Never" }),
            hint: activity.data?.lastCrawlAt ? undefined : "No checkpoint yet",
          },
          {
            label: "Last object change",
            value: formatDate(activity.data?.lastObjectAt, { fallback: "None" }),
          },
        ]}
      />

      {blocked ? (
        <Alert variant="destructive">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>This connection is on but cannot read {sourceName}</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>No credential is stored, so the worker cannot contact {sourceName}.</p>
            <Button size="sm" variant="outline" asChild>
              <Link
                to="/admin/connectors/$sourceSystem"
                params={{ sourceSystem }}
                search={{ connection: connectionKey, step: "credential" }}
              >
                Store a credential
              </Link>
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {!blocked && stalled ? (
        <Alert
          variant={partial ? "default" : "destructive"}
          className={
            partial
              ? "border-status-warning-border bg-status-warning-surface text-status-warning-content"
              : undefined
          }
        >
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>{latestCrawlTitle}</AlertTitle>
          <AlertDescription
            className={partial ? "space-y-2 text-status-warning-content/90" : "space-y-2"}
          >
            <p>
              {latestIssue?.detail ??
                OUTCOMES[lastAttempt.outcome ?? ""]?.hint ??
                "The crawl reported a failure."}
              {!latestIssue && lastAttempt.errorMessage ? ` ${lastAttempt.errorMessage}` : ""}
            </p>
            {lastAttempt.errorCode === "GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED" ? (
              <Button size="sm" variant="outline" asChild>
                <Link
                  to="/admin/connectors/$sourceSystem"
                  params={{ sourceSystem }}
                  search={{ connection: connectionKey, step: "scope" }}
                >
                  Review crawl limits
                </Link>
              </Button>
            ) : null}
          </AlertDescription>
        </Alert>
      ) : null}

      <AdminSection
        title="Sync health"
        description="Each component advances independently; incomplete evidence never becomes a successful checkpoint."
        icon={<ShieldCheck aria-hidden="true" />}
      >
        {componentCheckpoints.length === 0 ? (
          <AdminEmpty
            title="No component has been observed"
            description="Health appears after the worker handles the first batch."
          />
        ) : (
          <dl className="grid gap-px bg-border-subtle sm:grid-cols-2">
            {componentCheckpoints.map((checkpoint) => (
              <ComponentCheckpoint
                key={checkpoint.component ?? checkpoint.observedCursor}
                checkpoint={checkpoint}
              />
            ))}
          </dl>
        )}
      </AdminSection>

      <AdminSection
        title="Recent crawls"
        description="Newest first. Failed polls remain visible even when no batch was produced."
        icon={<History aria-hidden="true" />}
      >
        {attempts.length === 0 ? (
          <AdminEmpty
            title="Nothing has been crawled yet"
            description="Attempts appear after the worker polls this connection."
          />
        ) : (
          <DataTable
            columns={attemptColumns}
            data={attempts}
            getRowId={(attempt, index) => `${attempt.attemptedAt}-${index}`}
          />
        )}
      </AdminSection>

      <AdminSection
        title="Configuration"
        description="Current destination, credential state, scope, and crawl limits."
        icon={<Settings2 aria-hidden="true" />}
      >
        <div className="grid gap-px bg-border-subtle lg:grid-cols-2">
          <ConfigurationGroup title="Connection">
            <SettingRow label="Crawl">
              {connection.crawlEnabled ? (
                <Badge variant="success">On</Badge>
              ) : (
                <Badge variant="outline">Off</Badge>
              )}
            </SettingRow>
            <SettingRow label="Publishes into">
              {space?.name ?? space?.key ?? "Not set"}
            </SettingRow>
            <SettingRow label="Credential">
              {connection.credentialSet ? (
                <span>Encrypted · stored {formatDate(connection.credentialSetAt)}</span>
              ) : (
                <Badge variant="warning">None</Badge>
              )}
            </SettingRow>
            <SettingRow label="Content interval">
              {Math.round((connection.contentCrawlIntervalSeconds ?? 0) / 60)} minutes
            </SettingRow>
          </ConfigurationGroup>

          <ConfigurationGroup title={`${sourceName} scope`}>
            {descriptor ? (
              allFields(descriptor).map((field) => (
                <SettingRow
                  key={field.name}
                  label={
                    field.type === "number" && field.summaryFormat === "bytes"
                      ? field.label.replace(" bytes", "")
                      : field.label
                  }
                >
                  {formatConnectionSetting(field, connection.sourceConfig?.[field.name])}
                </SettingRow>
              ))
            ) : (
              <SettingRow label="Source settings">No source-specific settings</SettingRow>
            )}
          </ConfigurationGroup>
        </div>
      </AdminSection>
    </AdminPage>
  )
}

function ConfigurationGroup({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <section className="min-w-0 bg-card">
      <h3 className="border-b border-border-subtle px-4 py-3 text-sm font-semibold">{title}</h3>
      <dl className="divide-y divide-border-subtle">{children}</dl>
    </section>
  )
}

function SettingRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1 px-4 py-3 sm:grid-cols-[13rem_minmax(0,1fr)] sm:gap-4">
      <dt className="font-medium">{label}</dt>
      <dd className="text-muted-foreground">{children}</dd>
    </div>
  )
}

/** Format a stored source setting for the read-only connection summary. */
export function formatConnectionSetting(field: ConnectorField, value: unknown): string {
  const stored = value ?? ("default" in field ? field.default : undefined)
  if (field.type === "number" && field.summaryFormat === "bytes" && typeof stored === "number") {
    return formatBytes(stored)
  }
  if (Array.isArray(stored)) return stored.length ? stored.join(", ") : "All"
  if (typeof stored === "boolean") return stored ? "Yes" : "No"
  if (typeof stored === "number") return new Intl.NumberFormat().format(stored)
  if (stored === undefined || stored === null || stored === "") {
    return field.type === "list" || field.type === "scopes" ? "All" : "Not set"
  }
  return String(stored)
}

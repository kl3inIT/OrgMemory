import { useMutation, useQueries } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { ArrowLeft, RefreshCw, TriangleAlert } from "lucide-react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { formatTimestamp } from "@/features/admin/admin-labels"
import {
  adminConnectionActivityQueryOptions,
  adminConnectionsQueryOptions,
  knowledgeSpacesQueryOptions,
} from "@/features/admin/admin-queries"
import { requestAdminConnectionCrawlMutation } from "@/lib/hey-api/@tanstack/react-query.gen"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import { SourceIcon, type SourceIconName } from "@/features/admin/components/source-icon"
import { CONNECTOR_CATALOG } from "@/features/admin/connector-catalog"
import { allFields, CONNECTOR_FORMS } from "@/features/admin/connector-forms"
import type { AdminCrawlAttemptResponse } from "@/lib/hey-api"

/**
 * How each outcome reads on the screen.
 *
 * <p>The four are kept apart because they call for four different actions. A rejected batch is
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

function changedBy(attempt: AdminCrawlAttemptResponse) {
  const parts = [
    attempt.objectsMaterialized ? `${attempt.objectsMaterialized} new` : "",
    attempt.objectsRematerialized ? `${attempt.objectsRematerialized} changed` : "",
    attempt.objectsRotated ? `${attempt.objectsRotated} access` : "",
    attempt.objectsRetired ? `${attempt.objectsRetired} retired` : "",
    attempt.objectsFailed ? `${attempt.objectsFailed} failed` : "",
  ].filter(Boolean)
  return parts.length ? parts.join(" · ") : "Nothing changed"
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
      adminConnectionsQueryOptions(sourceSystem),
      adminConnectionActivityQueryOptions(sourceSystem, connectionKey),
      knowledgeSpacesQueryOptions(),
    ],
  })

  const crawlNow = useMutation({
    ...requestAdminConnectionCrawlMutation(),
    onSuccess: () =>
      toast.success("Crawl requested. The worker reads its content on the next poll — the attempt appears below when it does."),
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

  const connection = (connections.data ?? []).find(
    (candidate) => candidate.sourceConnectionKey === connectionKey,
  )
  const attempts = activity.data?.recentAttempts ?? []
  const space = (spaces.data ?? []).find((candidate) => candidate.id === connection?.knowledgeSpaceId)
  const catalogued = CONNECTOR_CATALOG.find((entry) => entry.sourceSystem === sourceSystem)
  const descriptor = CONNECTOR_FORMS[sourceSystem]

  // Standing rather than transient: a connection that cannot read is not a thing to be
  // discovered by whoever eventually wonders where the content went.
  const blocked = connection?.crawlEnabled && !connection.credentialSet
  const lastAttempt = attempts[0]
  const stalled = lastAttempt && lastAttempt.outcome !== "SUCCEEDED"

  return (
    <AdminPage
      title={`${catalogued?.name ?? sourceSystem} · ${connectionKey}`}
      icon={
        catalogued ? <SourceIcon name={catalogued.icon as SourceIconName} className="size-6" /> : undefined
      }
      actions={
        <>
          <Button variant="outline" asChild>
            <Link to="/admin/connectors">
              <ArrowLeft aria-hidden="true" />
              Sources
            </Link>
          </Button>
          {/* Only meaningful once the connection can actually read: an off or credential-less
              connection accepts the request and the worker, which polls neither, never acts. */}
          <Button
            variant="outline"
            disabled={!connection?.crawlEnabled || !connection.credentialSet || crawlNow.isPending}
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
          { label: "Objects retrievable", value: activity.data?.objectsActive ?? 0 },
          { label: "Retired", value: activity.data?.objectsArchived ?? 0 },
          {
            label: "Last indexed",
            value: activity.data?.lastCrawlAt ? formatTimestamp(activity.data.lastCrawlAt) : "Never",
            hint: activity.data?.lastCrawlAt ? undefined : "Nothing has been checkpointed",
          },
          {
            label: "Last change",
            value: activity.data?.lastObjectAt ? formatTimestamp(activity.data.lastObjectAt) : "None",
          },
        ]}
      />

      {blocked ? (
        <Alert variant="destructive">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>This connection is switched on but cannot read anything</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>
              The crawl is enabled and no credential is stored, so every poll ends without
              contacting {catalogued?.name ?? sourceSystem}.
            </p>
            {/* Straight to the step that fixes it. Naming the problem and leaving the reader to
                find where it is solved is the same as not naming it. */}
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
        <Alert variant="destructive">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>The most recent crawl did not succeed</AlertTitle>
          <AlertDescription>
            {OUTCOMES[lastAttempt.outcome ?? ""]?.hint ?? "The crawl reported a failure."}{" "}
            {lastAttempt.errorMessage}
          </AlertDescription>
        </Alert>
      ) : null}

      <AdminSection
        title="Configuration"
        description="What an administrator told this connection to do. The credential is not among it — it is stored encrypted and is never returned in any form."
      >
        <Table>
          <TableBody>
            <SettingRow label="Crawl">
              {connection?.crawlEnabled ? (
                <Badge variant="success">On</Badge>
              ) : (
                <Badge variant="outline">Off</Badge>
              )}
            </SettingRow>
            <SettingRow label="Publishes into">{space?.name ?? space?.key ?? "Not set"}</SettingRow>
            <SettingRow label="Credential">
              {connection?.credentialSet ? (
                <span>Stored {formatTimestamp(connection.credentialSetAt)}</span>
              ) : (
                <Badge variant="warning">None</Badge>
              )}
            </SettingRow>
            <SettingRow label="Content interval">
              {Math.round((connection?.contentCrawlIntervalSeconds ?? 0) / 60)} minutes
            </SettingRow>
            {/* Read from the same descriptor the form is rendered from, so a setting can never
                appear on one and not the other. */}
            {descriptor
              ? allFields(descriptor).map((field) => (
                  <SettingRow key={field.name} label={field.label}>
                    {formatSetting(connection?.sourceConfig?.[field.name])}
                  </SettingRow>
                ))
              : null}
          </TableBody>
        </Table>
      </AdminSection>

      <AdminSection
        title="Recent crawls"
        description="One row per batch the driver acted on, newest first. A connection that produced no batch at all appears here too — that is what a revoked credential looks like."
      >
        {attempts.length === 0 ? (
          <AdminEmpty
            title="Nothing has been crawled yet"
            description="Attempts appear here once the worker has polled this connection."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Outcome</TableHead>
                <TableHead>When</TableHead>
                <TableHead>Changed</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {attempts.map((attempt, index) => {
                const outcome = OUTCOMES[attempt.outcome ?? ""]
                return (
                  <TableRow key={`${attempt.attemptedAt}-${index}`}>
                    <TableCell>
                      <Badge variant={outcome?.variant ?? "muted"}>
                        {outcome?.label ?? attempt.outcome}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatTimestamp(attempt.attemptedAt)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{changedBy(attempt)}</TableCell>
                    <TableCell className="max-w-md text-muted-foreground">
                      {attempt.errorCode ? (
                        <code className="rounded bg-muted px-1 py-0.5 text-xs">{attempt.errorCode}</code>
                      ) : null}
                      {attempt.errorMessage ? (
                        <div className="mt-0.5 text-xs">{attempt.errorMessage}</div>
                      ) : null}
                      {!attempt.errorCode && !attempt.errorMessage ? "—" : null}
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

function SettingRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <TableRow>
      <TableCell className="w-52 font-medium">{label}</TableCell>
      <TableCell className="text-muted-foreground">{children}</TableCell>
    </TableRow>
  )
}

/** A stored setting, whatever shape the source gave it. Absent reads as the default, not blank. */
function formatSetting(value: unknown) {
  if (Array.isArray(value)) {
    return value.length ? value.join(", ") : "All"
  }
  if (typeof value === "boolean") return value ? "Yes" : "No"
  if (value === undefined || value === null || value === "") return "Not set"
  return String(value)
}

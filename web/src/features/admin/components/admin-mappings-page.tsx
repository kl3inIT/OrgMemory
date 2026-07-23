import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { connectionLabel, formatTimestamp, principalName } from "@/features/admin/admin-labels"
import { pageItems } from "@/features/admin/admin-collection"
import {
  adminSourceConnectionsQueryOptions,
  adminSourcePrincipalsQueryOptions,
  adminUsersQueryOptions,
  invalidateAdminData,
} from "@/features/admin/admin-queries"
import {
  AdminPagination,
  AdminSearch,
} from "@/features/admin/components/admin-collection-controls"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import { ConfirmMappingDialog } from "@/features/admin/components/confirm-mapping-dialog"
import { MappingBadge } from "@/features/admin/components/mapping-badge"
import {
  confirmAdminSourceMappingMutation,
  revokeAdminSourceMappingMutation,
  setAdminSourceConnectionTrustMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminSourcePrincipalResponse } from "@/lib/hey-api"

const TRUST_OPTIONS = [
  { value: "UNTRUSTED", label: "Untrusted" },
  { value: "SSO_VERIFIED", label: "SSO verified" },
] as const

export function AdminMappingsPage() {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState<AdminSourcePrincipalResponse>()
  const [principalQuery, setPrincipalQuery] = useState("")
  const [mappingFilter, setMappingFilter] = useState("all")
  const [principalPage, setPrincipalPage] = useState(1)

  const [principals, connections, users] = useQueries({
    queries: [
      adminSourcePrincipalsQueryOptions(),
      adminSourceConnectionsQueryOptions(),
      adminUsersQueryOptions(),
    ],
  })

  async function refresh(message: string) {
    await invalidateAdminData(queryClient)
    toast.success(message)
  }

  const confirm = useMutation({
    ...confirmAdminSourceMappingMutation(),
    onSuccess: async () => {
      setConfirming(undefined)
      await refresh("Identity confirmed. Retrieval resolves it from the next query.")
    },
    onError: () => toast.error("The identity could not be confirmed. Revoke any existing mapping first."),
  })

  const revoke = useMutation({
    ...revokeAdminSourceMappingMutation(),
    onSuccess: () => refresh("Mapping revoked. This principal now resolves to nobody."),
    onError: () => toast.error("The mapping could not be revoked."),
  })

  const trust = useMutation({
    ...setAdminSourceConnectionTrustMutation(),
    onSuccess: () => refresh("Connection trust recorded."),
    onError: () => toast.error("The trust level could not be saved."),
  })

  if (principals.isPending || connections.isPending || users.isPending) {
    return <LoadingState label="Loading source mappings" className="min-h-full flex-1" />
  }

  const failed = [principals, connections, users].find((query) => query.isError)
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Source mappings could not be loaded"
          description="Administration requires organization administrator permission."
          error={failed.error}
          onRetry={() => {
            void principals.refetch()
            void connections.refetch()
            void users.refetch()
          }}
        />
      </div>
    )
  }

  const principalRows = principals.data ?? []
  const connectionRows = connections.data ?? []
  const userRows = users.data ?? []
  const people = principalRows.filter((principal) => principal.kind === "SOURCE_USER")
  const unmapped = people.filter((principal) => !principal.mapping)
  const normalizedQuery = principalQuery.trim().toLocaleLowerCase()
  const filteredPrincipals = principalRows.filter((principal) => {
    const isGroup = principal.kind === "SOURCE_GROUP"
    const matchesQuery =
      !normalizedQuery ||
      [
        principalName(principal),
        principal.observedEmail,
        principal.externalKey,
        principal.mapping?.appUserName,
        principal.mapping?.appUserEmail,
      ]
        .filter(Boolean)
        .some((value) => value!.toLocaleLowerCase().includes(normalizedQuery))
    const matchesFilter =
      mappingFilter === "all" ||
      (mappingFilter === "unmapped" && !isGroup && !principal.mapping) ||
      (mappingFilter === "mapped" && !isGroup && Boolean(principal.mapping)) ||
      (mappingFilter === "groups" && isGroup)
    return matchesQuery && matchesFilter
  })
  const visiblePrincipals = pageItems(filteredPrincipals, principalPage)

  function updatePrincipalQuery(value: string) {
    setPrincipalQuery(value)
    setPrincipalPage(1)
  }

  function updateMappingFilter(value: string) {
    setMappingFilter(value)
    setPrincipalPage(1)
  }

  return (
    <AdminPage
      title="Source mappings"
      description="External identities observed by a crawl. Observation grants nothing: a principal that resolves to no internal user is denied everywhere."
    >
      <AdminStats
        stats={[
          { label: "Observed people", value: people.length },
          { label: "Resolved", value: people.length - unmapped.length },
          { label: "Unmapped", value: unmapped.length, hint: "Currently denied" },
          { label: "Connections", value: connectionRows.length },
        ]}
      />

      <AdminSection
        title="Connections"
        description="Mark a connection SSO verified only when its workspace authenticates through the same identity provider. That decision is what lets an observed email resolve on its own."
      >
        {connectionRows.length === 0 ? (
          <AdminEmpty
            title="No connections observed"
            description="A connection appears here after its first crawl reports identities."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Connection</TableHead>
                <TableHead>Identity trust</TableHead>
                <TableHead className="text-right">People</TableHead>
                <TableHead className="text-right">Unmapped</TableHead>
                <TableHead className="text-right">Last seen</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {connectionRows.map((connection) => {
                const key = `${connection.sourceSystem}/${connection.sourceConnectionKey}`
                const pending =
                  trust.isPending &&
                  trust.variables?.body.sourceConnectionKey === connection.sourceConnectionKey &&
                  trust.variables?.body.sourceSystem === connection.sourceSystem
                return (
                  <TableRow key={key}>
                    <TableCell className="font-medium">
                      {connectionLabel(connection.sourceSystem, connection.sourceConnectionKey)}
                    </TableCell>
                    <TableCell>
                      <Select
                        value={connection.identityTrust}
                        disabled={pending}
                        onValueChange={(value: string) =>
                          trust.mutate({
                            body: {
                              sourceSystem: connection.sourceSystem,
                              sourceConnectionKey: connection.sourceConnectionKey,
                              identityTrust: value as "UNTRUSTED" | "SSO_VERIFIED",
                            },
                          })
                        }
                      >
                        <SelectTrigger className="w-44" aria-label={`Identity trust for ${key}`}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {TRUST_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{connection.userCount ?? 0}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {connection.unmappedUserCount ? (
                        <Badge variant="warning">{connection.unmappedUserCount}</Badge>
                      ) : (
                        0
                      )}
                    </TableCell>
                    <TableCell className="text-right text-muted-foreground">
                      {formatTimestamp(connection.lastSeenAt)}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </AdminSection>

      <AdminSection
        title="Observed principals"
        description="Unmapped principals are listed first. Confirming one records an administrator-vouched link; revoking closes it without erasing the audit trail."
        toolbar={
          <>
            <AdminSearch
              value={principalQuery}
              onChange={updatePrincipalQuery}
              placeholder="Search observed principals"
            />
            <Select value={mappingFilter} onValueChange={updateMappingFilter}>
              <SelectTrigger className="w-full sm:ml-auto sm:w-44" aria-label="Filter principals">
                <SelectValue />
              </SelectTrigger>
              <SelectContent align="end">
                <SelectItem value="all">All principals</SelectItem>
                <SelectItem value="unmapped">Unmapped people</SelectItem>
                <SelectItem value="mapped">Mapped people</SelectItem>
                <SelectItem value="groups">Groups</SelectItem>
              </SelectContent>
            </Select>
          </>
        }
        footer={
          <AdminPagination
            page={principalPage}
            total={filteredPrincipals.length}
            onPageChange={setPrincipalPage}
          />
        }
      >
        {filteredPrincipals.length === 0 ? (
          <AdminEmpty
            title={principalRows.length === 0 ? "No principals observed" : "No principals found"}
            description={
              principalRows.length === 0
                ? "Principals appear after a connector crawl reports the source's identities."
                : "Try another name, email, external key, or mapping state."
            }
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Principal</TableHead>
                <TableHead>Connection</TableHead>
                <TableHead>Mapping</TableHead>
                <TableHead>Internal user</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visiblePrincipals.map((principal) => {
                const isGroup = principal.kind === "SOURCE_GROUP"
                const pending = revoke.isPending && revoke.variables?.path.principalId === principal.id
                return (
                  <TableRow key={principal.id}>
                    <TableCell>
                      <div className="min-w-56">
                        <div className="flex items-center gap-2">
                          <span className="truncate font-medium">{principalName(principal)}</span>
                          {isGroup ? <Badge variant="outline">Group</Badge> : null}
                          {principal.ssoVerified ? <Badge variant="secondary">SSO</Badge> : null}
                        </div>
                        <div className="mt-0.5 truncate text-xs text-muted-foreground">
                          {principal.observedEmail || principal.externalKey}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {connectionLabel(principal.sourceSystem, principal.sourceConnectionKey)}
                    </TableCell>
                    <TableCell>
                      {isGroup ? (
                        <span className="text-sm text-muted-foreground">Membership is sealed</span>
                      ) : (
                        <MappingBadge mapping={principal.mapping} />
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="min-w-40 truncate text-sm">
                        {principal.mapping?.appUserName ?? principal.mapping?.appUserEmail ?? "—"}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      {isGroup ? null : principal.mapping ? (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={pending}
                          onClick={() => revoke.mutate({ path: { principalId: principal.id! } })}
                        >
                          Revoke
                        </Button>
                      ) : (
                        <Button size="sm" onClick={() => setConfirming(principal)}>
                          Confirm
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </AdminSection>

      <ConfirmMappingDialog
        principal={confirming}
        users={userRows}
        pending={confirm.isPending}
        onOpenChange={(open) => !open && setConfirming(undefined)}
        onConfirm={(appUserId) =>
          confirming?.id && confirm.mutate({ path: { principalId: confirming.id }, body: { appUserId } })
        }
      />
    </AdminPage>
  )
}

import { useQuery } from "@tanstack/react-query"
import { ChevronDown } from "lucide-react"
import { useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { connectionLabel, formatTimestamp } from "@/features/admin/admin-labels"
import { pageItems } from "@/features/admin/admin-collection"
import { adminSourceGroupsQueryOptions } from "@/features/admin/admin-queries"
import {
  AdminPagination,
  AdminSearch,
} from "@/features/admin/components/admin-collection-controls"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import type { AdminSourceGroupResponse } from "@/lib/hey-api"

function SourceGroupRow({ group }: { group: AdminSourceGroupResponse }) {
  const unresolved = group.members?.filter((member) => !member.appUserId).length ?? 0

  return (
    <Collapsible className="group border-b border-border-subtle last:border-b-0">
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center gap-3 px-4 py-3 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring"
        >
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">
              {group.observedDisplayName?.trim() || group.externalKey || "Unnamed group"}
            </span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground">
              {connectionLabel(group.sourceSystem, group.sourceConnectionKey)} · sealed{" "}
              {formatTimestamp(group.sealedAt)}
            </span>
          </span>
          <span className="hidden items-center gap-2 sm:flex">
            <Badge variant="outline">Generation {group.aclGeneration ?? 0}</Badge>
            <Badge variant={unresolved ? "warning" : "secondary"}>
              {group.members?.length ?? 0} members
            </Badge>
          </span>
          <ChevronDown
            className="size-4 shrink-0 text-muted-foreground transition-transform group-data-[state=open]:rotate-180"
            aria-hidden="true"
          />
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent className="border-t border-border-subtle">
        {group.members?.length ? (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Member</TableHead>
                <TableHead>Observed email</TableHead>
                <TableHead className="text-right">Resolves to</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {group.members.map((member) => (
                <TableRow key={member.principalId}>
                  <TableCell className="font-medium">
                    {member.observedDisplayName?.trim() || member.externalKey}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {member.observedEmail || "—"}
                  </TableCell>
                  <TableCell className="text-right">
                    {member.appUserId ? member.appUserName : <Badge variant="destructive">Unmapped</Badge>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <AdminEmpty
            title="No sealed membership"
            description="The latest generation for this group carries no members."
          />
        )}
      </CollapsibleContent>
    </Collapsible>
  )
}

export function AdminGroupsPage() {
  const [query, setQuery] = useState("")
  const [page, setPage] = useState(1)
  const groups = useQuery(adminSourceGroupsQueryOptions())

  if (groups.isPending) {
    return <LoadingState label="Loading source groups" className="min-h-full flex-1" />
  }

  if (groups.isError) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Source groups could not be loaded"
          description="Administration requires organization administrator permission."
          error={groups.error}
          onRetry={() => groups.refetch()}
        />
      </div>
    )
  }

  const rows = groups.data ?? []
  const totalMembers = rows.reduce((total, group) => total + (group.members?.length ?? 0), 0)
  const unresolved = rows.reduce(
    (total, group) => total + (group.members?.filter((member) => !member.appUserId).length ?? 0),
    0,
  )
  const normalizedQuery = query.trim().toLocaleLowerCase()
  const filteredRows = rows.filter((group) =>
    !normalizedQuery
      ? true
      : [
          group.observedDisplayName,
          group.externalKey,
          group.sourceSystem,
          group.sourceConnectionKey,
        ]
          .filter(Boolean)
          .some((value) => value!.toLocaleLowerCase().includes(normalizedQuery)),
  )
  const visibleRows = pageItems(filteredRows, page)

  function updateQuery(value: string) {
    setQuery(value)
    setPage(1)
  }

  return (
    <AdminPage
      title="Source groups"
      description="Membership as it was sealed with the latest permissions generation. It is evidence, not configuration: it cannot be edited here because enforcement reads exactly this."
    >
      <AdminStats
        stats={[
          { label: "Groups", value: rows.length },
          { label: "Sealed memberships", value: totalMembers },
          { label: "Members resolving to nobody", value: unresolved, hint: "Denied until mapped" },
        ]}
      />

      <AdminSection
        title="Sealed source groups"
        toolbar={<AdminSearch value={query} onChange={updateQuery} placeholder="Search source groups" />}
        footer={<AdminPagination page={page} total={filteredRows.length} onPageChange={setPage} />}
      >
        {filteredRows.length === 0 ? (
          rows.length === 0 ? (
            <AdminEmpty
              title="No source groups sealed yet"
              description="Groups appear after a connector crawl seals a permissions generation that contains group membership."
            />
          ) : (
            <AdminEmpty
              title="No source groups found"
              description="Try another group name, external key, or connection."
            />
          )
        ) : (
          visibleRows.map((group) => <SourceGroupRow key={group.principalId} group={group} />)
        )}
      </AdminSection>
    </AdminPage>
  )
}

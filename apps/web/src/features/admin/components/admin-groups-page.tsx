import { useQuery } from "@tanstack/react-query"
import { ChevronDown } from "lucide-react"
import { useState } from "react"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { CollectionPagination } from "@/components/patterns/collection-pagination"
import { Badge } from "@/components/ui/badge"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { connectionLabel } from "@/features/admin/admin-labels"
import { ADMIN_PAGE_SIZE, pageItems } from "@/features/admin/admin-collection"
import { adminQuery } from "@/features/admin/admin-queries"
import { AdminSearch } from "@/features/admin/components/admin-collection-controls"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import type {
  AdminSourceGroupMemberResponse,
  AdminSourceGroupResponse,
} from "@/lib/hey-api"
import { listAdminSourceGroupsOptions } from "@/lib/hey-api/@tanstack/react-query.gen"
import { formatDate } from "@/lib/format"

const memberColumns: ColumnDef<AdminSourceGroupMemberResponse>[] = [
  {
    id: "member",
    accessorFn: (member) => member.observedDisplayName?.trim() || member.nativePrincipalId || "",
    header: "Member",
    enableSorting: true,
    cell: ({ row }) => (
      <span className="font-medium">
        {row.original.observedDisplayName?.trim() || row.original.nativePrincipalId}
      </span>
    ),
  },
  {
    accessorKey: "observedEmail",
    header: "Observed email",
    enableSorting: true,
    cell: ({ row }) => (
      <span className="text-muted-foreground">{row.original.observedEmail || "—"}</span>
    ),
  },
  {
    id: "resolvesTo",
    accessorFn: (member) => member.appUserName ?? "",
    header: "Resolves to",
    enableSorting: true,
    meta: {
      headerClassName: "text-right",
      cellClassName: "text-right",
    },
    cell: ({ row }) =>
      row.original.appUserId ? (
        row.original.appUserName
      ) : (
        <Badge variant="destructive">Unmapped</Badge>
      ),
  },
]

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
              {group.observedDisplayName?.trim() || group.nativePrincipalId || "Unnamed group"}
            </span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground">
              {connectionLabel(group.sourceSystem, group.sourceConnectionKey)} · sealed{" "}
              {formatDate(group.sealedAt)}
            </span>
          </span>
          <span className="hidden items-center gap-2 sm:flex">
            <Badge variant="outline">Generation {group.membershipGeneration ?? 0}</Badge>
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
          <DataTable
            columns={memberColumns}
            data={group.members}
            getRowId={(member, index) => member.principalId ?? String(index)}
          />
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
  const groups = useQuery(adminQuery(listAdminSourceGroupsOptions()))

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
          group.nativePrincipalId,
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
        footer={
          <CollectionPagination
            page={page}
            pageSize={ADMIN_PAGE_SIZE}
            total={filteredRows.length}
            onPageChange={setPage}
          />
        }
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

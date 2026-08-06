import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { Ellipsis, RefreshCw } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { CollectionPagination } from "@/components/patterns/collection-pagination"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
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
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import {
  clearanceLabel,
  CLEARANCES,
  type ClearanceValue,
} from "@/features/admin/admin-labels"
import { ADMIN_PAGE_SIZE, pageItems } from "@/features/admin/admin-collection"
import {
  adminQuery,
  invalidateAdminData,
  organizationContextQueryOptions,
} from "@/features/admin/admin-queries"
import { AdminSearch } from "@/features/admin/components/admin-collection-controls"
import { AdminInvitationsCard } from "@/features/admin/components/admin-invitations-card"
import { AdminEmpty, AdminPage } from "@/features/admin/components/admin-page"
import { avatarInitials } from "@/lib/avatar"
import {
  listAdminUsersOptions,
  updateAdminUserMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminUserResponse } from "@/lib/hey-api"

const NO_DEPARTMENT = "__none__"

export function AdminUsersPage({ currentUserId }: { currentUserId?: string }) {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState("")
  const [clearance, setClearance] = useState("all")
  const [signIn, setSignIn] = useState("all")
  const [accountStatus, setAccountStatus] = useState("all")
  const [page, setPage] = useState(1)
  const [executiveCandidate, setExecutiveCandidate] = useState<AdminUserResponse>()
  const users = useQuery(adminQuery(listAdminUsersOptions()))
  const organization = useQuery(organizationContextQueryOptions())
  const update = useMutation({
    ...updateAdminUserMutation(),
    onSuccess: async (_data, variables) => {
      await invalidateAdminData(queryClient)
      const message =
        variables.body.active !== undefined
          ? "Account status updated."
          : variables.body.departmentId !== undefined
            ? "Department updated."
            : "Clearance updated."
      toast.success(message)
    },
    onError: () => toast.error("The change was rejected. Reload and try again."),
  })

  if (users.isPending || organization.isPending) {
    return <LoadingState label="Loading users" className="min-h-full flex-1" />
  }

  if (users.isError || organization.isError) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Users could not be loaded"
          description="Administration requires organization administrator permission."
          error={users.error ?? organization.error}
          onRetry={() => void Promise.all([users.refetch(), organization.refetch()])}
        />
      </div>
    )
  }

  const rows = users.data ?? []
  const departments = organization.data?.departments ?? []
  const departmentNames = new Map(
    departments.flatMap((department) =>
      department.id ? [[department.id, department.name ?? "Unnamed department"] as const] : [],
    ),
  )
  const linked = rows.filter((user) => user.signInLinked).length
  const inactive = rows.filter((user) => !user.active).length
  const normalizedQuery = query.trim().toLocaleLowerCase()
  const filteredRows = rows.filter((user) => {
    const matchesQuery =
      !normalizedQuery ||
      [
        user.name,
        user.email,
        clearanceLabel(user.clearance),
        user.departmentId ? departmentNames.get(user.departmentId) : "No department",
      ]
        .filter(Boolean)
        .some((value) => value!.toLocaleLowerCase().includes(normalizedQuery))
    const matchesClearance = clearance === "all" || user.clearance === clearance
    const matchesSignIn =
      signIn === "all" ||
      (signIn === "linked" && user.signInLinked) ||
      (signIn === "unlinked" && !user.signInLinked)
    const matchesAccountStatus =
      accountStatus === "all" ||
      (accountStatus === "active" && user.active) ||
      (accountStatus === "deactivated" && !user.active)
    return matchesQuery && matchesClearance && matchesSignIn && matchesAccountStatus
  })
  const visibleRows = pageItems(filteredRows, page)
  const columns: ColumnDef<AdminUserResponse>[] = [
    {
      id: "user",
      accessorFn: (user) => user.name ?? user.email ?? "",
      header: "User",
      cell: ({ row }) => {
        const user = row.original
        return (
          <div className="flex min-w-64 items-center gap-3">
            <Avatar className="size-9">
              <AvatarFallback>{avatarInitials(user.name, user.email)}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <Link
                to="/admin/users/$userId"
                params={{ userId: user.id! }}
                className="block truncate font-medium hover:underline"
              >
                {user.name ?? "Unnamed user"}
              </Link>
              <div className="mt-0.5 truncate text-xs text-muted-foreground">
                {user.email}
              </div>
            </div>
          </div>
        )
      },
    },
    {
      id: "clearance",
      accessorFn: (user) => clearanceLabel(user.clearance),
      header: "Clearance",
      cell: ({ row }) => {
        const user = row.original
        const isSelf = user.id === currentUserId
        const pending = update.isPending && update.variables?.path.userId === user.id
        return (
          <Select
            value={user.clearance}
            disabled={isSelf || pending}
            onValueChange={(nextClearance: string) => {
              if (nextClearance === "EXECUTIVE" && user.clearance !== "EXECUTIVE") {
                setExecutiveCandidate(user)
                return
              }
              update.mutate({
                path: { userId: user.id! },
                body: { clearance: nextClearance as ClearanceValue },
              })
            }}
          >
            <SelectTrigger
              className="w-40"
              aria-label={`Clearance for ${user.name ?? user.email ?? "user"}`}
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CLEARANCES.map((value) => (
                <SelectItem key={value} value={value}>
                  {clearanceLabel(value)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )
      },
    },
    {
      id: "department",
      accessorFn: (user) =>
        user.departmentId ? (departmentNames.get(user.departmentId) ?? "Unknown department") : "No department",
      header: "Department",
      cell: ({ row }) => {
        const user = row.original
        const isSelf = user.id === currentUserId
        const pending = update.isPending && update.variables?.path.userId === user.id
        return (
          <Select
            value={user.departmentId ?? NO_DEPARTMENT}
            disabled={isSelf || pending}
            onValueChange={(departmentId: string) =>
              update.mutate({
                path: { userId: user.id! },
                body: { departmentId: departmentId === NO_DEPARTMENT ? null : departmentId },
              })
            }
          >
            <SelectTrigger
              className="w-44"
              aria-label={`Department for ${user.name ?? user.email ?? "user"}`}
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_DEPARTMENT}>No department</SelectItem>
              {departments.map((department) => (
                <SelectItem key={department.id} value={department.id!}>
                  {department.name ?? "Unnamed department"}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )
      },
    },
    {
      id: "signIn",
      accessorFn: (user) => (user.signInLinked ? "Linked" : "Not linked"),
      header: "Sign-in",
      cell: ({ row }) => (
        <span className="inline-flex items-center gap-2 text-sm">
          <span
            className={`size-1.5 rounded-full ${
              row.original.signInLinked
                ? "bg-status-success-content"
                : "bg-status-warning-content"
            }`}
            aria-hidden="true"
          />
          {row.original.signInLinked ? "Linked" : "Not linked"}
        </span>
      ),
    },
    {
      accessorKey: "mappedPrincipalCount",
      header: "Source identities",
      meta: { cellClassName: "tabular-nums" },
      cell: ({ row }) => row.original.mappedPrincipalCount ?? 0,
    },
    {
      id: "status",
      accessorFn: (user) => (user.active ? "Active" : "Deactivated"),
      header: "Status",
      cell: ({ row }) => (
        <span className="inline-flex items-center gap-2 text-sm">
          <span
            className={`size-1.5 rounded-full ${
              row.original.active
                ? "bg-status-success-content"
                : "bg-status-danger-content"
            }`}
            aria-hidden="true"
          />
          {row.original.active ? "Active" : "Deactivated"}
        </span>
      ),
    },
    {
      id: "actions",
      header: () => <span className="sr-only">Actions</span>,
      meta: { headerClassName: "w-12" },
      cell: ({ row }) => {
        const user = row.original
        const isSelf = user.id === currentUserId
        const pending = update.isPending && update.variables?.path.userId === user.id
        return (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={pending}
                aria-label={`Actions for ${user.name ?? user.email ?? "user"}`}
              >
                <Ellipsis aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                variant={user.active ? "destructive" : "default"}
                disabled={isSelf || pending}
                onSelect={() =>
                  update.mutate({
                    path: { userId: user.id! },
                    body: { active: !user.active },
                  })
                }
              >
                {user.active ? "Deactivate account" : "Activate account"}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )
      },
    },
  ]

  function updateQuery(value: string) {
    setQuery(value)
    setPage(1)
  }

  function showAllUsers() {
    setClearance("all")
    setSignIn("all")
    setAccountStatus("all")
    setPage(1)
  }

  function updateClearance(value: string) {
    setClearance(value)
    setPage(1)
  }

  function updateSignIn(value: string) {
    setSignIn(value)
    setPage(1)
  }

  function updateAccountStatus(value: string) {
    setAccountStatus(value)
    setPage(1)
  }

  return (
    <AdminPage title="Users">
      <div className="grid gap-3 lg:grid-cols-2">
        <div className="grid min-h-24 grid-cols-2 overflow-hidden rounded-xl border border-border-subtle bg-surface-raised">
          <button
            type="button"
            className="flex flex-col justify-center gap-1 px-5 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring"
            aria-pressed={clearance === "all" && signIn === "all" && accountStatus === "all"}
            onClick={showAllUsers}
          >
            <span className="text-2xl font-semibold tabular-nums">{rows.length}</span>
            <span className="text-sm text-muted-foreground">Users</span>
          </button>
          <button
            type="button"
            className="flex flex-col justify-center gap-1 border-l border-border-subtle px-5 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring"
            aria-pressed={signIn === "linked"}
            onClick={() => updateSignIn("linked")}
          >
            <span className="text-2xl font-semibold tabular-nums">{linked}</span>
            <span className="text-sm text-muted-foreground">Linked accounts</span>
          </button>
        </div>

        <div className="flex min-h-24 items-center gap-4 rounded-xl border border-border-subtle bg-surface-raised px-5">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-surface-subtle text-muted-foreground">
            <RefreshCw className="size-4" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">SCIM provisioning</p>
            <p className="text-sm text-muted-foreground">Not configured</p>
          </div>
          <Button variant="link" size="sm" asChild>
            <Link to="/admin/scim">View setup</Link>
          </Button>
        </div>
      </div>

      <AdminInvitationsCard />

      <section className="space-y-4" aria-label="Organization directory">
        <AdminSearch value={query} onChange={updateQuery} placeholder="Search users by name or email" />

        <div className="flex flex-wrap items-center gap-2">
          <Select value={clearance} onValueChange={updateClearance}>
            <SelectTrigger className="w-full sm:w-44" aria-label="Filter by clearance">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="start">
              <SelectItem value="all">All clearances</SelectItem>
              {CLEARANCES.map((value) => (
                <SelectItem key={value} value={value}>
                  {clearanceLabel(value)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={signIn} onValueChange={updateSignIn}>
            <SelectTrigger className="w-full sm:w-48" aria-label="Filter by sign-in state">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="start">
              <SelectItem value="all">All sign-in states</SelectItem>
              <SelectItem value="linked">Linked accounts</SelectItem>
              <SelectItem value="unlinked">Not linked</SelectItem>
            </SelectContent>
          </Select>

          <Select value={accountStatus} onValueChange={updateAccountStatus}>
            <SelectTrigger className="w-full sm:w-44" aria-label="Filter by account status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="start">
              <SelectItem value="all">All statuses</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="deactivated">Deactivated</SelectItem>
            </SelectContent>
          </Select>

          {inactive > 0 ? (
            <button
              type="button"
              className="ml-auto text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
              onClick={() => updateAccountStatus("deactivated")}
            >
              {inactive} deactivated
            </button>
          ) : null}
        </div>

        {filteredRows.length === 0 ? (
          <div className="rounded-xl border border-border-subtle">
            <AdminEmpty
              title={rows.length === 0 ? "No users yet" : "No users found"}
              description={
                rows.length === 0
                  ? "Users appear once their identity provider account is linked to this organization."
                  : "Try another name, email, department, clearance, or status."
              }
            />
          </div>
        ) : (
          <div className="overflow-hidden border-y border-border-subtle">
            <DataTable
              columns={columns}
              data={visibleRows}
              getRowId={(user, index) => user.id ?? String(index)}
            />
          </div>
        )}

        <CollectionPagination
          page={page}
          pageSize={ADMIN_PAGE_SIZE}
          total={filteredRows.length}
          onPageChange={setPage}
        />
      </section>

      <AlertDialog
        open={executiveCandidate !== undefined}
        onOpenChange={(open) => {
          if (!open) setExecutiveCandidate(undefined)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Raise clearance to Executive?</AlertDialogTitle>
            <AlertDialogDescription>
              This grants {executiveCandidate?.name ?? executiveCandidate?.email ?? "this user"}
              org-wide access to CONFIDENTIAL and RESTRICTED evidence. It does not grant
              administrative permissions.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep current clearance</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (executiveCandidate?.id) {
                  update.mutate({
                    path: { userId: executiveCandidate.id },
                    body: { clearance: "EXECUTIVE" },
                  })
                }
                setExecutiveCandidate(undefined)
              }}
            >
              Raise to Executive
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AdminPage>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { Plus, X } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
import { Badge } from "@/components/ui/badge"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  adminQuery,
  adminUserPermissionsQueryOptions,
  invalidateAdminData,
} from "@/features/admin/admin-queries"
import { PERMISSION_LABELS } from "@/features/admin/access-permission-labels"
import { AccessInspector } from "@/features/admin/components/access-inspector"
import { AccessVerdict, type AccessState } from "@/features/admin/components/access-verdict"
import {
  assignAdminRoleMutation,
  listAdminRolesOptions,
  listAdminUsersOptions,
  revokeAdminRoleMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

export function AdminUserPermissionsPage({
  userId,
  currentUserId,
}: {
  userId: string
  currentUserId?: string
}) {
  const queryClient = useQueryClient()
  const users = useQuery(adminQuery(listAdminUsersOptions()))
  const roles = useQuery(adminQuery(listAdminRolesOptions()))
  const permissions = useQuery(adminUserPermissionsQueryOptions(userId))
  const assign = useMutation(assignAdminRoleMutation())
  const revoke = useMutation(revokeAdminRoleMutation())

  const [rolePickerOpen, setRolePickerOpen] = useState(false)

  const user = users.data?.find((candidate) => candidate.id === userId)
  const principal = `user:${userId}`
  const heldRoles = (roles.data?.roles ?? []).filter((role) =>
    (role.assignees ?? []).includes(principal),
  )
  const availableRoles = (roles.data?.roles ?? []).filter(
    (role) => !(role.assignees ?? []).includes(principal),
  )

  async function changeRole(action: "assign" | "revoke", role: string) {
    try {
      if (action === "assign") {
        await assign.mutateAsync({ path: { role }, body: { userId } })
      } else {
        await revoke.mutateAsync({ path: { role, userId } })
      }
      await invalidateAdminData(queryClient)
      toast.success(action === "assign" ? `Assigned ${role}` : `Removed ${role}`)
    } catch {
      toast.error("The role could not be changed")
    }
  }

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title={user?.name ?? "Permissions"}
        description={
          user ? (
            <>
              {user.email}
              {user.signInLinked ? null : " · cannot sign in yet"}
            </>
          ) : undefined
        }
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link to="/admin/users">Users</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>{user?.name ?? "Permissions"}</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <AccessInspector userId={userId} />

        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
          <CardHeader>
            <CardTitle>Organization permissions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {permissions.isPending ? (
              <Skeleton className="h-40 w-full" />
            ) : permissions.isError ? (
              <p className="text-sm text-destructive">
                The authorization engine did not answer. Nothing is implied about access.
              </p>
            ) : (
              <>
                {Object.entries(permissions.data?.permissions ?? {}).map(([key, state]) => (
                  <div key={key} className="flex items-center justify-between gap-4">
                    <span className="text-sm">{PERMISSION_LABELS[key] ?? key}</span>
                    <AccessVerdict state={state as AccessState} />
                  </div>
                ))}
                <Separator />
                <p className="text-xs text-muted-foreground">
                  Resolved when this page loaded, not stored.
                  {permissions.data?.evaluatedAt
                    ? ` ${new Date(permissions.data.evaluatedAt).toLocaleTimeString()}`
                    : null}
                </p>
              </>
            )}
          </CardContent>
          </Card>

          <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between gap-2">
              Roles
              <Badge variant="secondary" className="font-normal">
                editable
              </Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {roles.isPending ? (
              <Skeleton className="h-24 w-full" />
            ) : (
              <>
                {heldRoles.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No roles assigned.</p>
                ) : (
                  <ul className="flex flex-wrap gap-2">
                    {heldRoles.map((role) => (
                      <li key={role.role}>
                        <Badge variant="outline" className="gap-1 py-1 pr-1">
                          {role.role}
                          {userId === currentUserId ? null : (
                            <Button
                              variant="ghost"
                              size="icon"
                              className="size-4"
                              aria-label={`Remove ${role.role}`}
                              onClick={() => void changeRole("revoke", role.role!)}
                            >
                              <X className="size-3" aria-hidden />
                            </Button>
                          )}
                        </Badge>
                      </li>
                    ))}
                  </ul>
                )}

                <Dialog open={rolePickerOpen} onOpenChange={setRolePickerOpen}>
                  <DialogTrigger asChild>
                    <Button variant="outline" size="sm" disabled={availableRoles.length === 0}>
                      <Plus className="size-4" aria-hidden />
                      Assign a role
                    </Button>
                  </DialogTrigger>
                  <DialogContent className="p-0">
                    <DialogHeader className="px-4 pt-4">
                      <DialogTitle>Assign a role</DialogTitle>
                    </DialogHeader>
                    <Command>
                      <CommandInput placeholder="Find a role" />
                      <CommandList>
                        <CommandEmpty>No role matches.</CommandEmpty>
                        <CommandGroup>
                          {availableRoles.map((role) => (
                            <CommandItem
                              key={role.role}
                              value={role.role}
                              onSelect={() => {
                                setRolePickerOpen(false)
                                void changeRole("assign", role.role!)
                              }}
                            >
                              {role.role}
                            </CommandItem>
                          ))}
                        </CommandGroup>
                      </CommandList>
                    </Command>
                  </DialogContent>
                </Dialog>

                {roles.data && !roles.data.complete ? (
                  <p className="text-xs text-amber-600 dark:text-amber-400">
                    The role list stopped before the end of the store, so it may be incomplete.
                  </p>
                ) : null}
                <p className="text-xs text-muted-foreground">
                  Document permissions come from the connected source and cannot be changed here.
                </p>
              </>
            )}
          </CardContent>
          </Card>
        </div>

      </PageLayout.Body>
    </PageLayout.Root>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { ArrowLeft, Plus, Search, X } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
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
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  adminRolesQueryOptions,
  adminUserPermissionsQueryOptions,
  adminUsersQueryOptions,
  invalidateAdminData,
} from "@/features/admin/admin-queries"
import { AccessDenied, AccessPath } from "@/features/admin/components/access-path"
import { AccessVerdict, type AccessState } from "@/features/admin/components/access-verdict"
import {
  assignAdminRoleMutation,
  explainAdminAccessMutation,
  revokeAdminRoleMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

/**
 * The permissions worth showing an administrator, in the order they are usually asked
 * about. The model defines more relations than this; these are the ones that answer "what
 * can this person do".
 */
const PERMISSION_LABELS: Record<string, string> = {
  can_manage_members: "Administer the organization",
  can_manage_sources: "Manage connected sources",
  can_create_knowledge_space: "Create knowledge spaces",
  can_search_knowledge: "Search knowledge",
  can_view_directory: "View the directory",
  can_view_audit: "View the audit trail",
  can_curate_graph: "Curate the knowledge graph",
}

const EXPLAINABLE_TYPES = [
  { value: "organization", label: "Organization" },
  { value: "knowledge_space", label: "Knowledge space" },
  { value: "knowledge_asset", label: "Document" },
  { value: "organizational_unit", label: "Department" },
] as const

export function AdminUserPermissionsPage({
  userId,
  currentUserId,
}: {
  userId: string
  currentUserId?: string
}) {
  const queryClient = useQueryClient()
  const users = useQuery(adminUsersQueryOptions())
  const roles = useQuery(adminRolesQueryOptions())
  const permissions = useQuery(adminUserPermissionsQueryOptions(userId))
  const explain = useMutation(explainAdminAccessMutation())
  const assign = useMutation(assignAdminRoleMutation())
  const revoke = useMutation(revokeAdminRoleMutation())

  const [resourceType, setResourceType] = useState<string>("organization")
  const [resourceId, setResourceId] = useState("")
  const [permission, setPermission] = useState("can_search_knowledge")
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
    <div className="space-y-6">
      <div className="space-y-1">
        <Button asChild variant="ghost" size="sm" className="-ml-2">
          <Link to="/admin/users">
            <ArrowLeft className="size-4" aria-hidden />
            Users
          </Link>
        </Button>
        <h1 className="text-2xl font-semibold tracking-tight">
          {user?.name ?? "Permissions"}
        </h1>
        {user ? (
          <p className="text-sm text-muted-foreground">
            {user.email} · {user.role}
            {user.signInLinked ? null : " · cannot sign in yet"}
          </p>
        ) : null}
      </div>

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

      <Card>
        <CardHeader>
          <CardTitle>Check one resource</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-end gap-2">
            <Select value={permission} onValueChange={setPermission}>
              <SelectTrigger className="w-[220px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.keys(PERMISSION_LABELS)
                  .concat(["can_view", "can_publish", "can_edit"])
                  .map((key) => (
                    <SelectItem key={key} value={key}>
                      {PERMISSION_LABELS[key] ?? key}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>

            <Select value={resourceType} onValueChange={setResourceType}>
              <SelectTrigger className="w-[180px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {EXPLAINABLE_TYPES.map((type) => (
                  <SelectItem key={type.value} value={type.value}>
                    {type.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <input
              value={resourceId}
              onChange={(event) => setResourceId(event.target.value)}
              placeholder="Resource id"
              className="h-9 min-w-[300px] flex-1 rounded-md border bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
            />

            <Button
              disabled={!resourceId || explain.isPending}
              onClick={() =>
                explain.mutate({
                  body: { userId, permission, resourceType, resourceId },
                })
              }
            >
              <Search className="size-4" aria-hidden />
              Check
            </Button>
          </div>

          {explain.isError ? (
            <p className="text-sm text-destructive">
              That resource could not be checked. Confirm the id belongs to this organization.
            </p>
          ) : null}

          {explain.data ? (
            <div className="space-y-4 rounded-lg border p-4">
              <AccessVerdict
                state={(explain.data.state ?? "UNKNOWN") as AccessState}
                provenance={explain.data.provenance}
              />
              <Separator />
              {explain.data.state === "ALLOWED" ? (
                <AccessPath path={explain.data.path ?? []} />
              ) : explain.data.state === "DENIED" ? (
                <AccessDenied blockedBy={explain.data.blockedBy ?? []} />
              ) : (
                <p className="text-sm text-muted-foreground">
                  No current answer: {explain.data.reasonCode}. This is not a refusal.
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                Model {explain.data.policyVersion}
              </p>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}

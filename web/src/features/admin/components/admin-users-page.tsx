import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { roleLabel, USER_ROLES, type UserRoleValue } from "@/features/admin/admin-labels"
import { adminUsersQueryOptions, invalidateAdminData } from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import { updateAdminUserMutation } from "@/lib/hey-api/@tanstack/react-query.gen"

export function AdminUsersPage({ currentUserId }: { currentUserId?: string }) {
  const queryClient = useQueryClient()
  const users = useQuery(adminUsersQueryOptions())
  const update = useMutation({
    ...updateAdminUserMutation(),
    onSuccess: async (_data, variables) => {
      await invalidateAdminData(queryClient)
      toast.success(variables.body.active === undefined ? "Role updated." : "Account status updated.")
    },
    onError: () => toast.error("The change was rejected. Reload and try again."),
  })

  if (users.isPending) {
    return <LoadingState label="Loading users" className="min-h-full flex-1" />
  }

  if (users.isError) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Users could not be loaded"
          description="Administration requires organization administrator permission."
          error={users.error}
          onRetry={() => users.refetch()}
        />
      </div>
    )
  }

  const rows = users.data ?? []
  const linked = rows.filter((user) => user.signInLinked).length
  const inactive = rows.filter((user) => !user.active).length
  const mappedIdentities = rows.reduce((total, user) => total + (user.mappedPrincipalCount ?? 0), 0)

  return (
    <AdminPage
      title="Users"
      description="Accounts come from the identity provider. This screen governs what an existing user may do, not who exists."
    >
      <AdminStats
        stats={[
          { label: "Users", value: rows.length },
          { label: "Can sign in", value: linked, hint: "Linked to an identity provider account" },
          { label: "Deactivated", value: inactive },
          { label: "Mapped source identities", value: mappedIdentities },
        ]}
      />

      <AdminSection
        title="Organization directory"
        description="A user who is not linked exists here but cannot sign in until the identity provider account is connected."
      >
        {rows.length === 0 ? (
          <AdminEmpty
            title="No users yet"
            description="Users appear once their identity provider account is linked to this organization."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>User</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Sign-in</TableHead>
                <TableHead className="text-right">Source identities</TableHead>
                <TableHead className="text-right">Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((user) => {
                const isSelf = user.id === currentUserId
                const pending = update.isPending && update.variables?.path.userId === user.id
                return (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="min-w-56">
                        <div className="truncate font-medium">{user.name ?? "Unnamed user"}</div>
                        <div className="mt-0.5 truncate text-xs text-muted-foreground">{user.email}</div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Select
                        value={user.role}
                        disabled={isSelf || pending}
                        onValueChange={(role: string) =>
                          update.mutate({
                            path: { userId: user.id! },
                            body: { role: role as UserRoleValue },
                          })
                        }
                      >
                        <SelectTrigger
                          className="w-44"
                          aria-label={`Role for ${user.name ?? user.email ?? "user"}`}
                        >
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {USER_ROLES.map((role) => (
                            <SelectItem key={role} value={role}>
                              {roleLabel(role)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>
                      {user.signInLinked ? (
                        <Badge variant="success">Linked</Badge>
                      ) : (
                        <Badge variant="warning">Not linked</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{user.mappedPrincipalCount ?? 0}</TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-3">
                        <Badge variant={user.active ? "secondary" : "destructive"}>
                          {user.active ? "Active" : "Deactivated"}
                        </Badge>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={isSelf || pending}
                          title={isSelf ? "You cannot change your own account" : undefined}
                          onClick={() =>
                            update.mutate({
                              path: { userId: user.id! },
                              body: { active: !user.active },
                            })
                          }
                        >
                          {user.active ? "Deactivate" : "Activate"}
                        </Button>
                      </div>
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

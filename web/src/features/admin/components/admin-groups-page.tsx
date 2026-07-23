import { useQuery } from "@tanstack/react-query"

import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { connectionLabel, formatTimestamp } from "@/features/admin/admin-labels"
import { adminSourceGroupsQueryOptions } from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage, AdminStats } from "@/features/admin/components/admin-page"

export function AdminGroupsPage() {
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

      {rows.length === 0 ? (
        <Card>
          <CardContent>
            <AdminEmpty
              title="No source groups sealed yet"
              description="Groups appear after a connector crawl seals a permissions generation that contains group membership."
            />
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {rows.map((group) => (
            <Card key={group.principalId} className="overflow-hidden">
              <CardHeader>
                <CardTitle className="flex flex-wrap items-center gap-2">
                  <span className="truncate">
                    {group.observedDisplayName?.trim() || group.externalKey || "Unnamed group"}
                  </span>
                  <Badge variant="outline">Generation {group.aclGeneration ?? 0}</Badge>
                </CardTitle>
                <p className="text-sm text-muted-foreground">
                  {connectionLabel(group.sourceSystem, group.sourceConnectionKey)} · sealed{" "}
                  {formatTimestamp(group.sealedAt)}
                </p>
              </CardHeader>
              <CardContent className="px-0 pb-0">
                {group.members?.length ? (
                  <div className="overflow-x-auto border-t">
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
                              {member.appUserId ? (
                                member.appUserName
                              ) : (
                                <Badge variant="destructive">Unmapped</Badge>
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                ) : (
                  <div className="border-t">
                    <AdminEmpty
                      title="No sealed membership"
                      description="The latest generation for this group carries no members."
                    />
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </AdminPage>
  )
}

import { useMutation, useQuery } from "@tanstack/react-query"
import { Search } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { adminUsersQueryOptions } from "@/features/admin/admin-queries"
import { PERMISSION_LABELS } from "@/features/admin/access-permission-labels"
import { AccessDenied, AccessPath } from "@/features/admin/components/access-path"
import { AccessVerdict, type AccessState } from "@/features/admin/components/access-verdict"
import { explainAdminAccessMutation } from "@/lib/hey-api/@tanstack/react-query.gen"

/**
 * A relation only exists on the types the model declares it for. Offering every permission
 * against every type invites questions the engine cannot answer — asking whether someone may
 * administer the organization *on a knowledge space* comes back UNKNOWN, which reads as "we
 * could not tell" when the truth is that the question was never meaningful.
 */
const RESOURCE_TYPES = [
  {
    value: "organization",
    label: "Organization",
    permissions: [
      "can_manage_members",
      "can_manage_sources",
      "can_create_knowledge_space",
      "can_search_knowledge",
      "can_view_directory",
      "can_view_audit",
      "can_curate_graph",
    ],
  },
  {
    value: "knowledge_space",
    label: "Knowledge space",
    permissions: ["can_view", "can_create_asset", "can_publish", "can_manage_acl"],
  },
  {
    value: "knowledge_asset",
    label: "Document",
    permissions: ["can_view", "can_edit", "can_publish", "can_manage_acl"],
  },
  { value: "organizational_unit", label: "Department", permissions: ["can_manage"] },
] as const

/**
 * One access question, answered against the live engine.
 *
 * <p>The scope is always named by the administrator — a user and one resource. There is
 * deliberately no "everything this person can reach": that answer is unbounded, mixes two
 * snapshots, and reads as authoritative when a missing row could mean denied, unevaluated,
 * or merely stale.
 */
export function AccessInspector({ userId }: { userId?: string }) {
  const users = useQuery(adminUsersQueryOptions())
  const explain = useMutation(explainAdminAccessMutation())

  const [subject, setSubject] = useState(userId ?? "")
  const [resourceType, setResourceType] = useState<string>("knowledge_space")
  const [permission, setPermission] = useState("can_view")
  const [resourceId, setResourceId] = useState("")

  const chosen = userId ?? subject
  const offered =
    RESOURCE_TYPES.find((type) => type.value === resourceType)?.permissions ?? []

  /** Changing the resource type can strip the chosen permission, so it moves with it. */
  function chooseResourceType(next: string) {
    setResourceType(next)
    const allowed = RESOURCE_TYPES.find((type) => type.value === next)?.permissions ?? []
    if (!allowed.includes(permission as never)) {
      setPermission(allowed[0] ?? "")
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Check one resource</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          {userId ? null : (
            <Select value={subject} onValueChange={setSubject}>
              <SelectTrigger className="w-[220px]" aria-label="User">
                <SelectValue placeholder="Pick a user" />
              </SelectTrigger>
              <SelectContent>
                {(users.data ?? []).map((user) => (
                  <SelectItem key={user.id} value={user.id!}>
                    {user.name ?? user.email}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}

          <Select value={resourceType} onValueChange={chooseResourceType}>
            <SelectTrigger className="w-[180px]" aria-label="Resource type">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {RESOURCE_TYPES.map((type) => (
                <SelectItem key={type.value} value={type.value}>
                  {type.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={permission} onValueChange={setPermission}>
            <SelectTrigger className="w-[240px]" aria-label="Permission">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {offered.map((key) => (
                <SelectItem key={key} value={key}>
                  {PERMISSION_LABELS[key] ?? key}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Input
            value={resourceId}
            onChange={(event) => setResourceId(event.target.value)}
            placeholder="Resource id"
            aria-label="Resource id"
            className="min-w-[300px] flex-1"
          />

          <Button
            disabled={!chosen || !resourceId || explain.isPending}
            onClick={() =>
              explain.mutate({
                body: { userId: chosen, permission, resourceType, resourceId },
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
            <p className="text-xs text-muted-foreground">Model {explain.data.policyVersion}</p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

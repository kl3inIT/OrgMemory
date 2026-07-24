import { useMutation, useQuery } from "@tanstack/react-query"
import { Search } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { adminUsersQueryOptions } from "@/features/admin/admin-queries"
import { AccessDenied, AccessPath } from "@/features/admin/components/access-path"
import { AccessVerdict, type AccessState } from "@/features/admin/components/access-verdict"
import { explainAdminAccessMutation } from "@/lib/hey-api/@tanstack/react-query.gen"

/**
 * Permissions worth asking about, labelled for someone who did not write the model.
 * The `can_view`/`can_publish`/`can_edit` group is not organization-level, so it only
 * answers against a space or a document.
 */
export const PERMISSION_LABELS: Record<string, string> = {
  can_manage_members: "Administer the organization",
  can_manage_sources: "Manage connected sources",
  can_create_knowledge_space: "Create knowledge spaces",
  can_search_knowledge: "Search knowledge",
  can_view_directory: "View the directory",
  can_view_audit: "View the audit trail",
  can_curate_graph: "Curate the knowledge graph",
  can_view: "View this resource",
  can_publish: "Publish to this resource",
  can_edit: "Edit this resource",
}

const RESOURCE_TYPES = [
  { value: "organization", label: "Organization" },
  { value: "knowledge_space", label: "Knowledge space" },
  { value: "knowledge_asset", label: "Document" },
  { value: "organizational_unit", label: "Department" },
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
  const [permission, setPermission] = useState("can_view")
  const [resourceType, setResourceType] = useState("knowledge_space")
  const [resourceId, setResourceId] = useState("")

  const chosen = userId ?? subject

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

          <Select value={permission} onValueChange={setPermission}>
            <SelectTrigger className="w-[220px]" aria-label="Permission">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(PERMISSION_LABELS).map(([key, label]) => (
                <SelectItem key={key} value={key}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={resourceType} onValueChange={setResourceType}>
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

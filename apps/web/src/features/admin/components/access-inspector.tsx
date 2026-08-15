import { useMutation, useQuery } from "@tanstack/react-query"
import {
  Building2,
  CircleCheck,
  CircleHelp,
  CircleSlash,
  Clock3,
  FileText,
  FolderClosed,
  Search,
  ShieldCheck,
  UserRound,
} from "lucide-react"
import { useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { adminQuery } from "@/features/admin/admin-queries"
import { PERMISSION_LABELS } from "@/features/admin/access-permission-labels"
import type { AccessState } from "@/features/admin/components/access-verdict"
import { cn } from "@/lib/utils"
import {
  explainAdminAccessMutation,
  listAdminUsersOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { ExplainAccessResponse } from "@/lib/hey-api/types.gen"

const RESOURCE_TYPES = [
  {
    value: "organization",
    label: "Organization",
    permissions: [
      "can_manage_members",
      "can_manage_sources",
      "can_manage_ai",
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

const STATE_PRESENTATION = {
  ALLOWED: {
    label: "Allowed",
    icon: CircleCheck,
    className: "border-status-success-border bg-status-success-surface text-status-success-content",
    accent: "bg-status-success-content",
  },
  DENIED: {
    label: "Denied",
    icon: CircleSlash,
    className: "border-status-danger-border bg-status-danger-surface text-status-danger-content",
    accent: "bg-status-danger-content",
  },
  UNKNOWN: {
    label: "Unknown",
    icon: CircleHelp,
    className: "border-status-warning-border bg-status-warning-surface text-status-warning-content",
    accent: "bg-status-warning-content",
  },
} as const

function StateBadge({ state, prominent = false }: { state?: AccessState; prominent?: boolean }) {
  const presentation = STATE_PRESENTATION[state ?? "UNKNOWN"]
  const Icon = presentation.icon
  return (
    <Badge
      variant="outline"
      className={cn(
        "gap-2 font-semibold",
        prominent ? "rounded-lg px-3 py-1.5 text-sm" : "gap-1.5",
        presentation.className,
      )}
    >
      <Icon className={prominent ? "size-4" : "size-3.5"} aria-hidden />
      {presentation.label}
    </Badge>
  )
}

type AccessAssignment = {
  assignment: string
  grantedThrough: string
  appliesTo: string
}

const ORGANIZATION_ROLE_LABELS: Record<string, string> = {
  administrator: "Organization administrator",
  knowledge_reader: "Knowledge reader",
  knowledge_contributor: "Knowledge contributor",
  knowledge_reviewer: "Knowledge reviewer",
  knowledge_curator: "Knowledge curator",
  source_manager: "Source manager",
}

function resolvedStep(
  data: ExplainAccessResponse,
  type: string,
): NonNullable<ExplainAccessResponse["path"]>[number] | undefined {
  return data.path?.find((step) => step.objectType === type && step.objectLabel)
}

function accessAssignments(
  data: ExplainAccessResponse,
  userName: string,
): AccessAssignment[] {
  if (data.relationshipState !== "ALLOWED") return []

  const assignmentStep = [...(data.path ?? [])]
    .reverse()
    .find((step) =>
      step.objectLabel
      && ["organizational_unit", "organization", "group"].includes(step.objectType ?? ""),
    )
  const space = resolvedStep(data, "knowledge_space")?.objectLabel ?? data.resource?.contextLabel
  const appliesTo = space ?? data.resource?.label ?? "Selected resource"

  if (assignmentStep?.objectType === "organizational_unit") {
    return [{
      assignment: assignmentStep.relation === "manager" ? "Department manager" : "Department member",
      grantedThrough: assignmentStep.objectLabel!,
      appliesTo,
    }]
  }
  if (assignmentStep?.objectType === "organization") {
    const role = assignmentStep.relation
      ? ORGANIZATION_ROLE_LABELS[assignmentStep.relation]
      : undefined
    return [{
      assignment: role ? "Assigned role" : "Organization member",
      grantedThrough: role ?? assignmentStep.objectLabel!,
      appliesTo,
    }]
  }
  if (assignmentStep?.objectType === "group") {
    return [{
      assignment: "Group member",
      grantedThrough: assignmentStep.objectLabel!,
      appliesTo,
    }]
  }
  const selectedResourceObject = data.resource
    ? `${data.resource.type}:${data.resource.id}`
    : undefined
  const selectedResourceStep = data.path?.find(
    (step) => selectedResourceObject !== undefined && step.object === selectedResourceObject,
  )
  if (selectedResourceStep?.kind === "DIRECT") {
    return [{
      assignment: "Direct access",
      grantedThrough: userName,
      appliesTo,
    }]
  }
  return []
}

function accessSummary(data: ExplainAccessResponse, userName: string) {
  if (data.evaluationKind === "RELATIONSHIP_ONLY") {
    if (data.state === "ALLOWED") {
      return `${userName} currently has this permission for the selected resource.`
    }
    if (data.state === "DENIED") {
      return `${userName} does not currently have this permission for the selected resource.`
    }
    return `OrgMemory could not resolve this permission for ${userName}.`
  }
  if (data.state === "ALLOWED") {
    return `${userName} can use this document in secure search.`
  }
  if (data.relationshipState === "ALLOWED" && data.contentPolicyState === "DENIED") {
    return `${userName} has a valid assignment, but this document cannot currently be used in secure search.`
  }
  if (data.relationshipState === "DENIED") {
    return `${userName} has no direct or inherited permission for this document.`
  }
  return `OrgMemory could not produce a current access result for ${userName}.`
}

function availability(data: ExplainAccessResponse) {
  if (data.evaluationKind !== "CANONICAL_CONTENT") {
    return {
      label: "Not applicable",
      description: "This permission check does not evaluate document availability.",
      state: "neutral" as const,
    }
  }
  if (data.relationshipState !== "ALLOWED") {
    if (data.relationshipState === "UNKNOWN") {
      return {
        label: "Not checked",
        description: "Permission evaluation did not complete, so document availability was not evaluated.",
        state: "neutral" as const,
      }
    }
    return {
      label: "Not checked",
      description: "Document availability was not evaluated because the user has no current assignment.",
      state: "neutral" as const,
    }
  }
  if (data.contentPolicyState === "ALLOWED") {
    return {
      label: "Available",
      description: "Current document restrictions allow this item to be used in secure search.",
      state: "success" as const,
    }
  }
  if (data.contentPolicyState === "DENIED") {
    return {
      label: "Unavailable",
      description: "Current document restrictions prevent secure search from using this item.",
      state: "danger" as const,
    }
  }
  return {
    label: "Could not verify",
    description: "OrgMemory could not verify the document's current availability.",
    state: "warning" as const,
  }
}

function formatCheckedAt(value?: string) {
  if (!value) return "just now"
  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) return "just now"
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(instant)
}

/** A business-facing current access result; raw authorization diagnostics stay off this surface. */
export function EffectiveAccessDecision({
  data,
  userName = "This user",
}: {
  data: ExplainAccessResponse
  userName?: string
}) {
  const state = (data.state ?? "UNKNOWN") as AccessState
  const resource = data.resource
  const assignments = accessAssignments(data, userName)
  const assignmentUnknown = data.relationshipState === "UNKNOWN"
  const assignmentUnavailable = data.relationshipState === "ALLOWED" && assignments.length === 0
  const documentAvailability = availability(data)
  const presentation = STATE_PRESENTATION[state]
  const StatusIcon = presentation.icon

  return (
    <div className="space-y-4">
      <section
        className={cn(
          "relative overflow-hidden rounded-2xl border p-5 shadow-xs sm:p-6",
          presentation.className,
        )}
        aria-labelledby="current-access-title"
        aria-live="polite"
      >
        <span aria-hidden className={cn("absolute inset-y-0 left-0 w-1", presentation.accent)} />
        <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,0.72fr)] lg:items-center">
          <div className="min-w-0">
            <p id="current-access-title" className="text-sm font-semibold opacity-80">Current access</p>
            <div className="mt-3 flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-xl border border-current/15 bg-background/70">
                <StatusIcon className="size-5" aria-hidden />
              </span>
              <StateBadge state={state} prominent />
            </div>
            <p className="mt-4 max-w-2xl text-base font-medium leading-7 text-foreground">
              {accessSummary(data, userName)}
            </p>
          </div>

          <div className="min-w-0 rounded-xl border border-current/15 bg-background/75 p-4 text-foreground backdrop-blur-sm">
            <div className="flex items-start gap-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border bg-background">
                <FileText className="size-4 text-muted-foreground" aria-hidden />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{resource?.label ?? "Selected resource"}</p>
                <p className="mt-1 flex items-center gap-1.5 text-sm text-muted-foreground">
                  <FolderClosed className="size-3.5" aria-hidden />
                  {resource?.contextLabel ?? "Current scope"}
                </p>
              </div>
              {resource?.classification ? <Badge variant="muted">{resource.classification}</Badge> : null}
            </div>
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border bg-card" aria-labelledby="access-assignment-title">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
          <div>
            <div className="flex items-center gap-2">
              <ShieldCheck className="size-4 text-muted-foreground" aria-hidden />
              <h3 id="access-assignment-title" className="text-sm font-semibold">
                {assignmentUnknown || assignmentUnavailable ? "Assignment source" : "Access granted through"}
              </h3>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              {assignmentUnknown
                ? "OrgMemory could not resolve the assignment path for this check."
                : assignmentUnavailable
                  ? "Access is granted, but this check did not return a readable assignment path."
                : assignments.length > 0
                ? `${userName} receives access through ${assignments.length === 1 ? "one current assignment" : `${assignments.length} current assignments`}.`
                : "No direct or inherited assignment currently grants access."}
            </p>
          </div>
          <Badge variant={assignments.length > 0 ? "success" : "muted"}>
            {assignmentUnknown
              ? "Assignment not resolved"
              : assignmentUnavailable
                ? "Assignment path unavailable"
              : assignments.length > 0
                ? `${assignments.length} assignment`
                : "No current assignment"}
          </Badge>
        </div>

        {assignments.length > 0 ? (
          <>
            <div className="grid gap-3 p-4 md:hidden">
              {assignments.map((assignment) => (
                <article
                  key={`${assignment.assignment}-${assignment.grantedThrough}`}
                  className="rounded-xl border bg-muted/15 p-4"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="flex items-center gap-2 text-sm font-semibold">
                        <UserRound className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                        {assignment.assignment}
                      </p>
                      <p className="mt-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        Granted through
                      </p>
                      <p className="mt-1 flex items-center gap-2 text-sm font-medium">
                        <Building2 className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                        {assignment.grantedThrough}
                      </p>
                    </div>
                    <Badge variant="success">Granted</Badge>
                  </div>
                  <div className="mt-4 border-t pt-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Applies to</p>
                    <p className="mt-1 text-sm text-muted-foreground">{assignment.appliesTo}</p>
                  </div>
                </article>
              ))}
            </div>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[42rem] text-left text-sm">
                <thead className="bg-muted/35 text-xs font-medium text-muted-foreground">
                  <tr>
                    <th className="px-5 py-3 font-medium">Assignment</th>
                    <th className="px-5 py-3 font-medium">Granted through</th>
                    <th className="px-5 py-3 font-medium">Applies to</th>
                    <th className="px-5 py-3 text-right font-medium">Result</th>
                  </tr>
                </thead>
                <tbody>
                  {assignments.map((assignment) => (
                    <tr key={`${assignment.assignment}-${assignment.grantedThrough}`} className="border-t first:border-t-0">
                      <td className="px-5 py-4">
                        <span className="flex items-center gap-2 font-medium">
                          <UserRound className="size-4 text-muted-foreground" aria-hidden />
                          {assignment.assignment}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <span className="flex items-center gap-2">
                          <Building2 className="size-4 text-muted-foreground" aria-hidden />
                          {assignment.grantedThrough}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-muted-foreground">{assignment.appliesTo}</td>
                      <td className="px-5 py-4 text-right">
                        <Badge variant="success">Granted</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        ) : assignmentUnknown ? (
          <div className="flex items-start gap-3 px-5 py-5 text-sm text-muted-foreground">
            <CircleHelp className="mt-0.5 size-4 shrink-0" aria-hidden />
            Run the check again. No assignment change should be made from an unresolved result.
          </div>
        ) : assignmentUnavailable ? (
          <div className="flex items-start gap-3 px-5 py-5 text-sm text-muted-foreground">
            <CircleHelp className="mt-0.5 size-4 shrink-0" aria-hidden />
            This result proves access, but not whether its source is direct or inherited.
          </div>
        ) : (
          <div className="flex items-start gap-3 px-5 py-5 text-sm text-muted-foreground">
            <CircleSlash className="mt-0.5 size-4 shrink-0" aria-hidden />
            Add direct access or include this user through an eligible department or organization.
          </div>
        )}
      </section>

      <div className="grid gap-4 md:grid-cols-2">
        <section className="rounded-xl border bg-card p-5" aria-labelledby="document-availability-title">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <FileText className="size-4 text-muted-foreground" aria-hidden />
                <h3 id="document-availability-title" className="text-sm font-semibold">Document availability</h3>
              </div>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                {documentAvailability.description}
              </p>
            </div>
            <Badge
              variant={documentAvailability.state === "success"
                ? "success"
                : documentAvailability.state === "danger"
                  ? "destructive"
                  : "muted"}
            >
              {documentAvailability.label}
            </Badge>
          </div>
        </section>

        <section className="rounded-xl border bg-card p-5" aria-labelledby="permission-check-title">
          <div className="flex items-start gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border bg-muted/30">
              <Clock3 className="size-4 text-muted-foreground" aria-hidden />
            </span>
            <div>
              <h3 id="permission-check-title" className="text-sm font-semibold">Checked against current permissions</h3>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                Evaluated {formatCheckedAt(data.evaluatedAt)}. Run the check again after a permission change to confirm its current effect.
              </p>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

export function AccessInspector({ userId }: { userId?: string }) {
  const users = useQuery(adminQuery(listAdminUsersOptions()))
  const explain = useMutation(explainAdminAccessMutation())

  const [subject, setSubject] = useState(userId ?? "")
  const [resourceType, setResourceType] = useState<string>("knowledge_asset")
  const [permission, setPermission] = useState("can_view")
  const [resourceId, setResourceId] = useState("")

  const chosen = userId ?? subject
  const chosenUser = users.data?.find((user) => user.id === chosen)
  const isDocumentUseCheck = resourceType === "knowledge_asset" && permission === "can_view"
  const chosenUserName = chosenUser?.name ?? chosenUser?.email ?? "This user"
  const offered = RESOURCE_TYPES.find((type) => type.value === resourceType)?.permissions ?? []

  function chooseResourceType(next: string) {
    setResourceType(next)
    const allowed = RESOURCE_TYPES.find((type) => type.value === next)?.permissions ?? []
    if (!allowed.includes(permission as never)) setPermission(allowed[0] ?? "")
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{isDocumentUseCheck ? "Check document access" : "Check resource permission"}</CardTitle>
        <CardDescription>
          {isDocumentUseCheck
            ? "See whether this user can use a document in secure search and where that access comes from."
            : "See whether this user currently has the selected permission and where it comes from."}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid items-end gap-3 md:grid-cols-2 xl:grid-cols-[minmax(12rem,0.8fr)_minmax(12rem,0.8fr)_minmax(15rem,1fr)_minmax(18rem,1.4fr)_auto]">
          {userId ? null : (
            <div className="space-y-2">
              <Label>User</Label>
              <Select value={subject} onValueChange={setSubject}>
                <SelectTrigger className="w-full" aria-label="User">
                  <SelectValue placeholder="Pick a user" />
                </SelectTrigger>
                <SelectContent>
                  {(users.data ?? []).map((user) => (
                    <SelectItem key={user.id} value={user.id!}>{user.name ?? user.email}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          <div className="space-y-2">
            <Label>Resource type</Label>
            <Select value={resourceType} onValueChange={chooseResourceType}>
              <SelectTrigger className="w-full" aria-label="Resource type"><SelectValue /></SelectTrigger>
              <SelectContent>
                {RESOURCE_TYPES.map((type) => (
                  <SelectItem key={type.value} value={type.value}>{type.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label>Use</Label>
            <Select value={permission} onValueChange={setPermission}>
              <SelectTrigger className="w-full" aria-label="Use"><SelectValue /></SelectTrigger>
              <SelectContent>
                {offered.map((key) => (
                  <SelectItem key={key} value={key}>{PERMISSION_LABELS[key] ?? key}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2 md:col-span-2 xl:col-span-1">
            <Label htmlFor="access-resource-id">
              {isDocumentUseCheck ? "Document or source" : "Resource identifier"}
            </Label>
            <Input
              id="access-resource-id"
              value={resourceId}
              onChange={(event) => setResourceId(event.target.value)}
              placeholder="Paste the document or source ID"
            />
          </div>

          <Button
            className="md:col-span-2 xl:col-span-1"
            disabled={!chosen || !resourceId || explain.isPending}
            onClick={() => explain.mutate({ body: { userId: chosen, permission, resourceType, resourceId } })}
          >
            <Search className="size-4" aria-hidden />
            {explain.isPending ? "Checking…" : "Check current access"}
          </Button>
        </div>

        {explain.isError ? (
          <p role="alert" className="rounded-lg border border-status-danger-border bg-status-danger-surface p-3 text-sm text-status-danger-content">
            This item could not be checked. Confirm it belongs to this organization and try again.
          </p>
        ) : null}

        {explain.data ? (
          <EffectiveAccessDecision data={explain.data} userName={chosenUserName} />
        ) : null}
      </CardContent>
    </Card>
  )
}

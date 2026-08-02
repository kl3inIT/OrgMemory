import { useMutation, useQuery } from "@tanstack/react-query"
import {
  ChevronDown,
  CircleCheck,
  CircleHelp,
  CircleSlash,
  FileText,
  FolderClosed,
  Info,
  ListChecks,
  Search,
  ShieldCheck,
} from "lucide-react"
import { useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
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
  },
  DENIED: {
    label: "Denied",
    icon: CircleSlash,
    className: "border-status-danger-border bg-status-danger-surface text-status-danger-content",
  },
  UNKNOWN: {
    label: "Unknown",
    icon: CircleHelp,
    className: "border-status-warning-border bg-status-warning-surface text-status-warning-content",
  },
} as const

function StateBadge({ state }: { state?: AccessState }) {
  const presentation = STATE_PRESENTATION[state ?? "UNKNOWN"]
  const Icon = presentation.icon
  return (
    <Badge variant="outline" className={cn("gap-1.5", presentation.className)}>
      <Icon className="size-3.5" aria-hidden />
      {presentation.label}
    </Badge>
  )
}

function DecisionGate({
  title,
  description,
  state,
  last = false,
}: {
  title: string
  description: string
  state?: AccessState
  last?: boolean
}) {
  const Icon = STATE_PRESENTATION[state ?? "UNKNOWN"].icon
  return (
    <li className="relative grid grid-cols-[2rem_1fr_auto] gap-x-3 pb-6 last:pb-0">
      {last ? null : (
        <span aria-hidden className="absolute left-4 top-8 h-[calc(100%-2rem)] w-px bg-border" />
      )}
      <span className="relative z-10 flex size-8 items-center justify-center rounded-full border bg-background">
        <Icon className="size-4 text-muted-foreground" aria-hidden />
      </span>
      <div className="min-w-0 pt-0.5">
        <p className="text-sm font-medium">{title}</p>
        <p className="mt-1 text-sm leading-5 text-muted-foreground">{description}</p>
      </div>
      <StateBadge state={state} />
    </li>
  )
}

function finalExplanation(data: ExplainAccessResponse) {
  if (data.state === "ALLOWED") {
    return "The relationship and every current content-policy gate allow this document to be retrieved."
  }
  if (data.relationshipState === "ALLOWED" && data.contentPolicyState === "DENIED") {
    return "OpenFGA grants a relationship to this document, but the current source ACL or content policy prevents retrieval."
  }
  if (data.relationshipState === "DENIED") {
    return "No OpenFGA relationship currently grants access. Content policy was not evaluated."
  }
  return "The system cannot produce a current answer. Treat this as unresolved, not as permission."
}

/** A canonical content verdict presented as a small decision brief, not a raw tuple dump. */
export function EffectiveAccessDecision({ data }: { data: ExplainAccessResponse }) {
  const state = (data.state ?? "UNKNOWN") as AccessState
  const resource = data.resource
  const canonical = data.evaluationKind === "CANONICAL_CONTENT"

  if (!canonical) {
    return (
      <div className="rounded-xl border bg-muted/20 p-5">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium">Relationship decision</p>
            <p className="mt-1 text-sm text-muted-foreground">
              This resource uses an OpenFGA relationship-only check.
            </p>
          </div>
          <StateBadge state={state} />
        </div>
        <Separator className="my-5" />
        <p className="text-sm leading-6 text-muted-foreground">
          {state === "ALLOWED"
            ? "OpenFGA currently grants this permission. This result does not evaluate document content policy."
            : state === "DENIED"
              ? "No OpenFGA relationship currently grants this permission."
              : "The authorization engine did not produce a current answer. Treat this as unresolved."}
        </p>
        <TechnicalDetails data={data} />
      </div>
    )
  }

  return (
    <div className="grid overflow-hidden rounded-xl border bg-card lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
      <section className="border-b p-5 sm:p-6 lg:border-b-0 lg:border-r" aria-labelledby="decision-brief-title">
        <div className="flex items-center gap-2 text-muted-foreground">
          <ListChecks className="size-4" aria-hidden />
          <p id="decision-brief-title" className="text-sm font-medium">Decision brief</p>
        </div>

        <div className="mt-5 flex items-start gap-3 rounded-lg border bg-muted/25 p-4">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border bg-background">
            <FileText className="size-4 text-muted-foreground" aria-hidden />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">{resource?.label ?? "Document"}</p>
            <p className="mt-1 flex items-center gap-1.5 text-sm text-muted-foreground">
              <FolderClosed className="size-3.5" aria-hidden />
              {resource?.contextLabel ?? "Knowledge space"}
            </p>
          </div>
          {resource?.classification ? (
            <Badge variant="muted">{resource.classification}</Badge>
          ) : null}
        </div>

        <div className="py-7">
          <p className="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">
            Effective decision
          </p>
          <div className="mt-3 flex items-center gap-3">
            <StateBadge state={state} />
            <span className="text-sm text-muted-foreground">for content retrieval</span>
          </div>
          <p className="mt-4 max-w-xl text-sm leading-6 text-muted-foreground">
            {finalExplanation(data)}
          </p>
        </div>

        <div className="flex gap-3 rounded-lg border border-status-info-border bg-status-info-surface p-4 text-status-info-content">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
          <div>
            <p className="text-sm font-medium">Space grants never override source restrictions</p>
            <p className="mt-1 text-sm leading-5 opacity-90">
              A Space can make a user eligible in OpenFGA. The current Source ACL,
              classification, publication, and lifecycle checks remain mandatory.
            </p>
          </div>
        </div>

        <TechnicalDetails data={data} />
      </section>

      <section className="p-5 sm:p-6" aria-labelledby="decision-path-title">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-muted-foreground" aria-hidden />
          <h3 id="decision-path-title" className="text-sm font-medium">How the decision was made</h3>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          Final access is the intersection of the relationship and canonical content gates.
        </p>

        <ol className="mt-7">
          <DecisionGate
            title="OpenFGA relationship"
            description="Checks direct and inherited grants from organization, department, role, Space, and user relationships."
            state={data.relationshipState as AccessState | undefined}
          />
          <DecisionGate
            title="Source ACL and content policy"
            description="Checks the current source-owned ACL together with classification, publication, lifecycle, and current-version evidence."
            state={data.contentPolicyState as AccessState | undefined}
          />
          <DecisionGate
            title="Final content access"
            description="Allowed only when every mandatory gate above is allowed. No grant has override priority."
            state={state}
            last
          />
        </ol>
      </section>
    </div>
  )
}

function TechnicalDetails({ data }: { data: ExplainAccessResponse }) {
  return (
    <Collapsible className="mt-5">
      <CollapsibleTrigger className="group flex w-full items-center justify-between rounded-md py-2 text-left text-xs font-medium text-muted-foreground hover:text-foreground">
        Technical details
        <ChevronDown className="size-3.5 transition-transform group-data-[state=open]:rotate-180" aria-hidden />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <dl className="grid gap-2 rounded-lg border bg-muted/25 p-3 font-mono text-xs text-muted-foreground">
          <div className="grid gap-1 sm:grid-cols-[9rem_1fr]">
            <dt>Resource ID</dt>
            <dd className="break-all text-foreground">{data.resource?.id ?? "Not returned"}</dd>
          </div>
          <div className="grid gap-1 sm:grid-cols-[9rem_1fr]">
            <dt>Authorization model</dt>
            <dd className="break-all text-foreground">{data.policyVersion ?? "Unknown"}</dd>
          </div>
          <div className="grid gap-1 sm:grid-cols-[9rem_1fr]">
            <dt>Relationship reason</dt>
            <dd className="break-all text-foreground">{data.relationshipReasonCode ?? data.reasonCode}</dd>
          </div>
          <div className="grid gap-1 sm:grid-cols-[9rem_1fr]">
            <dt>Content reason</dt>
            <dd className="break-all text-foreground">{data.contentPolicyReasonCode ?? "Not applicable"}</dd>
          </div>
        </dl>
      </CollapsibleContent>
    </Collapsible>
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
  const offered = RESOURCE_TYPES.find((type) => type.value === resourceType)?.permissions ?? []

  function chooseResourceType(next: string) {
    setResourceType(next)
    const allowed = RESOURCE_TYPES.find((type) => type.value === next)?.permissions ?? []
    if (!allowed.includes(permission as never)) setPermission(allowed[0] ?? "")
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Effective access inspector</CardTitle>
        <CardDescription>
          Check one exact resource against live relationships and, for document viewing, the canonical retrieval policy.
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
            <Label>Permission</Label>
            <Select value={permission} onValueChange={setPermission}>
              <SelectTrigger className="w-full" aria-label="Permission"><SelectValue /></SelectTrigger>
              <SelectContent>
                {offered.map((key) => (
                  <SelectItem key={key} value={key}>{PERMISSION_LABELS[key] ?? key}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2 md:col-span-2 xl:col-span-1">
            <Label htmlFor="access-resource-id">Exact resource ID</Label>
            <Input
              id="access-resource-id"
              value={resourceId}
              onChange={(event) => setResourceId(event.target.value)}
              placeholder="Paste an ID to inspect"
              className="font-mono text-xs"
            />
          </div>

          <Button
            className="md:col-span-2 xl:col-span-1"
            disabled={!chosen || !resourceId || explain.isPending}
            onClick={() => explain.mutate({ body: { userId: chosen, permission, resourceType, resourceId } })}
          >
            <Search className="size-4" aria-hidden />
            {explain.isPending ? "Checking…" : "Check access"}
          </Button>
        </div>

        {explain.isError ? (
          <p role="alert" className="rounded-lg border border-status-danger-border bg-status-danger-surface p-3 text-sm text-status-danger-content">
            This resource could not be checked. Confirm the ID belongs to this organization and that you can view the audit trail.
          </p>
        ) : null}

        {explain.data ? <EffectiveAccessDecision data={explain.data} /> : null}
      </CardContent>
    </Card>
  )
}

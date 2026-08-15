import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Building2, ChevronDown, FolderPlus, LockKeyhole, Trash2, Users, X } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
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
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import {
  adminKnowledgeSpaceGrantOptionsQueryOptions,
  adminQuery,
  invalidateAdminData,
  organizationContextQueryOptions,
} from "@/features/admin/admin-queries"
import {
  audienceLabel,
  isBuiltInAudience,
  organizationRole,
  parseSpaceSubject,
  roleLabel,
  subjectLabel,
} from "@/features/admin/admin-space-audience"
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import {
  createAdminKnowledgeSpaceMutation,
  deleteAdminKnowledgeSpaceMutation,
  grantAdminKnowledgeSpaceAccessMutation,
  listAdminKnowledgeSpacesOptions,
  listAdminUsersOptions,
  revokeAdminKnowledgeSpaceAccessMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminKnowledgeSpaceResponse } from "@/lib/hey-api"
import { cn } from "@/lib/utils"

/** Operational grants are intentionally independent from the viewer audience. */
const RELATIONS = [
  { value: "viewer", label: "Can read", hint: "Reads what the source system also allows" },
  { value: "contributor", label: "Can add", hint: "Adds knowledge to this space" },
  { value: "reviewer", label: "Can approve", hint: "Decides what counts as approved here" },
  { value: "administrator", label: "Can administer", hint: "Changes who has access" },
] as const

const SUBJECT_LABELS: Record<string, string> = {
  ORGANIZATION: "Everyone in the organization",
  DEPARTMENT: "Everyone in a department",
  DEPARTMENT_MANAGERS: "Whoever manages a department",
  ROLE: "A role",
  USER: "One person",
}

type SubjectKind = "ORGANIZATION" | "DEPARTMENT" | "DEPARTMENT_MANAGERS" | "ROLE" | "USER"
type AudienceMode = "ORGANIZATION" | "DEPARTMENT" | "RESTRICTED_CUSTOM"

const AUDIENCE_MODES = [
  {
    value: "ORGANIZATION",
    label: "Organization",
    description: "Every current member is eligible. Source permissions still limit each document.",
    icon: Building2,
  },
  {
    value: "DEPARTMENT",
    label: "Department",
    description: "Only current members of one department are eligible.",
    icon: Users,
  },
  {
    value: "RESTRICTED_CUSTOM",
    label: "Restricted custom",
    description: "Starts closed. Add approved people or departments as readers.",
    icon: LockKeyhole,
  },
] as const

/** Which shapes name a department, and so need one picked. */
const DEPARTMENT_KINDS: SubjectKind[] = ["DEPARTMENT", "DEPARTMENT_MANAGERS"]

type Directory = {
  departments: Map<string, string>
  users: Map<string, string>
  /** Relation to the subject shapes the authorization model accepts for it. */
  grantOptions: Map<string, SubjectKind[]>
  /** Relation to the named organization roles the model accepts for it. */
  grantRoles: Map<string, string[]>
}

function relationLabel(relation: string) {
  return RELATIONS.find((entry) => entry.value === relation)?.label ?? relation
}

/**
 * The subject shape a revoke request would carry, or null when the write API cannot name it.
 *
 * <p>Bootstrap grants use references this form does not offer — {@code organization#knowledge_
 * contributor} among them. Mapping those onto the nearest shape would delete a different tuple
 * than the one shown, and OpenFGA ignores a missing delete, so the row would report success and
 * change nothing. A grant this cannot express gets no revoke control instead.
 */
function revocationFor(subject: string): { kind: SubjectKind; subjectId?: string; role?: string } | null {
  const { type, id, relation } = parseSpaceSubject(subject)

  if (type === "user" && relation === undefined) return { kind: "USER", subjectId: id }
  if (type === "role" && relation === "assignee") return { kind: "ROLE", role: id }
  if (type === "organization" && relation === "member") return { kind: "ORGANIZATION" }
  const namedRole = type === "organization" ? organizationRole(relation) : undefined
  if (namedRole) {
    return { kind: "ROLE", role: namedRole }
  }
  if (type === "organizational_unit" && relation === "member") {
    return { kind: "DEPARTMENT", subjectId: id }
  }
  if (type === "organizational_unit" && relation === "manager") {
    return { kind: "DEPARTMENT_MANAGERS", subjectId: id }
  }
  return null
}

function SpaceRow({ space, directory }: { space: AdminKnowledgeSpaceResponse; directory: Directory }) {
  const queryClient = useQueryClient()
  const grant = useMutation(grantAdminKnowledgeSpaceAccessMutation())
  const revoke = useMutation(revokeAdminKnowledgeSpaceAccessMutation())
  const remove = useMutation(deleteAdminKnowledgeSpaceMutation())
  const [deleteOpen, setDeleteOpen] = useState(false)

  const audienceIsManaged = space.audienceMode !== "RESTRICTED_CUSTOM"
  const [relation, setRelation] = useState<string>(audienceIsManaged ? "contributor" : "viewer")
  const [kind, setKind] = useState<SubjectKind>("DEPARTMENT")
  const [subjectId, setSubjectId] = useState("")
  const [role, setRole] = useState("")

  // The relation decides which subjects are even expressible, so it drives the second control
  // rather than the two being chosen independently and reconciled by a refusal.
  const allowedKinds = directory.grantOptions.get(relation) ?? []
  const allowedRoles = directory.grantRoles.get(relation) ?? []
  const availableRelations = audienceIsManaged
    ? RELATIONS.filter((entry) => entry.value !== "viewer")
    : RELATIONS
  const grants = space.grants ?? []
  const readers = grants.filter(
    (entry) =>
      entry.relation === "viewer" &&
      entry.effective !== false &&
      (space.audienceMode === "RESTRICTED_CUSTOM" ||
        isBuiltInAudience(space, entry.subject ?? "")),
  )
  const builtInAudiencePresent = grants.some(
    (entry) => entry.relation === "viewer" && isBuiltInAudience(space, entry.subject ?? ""),
  )
  const ineffectiveViewerPresent = grants.some(
    (entry) => entry.relation === "viewer" && entry.effective === false,
  )
  const policyDrift =
    space.grantsComplete !== false &&
    (ineffectiveViewerPresent ||
      (audienceIsManaged &&
        (!builtInAudiencePresent ||
          grants.some(
            (entry) =>
              entry.relation === "viewer" && !isBuiltInAudience(space, entry.subject ?? ""),
          ))))

  async function addGrant() {
    try {
      await grant.mutateAsync({
        path: { knowledgeSpaceId: space.id! },
        body: {
          relation,
          kind,
          subjectId:
            DEPARTMENT_KINDS.includes(kind) || kind === "USER" ? subjectId : undefined,
          role: kind === "ROLE" ? role : undefined,
        },
      })
      setSubjectId("")
      setRole("")
      await invalidateAdminData(queryClient)
      toast.success(`Access to ${space.name} was granted`)
    } catch {
      toast.error("That grant could not be applied")
    }
  }

  async function removeGrant(
    grantRelation: string,
    removal: NonNullable<ReturnType<typeof revocationFor>>,
  ) {
    try {
      await revoke.mutateAsync({
        path: { knowledgeSpaceId: space.id! },
        query: { relation: grantRelation, ...removal },
      })
      await invalidateAdminData(queryClient)
      toast.success(`Access to ${space.name} was revoked`)
    } catch {
      toast.error("That grant could not be revoked")
    }
  }

  async function deleteSpace() {
    try {
      await remove.mutateAsync({ path: { knowledgeSpaceId: space.id! } })
      setDeleteOpen(false)
      await invalidateAdminData(queryClient)
      toast.success(`${space.name} was deleted`, {
        description: "Retained evidence stays governed by the organization retention policy.",
      })
    } catch {
      toast.error("That Space could not be deleted")
    }
  }

  const subjectIsChosen =
    kind === "ORGANIZATION" || (kind === "ROLE" ? role.trim().length > 0 : subjectId.length > 0)

  /** Keeps the subject valid for the relation, so the pair can never be one the model refuses. */
  function chooseRelation(next: string) {
    setRelation(next)
    const permitted = directory.grantOptions.get(next) ?? []
    if (!permitted.includes(kind)) {
      setKind(permitted[0] ?? "USER")
      setSubjectId("")
      setRole("")
    }
  }

  return (
    <Collapsible className="group border-b border-border-subtle last:border-b-0">
      <div className="flex items-center">
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring"
          >
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{space.name}</span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground">
              {audienceLabel(space, directory)} · Policy v{space.audienceVersion ?? 1}
            </span>
          </span>
          <span className="hidden items-center gap-2 sm:flex">
            {space.grantsComplete === false ? (
              <Badge variant="warning">Partial list</Badge>
            ) : policyDrift ? (
              <Badge variant="warning">Audience policy drift</Badge>
            ) : readers.length === 0 ? (
              <Badge variant="warning">Closed audience</Badge>
            ) : (
              <Badge variant="secondary">{audienceLabel(space, directory)}</Badge>
            )}
          </span>
          <ChevronDown
            className="size-4 shrink-0 text-muted-foreground transition-transform group-data-[state=open]:rotate-180"
            aria-hidden="true"
          />
          </button>
        </CollapsibleTrigger>
        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="mr-3 text-muted-foreground hover:text-destructive"
          aria-label={`Delete ${space.name}`}
          onClick={() => setDeleteOpen(true)}
        >
          <Trash2 className="size-4" aria-hidden="true" />
        </Button>
      </div>

      <CollapsibleContent className="space-y-3 border-t border-border-subtle bg-surface-subtle/40 px-4 py-3">
        {space.grantsComplete === false ? (
          <p className="text-xs text-muted-foreground">
            The relationship store could not be read in full, so this is part of who has access, not
            all of it.
          </p>
        ) : null}

        {grants.length === 0 ? (
          <p className="text-sm text-muted-foreground">No grants are stored against this space.</p>
        ) : (
          <ul className="space-y-1.5">
            {grants.map((entry) => {
              const removal = revocationFor(entry.subject!)
              const policyManaged =
                entry.relation === "viewer" && isBuiltInAudience(space, entry.subject!)
              const conflictsWithPolicy =
                entry.relation === "viewer" && entry.effective === false
              return (
                <li
                  key={`${entry.relation}:${entry.subject}`}
                  className="flex items-center gap-2 rounded-lg bg-surface-raised px-3 py-2"
                >
                  <Badge variant="outline">{relationLabel(entry.relation!)}</Badge>
                  <span className="min-w-0 flex-1 truncate text-sm">
                    {subjectLabel(entry.subject!, directory)}
                  </span>
                  {conflictsWithPolicy ? (
                    <span className="flex shrink-0 items-center gap-1 text-xs text-warning-foreground">
                      Not effective · remove drift
                      {removal ? (
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          aria-label={`Remove ineffective ${relationLabel(entry.relation!)} grant for ${subjectLabel(entry.subject!, directory)}`}
                          disabled={revoke.isPending}
                          onClick={() => void removeGrant(entry.relation!, removal)}
                        >
                          <X className="size-4" aria-hidden="true" />
                        </Button>
                      ) : null}
                    </span>
                  ) : removal && !policyManaged ? (
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      aria-label={`Revoke ${relationLabel(entry.relation!)} from ${subjectLabel(entry.subject!, directory)}`}
                      disabled={revoke.isPending}
                      onClick={() => void removeGrant(entry.relation!, removal)}
                    >
                      <X className="size-4" aria-hidden="true" />
                    </Button>
                  ) : (
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {policyManaged ? "Managed by audience policy" : "Managed outside this form"}
                    </span>
                  )}
                </li>
              )
            })}
          </ul>
        )}

        <form
          className="flex flex-wrap items-end gap-2 border-t border-border-subtle pt-3"
          onSubmit={(event) => {
            event.preventDefault()
            void addGrant()
          }}
        >
          <Select value={relation} onValueChange={chooseRelation}>
            <SelectTrigger className="w-44" aria-label="What this grant allows">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {availableRelations.map((entry) => (
                <SelectItem key={entry.value} value={entry.value}>
                  {entry.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select
            value={kind}
            onValueChange={(next: string) => {
              setKind(next as SubjectKind)
              setSubjectId("")
              setRole("")
            }}
          >
            <SelectTrigger className="w-64" aria-label="Who this grant is for">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {allowedKinds.map((entry) => (
                <SelectItem key={entry} value={entry}>
                  {SUBJECT_LABELS[entry] ?? entry}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {DEPARTMENT_KINDS.includes(kind) ? (
            <Select value={subjectId} onValueChange={setSubjectId}>
              <SelectTrigger className="w-56" aria-label="Department">
                <SelectValue placeholder="Choose a department" />
              </SelectTrigger>
              <SelectContent>
                {[...directory.departments].map(([id, name]) => (
                  <SelectItem key={id} value={id}>
                    {name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null}

          {kind === "USER" ? (
            <Select value={subjectId} onValueChange={setSubjectId}>
              <SelectTrigger className="w-56" aria-label="Person">
                <SelectValue placeholder="Choose a person" />
              </SelectTrigger>
              <SelectContent>
                {[...directory.users].map(([id, name]) => (
                  <SelectItem key={id} value={id}>
                    {name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null}

          {kind === "ROLE" ? (
            <Select value={role} onValueChange={setRole}>
              <SelectTrigger className="w-56" aria-label="Organization role">
                <SelectValue placeholder="Choose a role" />
              </SelectTrigger>
              <SelectContent>
                {allowedRoles.map((entry) => (
                  <SelectItem key={entry} value={entry}>
                    {roleLabel(entry)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null}

          <Button type="submit" variant="outline" disabled={!subjectIsChosen || grant.isPending}>
            Grant
          </Button>
        </form>
      </CollapsibleContent>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this Knowledge Space?</AlertDialogTitle>
            <AlertDialogDescription>
              “{space.name}” will disappear from administration, uploads, and retrieval. Retained
              documents, permissions, and audit evidence continue to follow the organization
              retention policy.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>Keep Space</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={remove.isPending}
              onClick={(event) => {
                event.preventDefault()
                void deleteSpace()
              }}
            >
              {remove.isPending ? "Deleting…" : "Delete Space"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Collapsible>
  )
}

/**
 * Knowledge Spaces, and who each one is for.
 *
 * <p>A space is where the organization records who a body of knowledge serves and who answers for
 * it. Until this screen existed both answers lived in a Flyway migration and a bootstrap tuple
 * file, which meant the concept the product is organized around was the one thing it could not
 * administer.
 */
export function AdminSpacesPage() {
  const queryClient = useQueryClient()
  const spaces = useQuery(adminQuery(listAdminKnowledgeSpacesOptions()))
  const context = useQuery(organizationContextQueryOptions())
  const users = useQuery(adminQuery(listAdminUsersOptions()))
  const grantOptions = useQuery(adminKnowledgeSpaceGrantOptionsQueryOptions())
  const create = useMutation(createAdminKnowledgeSpaceMutation())

  const [name, setName] = useState("")
  const [audienceMode, setAudienceMode] = useState<AudienceMode>("DEPARTMENT")
  const [departmentId, setDepartmentId] = useState("")

  if (spaces.isPending) {
    return <LoadingState label="Loading Knowledge Spaces" className="min-h-full flex-1" />
  }

  if (spaces.isError) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Knowledge Spaces could not be loaded"
          description="Creating and granting a space requires organization administrator permission."
          error={spaces.error}
          onRetry={() => spaces.refetch()}
        />
      </div>
    )
  }

  const directory: Directory = {
    departments: new Map(
      (context.data?.departments ?? []).map((department) => [department.id!, department.name!]),
    ),
    users: new Map(
      (users.data ?? []).map((user) => [user.id!, user.name || user.email || "Unnamed person"]),
    ),
    grantOptions: new Map(
      (grantOptions.data ?? []).map((option) => [
        option.relation!,
        (option.kinds ?? []) as SubjectKind[],
      ]),
    ),
    grantRoles: new Map(
      (grantOptions.data ?? []).map((option) => [option.relation!, option.roles ?? []]),
    ),
  }

  const rows = spaces.data ?? []
  const withoutReaders = rows.filter(
    (space) =>
      space.audienceMode === "RESTRICTED_CUSTOM" &&
      (space.grants ?? []).filter(
        (grant) => grant.relation === "viewer" && grant.effective !== false,
      ).length === 0,
  ).length

  async function createSpace() {
    try {
      const created = await create.mutateAsync({
        body: {
          name,
          audienceMode,
          departmentId: audienceMode === "DEPARTMENT" ? departmentId : undefined,
        },
      })
      setName("")
      setAudienceMode("DEPARTMENT")
      setDepartmentId("")
      await invalidateAdminData(queryClient)
      toast.success(`${created.name} was created`, {
        description:
          audienceMode === "RESTRICTED_CUSTOM"
            ? "This Space starts closed. Add explicit viewers below."
            : "Its built-in audience is active; source permissions still cap every document.",
      })
    } catch {
      toast.error("That space could not be created", {
        description: "A space with the same derived key may already exist.",
      })
    }
  }

  return (
    <AdminPage
      title="Knowledge Spaces"
      description="Choose one durable audience promise for each body of knowledge. A Space makes someone eligible; source permissions still decide which documents that person may read."
    >
      <AdminStats
        stats={[
          { label: "Spaces", value: rows.length },
          {
            label: "Department audiences",
            value: rows.filter((space) => space.audienceMode === "DEPARTMENT").length,
          },
          {
            label: "Closed custom audiences",
            value: withoutReaders,
            hint: "No viewer has been approved yet",
          },
          {
            label: "Grantable role policies",
            value: new Set((grantOptions.data ?? []).flatMap((option) => option.roles ?? [])).size,
          },
        ]}
      />

      <AdminSection title="Create a space">
        <form
          className="space-y-5 p-4"
          onSubmit={(event) => {
            event.preventDefault()
            void createSpace()
          }}
        >
          <div className="flex flex-wrap items-end gap-3">
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Sales Knowledge"
              aria-label="Knowledge Space name"
              className="min-w-[260px] flex-1"
            />
            {audienceMode === "DEPARTMENT" ? (
              <Select value={departmentId} onValueChange={setDepartmentId}>
                <SelectTrigger className="w-64" aria-label="Owning department">
                  <SelectValue placeholder="Choose the department" />
                </SelectTrigger>
                <SelectContent>
                  {[...directory.departments].map(([id, label]) => (
                    <SelectItem key={id} value={id}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : null}
          </div>

          <div className="grid gap-2 md:grid-cols-3" role="radiogroup" aria-label="Space audience">
            {AUDIENCE_MODES.map((mode) => {
              const Icon = mode.icon
              const selected = audienceMode === mode.value
              return (
                <button
                  key={mode.value}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  className={cn(
                    "rounded-xl border p-3 text-left outline-none transition-colors",
                    "focus-visible:ring-2 focus-visible:ring-focus-ring",
                    selected
                      ? "border-primary bg-primary/5"
                      : "border-border-subtle bg-surface-raised hover:bg-surface-subtle",
                  )}
                  onClick={() => {
                    setAudienceMode(mode.value)
                    if (mode.value !== "DEPARTMENT") setDepartmentId("")
                  }}
                >
                  <span className="flex items-center gap-2 text-sm font-medium">
                    <Icon className="size-4" aria-hidden="true" />
                    {mode.label}
                  </span>
                  <span className="mt-1.5 block text-xs leading-relaxed text-muted-foreground">
                    {mode.description}
                  </span>
                </button>
              )
            })}
          </div>

          <div className="flex justify-end">
            <Button
              type="submit"
              disabled={
                !name.trim() ||
                create.isPending ||
                (audienceMode === "DEPARTMENT" && !departmentId)
              }
            >
              <FolderPlus className="size-4" aria-hidden="true" />
              Create Space
            </Button>
          </div>
        </form>
        <p className="border-t border-border-subtle px-4 py-3 text-xs text-muted-foreground">
          Audience mode is fixed at creation. Changing it later requires a governed impact review;
          ordinary grants cannot silently widen an organization or department audience.
        </p>
      </AdminSection>

      <AdminSection title="Spaces and who can reach them">
        {rows.length === 0 ? (
          <AdminEmpty
            title="No Knowledge Spaces yet"
            description="Create one above. A connector cannot be pointed anywhere until at least one space exists."
          />
        ) : (
          rows.map((space) => <SpaceRow key={space.id} space={space} directory={directory} />)
        )}
      </AdminSection>
    </AdminPage>
  )
}

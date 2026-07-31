import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronDown, FolderPlus, X } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
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
import { AdminEmpty, AdminPage, AdminSection, AdminStats } from "@/features/admin/components/admin-page"
import {
  createAdminKnowledgeSpaceMutation,
  grantAdminKnowledgeSpaceAccessMutation,
  listAdminKnowledgeSpacesOptions,
  listAdminRolesOptions,
  listAdminUsersOptions,
  revokeAdminKnowledgeSpaceAccessMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { AdminKnowledgeSpaceResponse } from "@/lib/hey-api"

/** What each grant lets its holder do. Ordered from least to most, which is how they nest. */
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

/** Which shapes name a department, and so need one picked. */
const DEPARTMENT_KINDS: SubjectKind[] = ["DEPARTMENT", "DEPARTMENT_MANAGERS"]

type Directory = {
  departments: Map<string, string>
  users: Map<string, string>
  /** Relation to the subject shapes the authorization model accepts for it. */
  grantOptions: Map<string, SubjectKind[]>
}

function relationLabel(relation: string) {
  return RELATIONS.find((entry) => entry.value === relation)?.label ?? relation
}

/**
 * A stored grant reads back as an OpenFGA reference. Rendering that raw would put an id in front
 * of somebody deciding who sees a body of knowledge, so each known shape resolves to the name the
 * administrator chose it by.
 *
 * <p>A reference that resolves to nothing falls back to the reference itself rather than a generic
 * word. A tuple naming a unit the directory does not list is real — the demo dataset contains one
 * — and "a department" would hide which, leaving somebody auditing access with a subject they
 * cannot look up. The id is the only useful thing left to show.
 */
function parseSubject(subject: string) {
  const colon = subject.indexOf(":")
  if (colon < 0) return { type: "", id: subject, relation: undefined as string | undefined }
  const rest = subject.slice(colon + 1)
  const hash = rest.indexOf("#")
  return {
    type: subject.slice(0, colon),
    id: hash < 0 ? rest : rest.slice(0, hash),
    relation: hash < 0 ? undefined : rest.slice(hash + 1),
  }
}

function subjectLabel(subject: string, directory: Directory) {
  const { type, id, relation } = parseSubject(subject)

  if (type === "organization" && relation === "member") return "Everyone in the organization"
  if (type === "organizational_unit" && relation === "member") {
    const department = directory.departments.get(id)
    return department ?? subject
  }
  if (type === "organizational_unit" && relation === "manager") {
    const department = directory.departments.get(id)
    return department ? `${department} · managers` : subject
  }
  if (type === "role") return `Role · ${id}`
  if (type === "user") return directory.users.get(id) ?? subject
  return subject
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
  const { type, id, relation } = parseSubject(subject)

  if (type === "user" && relation === undefined) return { kind: "USER", subjectId: id }
  if (type === "role" && relation === "assignee") return { kind: "ROLE", role: id }
  if (type === "organization" && relation === "member") return { kind: "ORGANIZATION" }
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

  const [relation, setRelation] = useState<string>("viewer")
  const [kind, setKind] = useState<SubjectKind>("ORGANIZATION")
  const [subjectId, setSubjectId] = useState("")
  const [role, setRole] = useState("")

  // The relation decides which subjects are even expressible, so it drives the second control
  // rather than the two being chosen independently and reconciled by a refusal.
  const allowedKinds = directory.grantOptions.get(relation) ?? []
  const grants = space.grants ?? []
  // Administrators can always reach a space they administer, so a space holding only those is not
  // broken — but nobody it was created for can see it yet, and that is worth saying out loud.
  // Every other relation reaches can_view through the model, so each one counts as a reader.
  const readers = grants.filter((entry) => entry.relation !== "administrator")

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
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center gap-3 px-4 py-3 text-left outline-none transition-colors hover:bg-surface-subtle focus-visible:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring"
        >
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{space.name}</span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground">
              {space.key}
              {space.departmentId
                ? ` · ${directory.departments.get(space.departmentId) ?? "a department"}`
                : " · whole organization"}
            </span>
          </span>
          <span className="hidden items-center gap-2 sm:flex">
            {space.grantsComplete === false ? (
              <Badge variant="warning">Partial list</Badge>
            ) : readers.length === 0 ? (
              <Badge variant="warning">Nobody can read this yet</Badge>
            ) : (
              <Badge variant="secondary">
                {grants.length} {grants.length === 1 ? "grant" : "grants"}
              </Badge>
            )}
          </span>
          <ChevronDown
            className="size-4 shrink-0 text-muted-foreground transition-transform group-data-[state=open]:rotate-180"
            aria-hidden="true"
          />
        </button>
      </CollapsibleTrigger>

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
              return (
                <li
                  key={`${entry.relation}:${entry.subject}`}
                  className="flex items-center gap-2 rounded-lg bg-surface-raised px-3 py-2"
                >
                  <Badge variant="outline">{relationLabel(entry.relation!)}</Badge>
                  <span className="min-w-0 flex-1 truncate text-sm">
                    {subjectLabel(entry.subject!, directory)}
                  </span>
                  {removal ? (
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
                      Written at bootstrap
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
              {RELATIONS.map((entry) => (
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
            <Input
              value={role}
              onChange={(event) => setRole(event.target.value)}
              placeholder="organization-admin"
              aria-label="Role name"
              className="w-56"
            />
          ) : null}

          <Button type="submit" variant="outline" disabled={!subjectIsChosen || grant.isPending}>
            Grant
          </Button>
        </form>
      </CollapsibleContent>
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
  const roles = useQuery(adminQuery(listAdminRolesOptions()))
  const grantOptions = useQuery(adminKnowledgeSpaceGrantOptionsQueryOptions())
  const create = useMutation(createAdminKnowledgeSpaceMutation())

  const [name, setName] = useState("")
  const [departmentId, setDepartmentId] = useState("ORGANIZATION")

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
    users: new Map((users.data ?? []).map((user) => [user.id!, user.name || user.email || user.id!])),
    grantOptions: new Map(
      (grantOptions.data ?? []).map((option) => [
        option.relation!,
        (option.kinds ?? []) as SubjectKind[],
      ]),
    ),
  }

  const rows = spaces.data ?? []
  const withoutReaders = rows.filter(
    (space) => (space.grants ?? []).filter((grant) => grant.relation !== "administrator").length === 0,
  ).length

  async function createSpace() {
    try {
      const created = await create.mutateAsync({
        body: {
          name,
          departmentId: departmentId === "ORGANIZATION" ? undefined : departmentId,
        },
      })
      setName("")
      setDepartmentId("ORGANIZATION")
      await invalidateAdminData(queryClient)
      toast.success(`${created.name} was created`, {
        description: "Nobody can read it until you grant access below.",
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
      description="A space is one body of knowledge: who it serves, and who answers for it. Granting a space decides who may reach what lands in it — the source system still caps every read, so a grant here can never reveal a document Slack or Drive would refuse."
    >
      <AdminStats
        stats={[
          { label: "Spaces", value: rows.length },
          { label: "Scoped to a department", value: rows.filter((space) => space.departmentId).length },
          {
            label: "Nobody can read yet",
            value: withoutReaders,
            hint: "Only their administrators can reach these",
          },
          { label: "Roles available to grant", value: roles.data?.roles?.length ?? 0 },
        ]}
      />

      <AdminSection title="Create a space">
        <form
          className="flex flex-wrap items-end gap-2 p-4"
          onSubmit={(event) => {
            event.preventDefault()
            void createSpace()
          }}
        >
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Sales Knowledge"
            aria-label="Knowledge Space name"
            className="min-w-[260px] flex-1"
          />
          <Select value={departmentId} onValueChange={setDepartmentId}>
            <SelectTrigger className="w-56" aria-label="Who this space belongs to">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ORGANIZATION">Whole organization</SelectItem>
              {[...directory.departments].map(([id, label]) => (
                <SelectItem key={id} value={id}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button type="submit" disabled={!name.trim() || create.isPending}>
            <FolderPlus className="size-4" aria-hidden="true" />
            Create
          </Button>
        </form>
        <p className="border-t border-border-subtle px-4 py-3 text-xs text-muted-foreground">
          The key is derived from the name and never changes, so a later rename keeps every existing
          reference working.
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

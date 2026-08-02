import type { AdminKnowledgeSpaceResponse } from "@/lib/hey-api"

export type AudienceDirectory = {
  departments: Map<string, string>
  users: Map<string, string>
}

const ORGANIZATION_ROLE_RELATIONS: Record<string, string> = {
  knowledge_reader: "knowledge-reader",
  knowledge_contributor: "knowledge-contributor",
  knowledge_reviewer: "knowledge-reviewer",
}

const ROLE_LABELS: Record<string, string> = {
  "knowledge-reader": "Knowledge readers",
  "knowledge-contributor": "Knowledge contributors",
  "knowledge-reviewer": "Knowledge reviewers",
}

export function organizationRole(relation: string | undefined) {
  return relation ? ORGANIZATION_ROLE_RELATIONS[relation] : undefined
}

export function roleLabel(role: string) {
  return ROLE_LABELS[role] ?? role
}

export function parseSpaceSubject(subject: string) {
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

/** Resolve stored relationship subjects without exposing internal ids in the primary UI. */
export function subjectLabel(subject: string, directory: AudienceDirectory) {
  const { type, id, relation } = parseSpaceSubject(subject)

  if (type === "organization" && relation === "member") return "Everyone in the organization"
  if (type === "organization" && organizationRole(relation)) {
    return roleLabel(organizationRole(relation)!)
  }
  if (type === "organizational_unit" && relation === "member") {
    return directory.departments.get(id) ?? "Department no longer in directory"
  }
  if (type === "organizational_unit" && relation === "manager") {
    const department = directory.departments.get(id)
    return department ? `${department} · managers` : "Managers of an unavailable department"
  }
  if (type === "role") return `Role · ${roleLabel(id)}`
  if (type === "user") return directory.users.get(id) ?? "Person no longer in directory"
  return "Unrecognized stored subject"
}

export function audienceLabel(space: AdminKnowledgeSpaceResponse, directory: AudienceDirectory) {
  if (space.audienceMode === "ORGANIZATION") return "Organization audience"
  if (space.audienceMode === "DEPARTMENT") {
    const department = space.departmentId ? directory.departments.get(space.departmentId) : undefined
    return department ? `${department} department` : "Department unavailable"
  }
  return "Restricted custom audience"
}

export function isBuiltInAudience(space: AdminKnowledgeSpaceResponse, subject: string) {
  const parsed = parseSpaceSubject(subject)
  if (space.audienceMode === "ORGANIZATION") {
    return parsed.type === "organization" && parsed.relation === "member"
  }
  if (space.audienceMode === "DEPARTMENT") {
    return (
      parsed.type === "organizational_unit" &&
      parsed.relation === "member" &&
      parsed.id === space.departmentId
    )
  }
  return false
}

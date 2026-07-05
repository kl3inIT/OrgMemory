import { useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { getOrganizationContext, type Department, type OrgUser } from "@/lib/api"

export function useOrganizationContext() {
  return useQuery({
    queryKey: ["organization-context"],
    queryFn: getOrganizationContext,
  })
}

export function useOrganizationLookups() {
  const context = useOrganizationContext()
  const lookups = useMemo(() => {
    const departments = context.data?.departments ?? []
    const users = context.data?.users ?? []
    return {
      departments,
      users,
      departmentById: new Map(departments.map((department) => [department.id, department])),
      userById: new Map(users.map((user) => [user.id, user])),
    }
  }, [context.data])

  return { ...context, ...lookups }
}

export function departmentName(departments: Map<string, Department>, id?: string | null) {
  return id ? departments.get(id)?.name ?? "Unassigned" : "Unassigned"
}

export function userName(users: Map<string, OrgUser>, id?: string | null) {
  return id ? users.get(id)?.name ?? "Unassigned" : "Unassigned"
}

export function userInitials(user?: OrgUser) {
  if (!user) return "OM"
  return user.name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()
}

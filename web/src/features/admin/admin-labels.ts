import type { AdminSourcePrincipalResponse } from "@/lib/hey-api"

export const USER_ROLES = [
  "EMPLOYEE",
  "TEAM_LEAD",
  "MANAGER",
  "DIRECTOR",
  "EXECUTIVE",
  "ADMIN",
] as const

export type UserRoleValue = (typeof USER_ROLES)[number]

const ROLE_LABELS: Record<UserRoleValue, string> = {
  EMPLOYEE: "Employee",
  TEAM_LEAD: "Team lead",
  MANAGER: "Manager",
  DIRECTOR: "Director",
  EXECUTIVE: "Executive",
  ADMIN: "Administrator",
}

export function roleLabel(role: string | undefined) {
  return role ? (ROLE_LABELS[role as UserRoleValue] ?? role) : "Unknown"
}

/**
 * How a mapping was established. The tier is the whole point of the ledger: it says how
 * much evidence stands behind a person's access.
 */
export const MAPPING_METHODS: Record<string, { label: string; hint: string }> = {
  IDP_JOIN: { label: "IdP join", hint: "Matched on the identity provider's issuer and subject." },
  SSO_EMAIL_JOIN: { label: "SSO email", hint: "Matched on an email the source verified through SSO." },
  SELF_CLAIM: { label: "Self claim", hint: "The user claimed this identity themselves." },
  ADMIN_CONFIRMED: { label: "Admin confirmed", hint: "An administrator vouched for this link." },
}

export function principalName(principal: AdminSourcePrincipalResponse) {
  return principal.observedDisplayName?.trim() || principal.externalKey || "Unknown principal"
}

export function connectionLabel(sourceSystem?: string, sourceConnectionKey?: string) {
  return [sourceSystem, sourceConnectionKey].filter(Boolean).join(" · ") || "Unknown connection"
}

export function formatTimestamp(value?: string) {
  if (!value) return "—"
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? "—" : parsed.toLocaleString()
}

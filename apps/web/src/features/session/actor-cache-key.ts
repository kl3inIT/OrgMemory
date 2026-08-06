import type { SessionResponse } from "@/lib/hey-api"

/**
 * Actor-scoped server responses must never be reused after the browser identity
 * or its effective clearance changes.
 */
export function sessionActorKey(session: SessionResponse) {
  return [
    session.organizationId ?? "no-organization",
    session.userId ?? "no-user",
    session.departmentId ?? "no-department",
    session.clearance ?? "no-clearance",
  ].join(":")
}

export function scopeActorQueryKey<
  T extends readonly [Record<string, unknown>],
>(queryKey: T, actorKey: string): T {
  return [{ ...queryKey[0], actorKey }] as unknown as T
}

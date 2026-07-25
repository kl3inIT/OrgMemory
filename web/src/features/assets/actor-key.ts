import type { SessionResponse } from "@/lib/hey-api"

/**
 * Permission-filtered Asset responses must never be reused across browser
 * identities. The server remains authoritative; this key only partitions the
 * client cache when a user, organization, department, or role changes.
 */
export function assetActorKey(session: SessionResponse) {
  return [
    session.organizationId ?? "no-organization",
    session.userId ?? "no-user",
    session.departmentId ?? "no-department",
    session.role ?? "no-role",
  ].join(":")
}

export function scopeAssetQueryKey<
  T extends readonly [Record<string, unknown>],
>(queryKey: T, actorKey: string): T {
  return [{ ...queryKey[0], actorKey }] as unknown as T
}

import {
  scopeActorQueryKey,
  sessionActorKey,
} from "@/features/session/actor-cache-key"

/**
 * Permission-filtered Asset responses must never be reused across browser
 * identities. The server remains authoritative; this key only partitions the
 * client cache when a user, organization, department, or role changes.
 */
export const assetActorKey = sessionActorKey

export const scopeAssetQueryKey = scopeActorQueryKey

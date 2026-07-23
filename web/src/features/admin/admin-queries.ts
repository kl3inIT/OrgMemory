import { queryOptions, type QueryClient } from "@tanstack/react-query"

import {
  listAdminSourceConnectionsOptions,
  listAdminSourceConnectionsQueryKey,
  listAdminSourceGroupsOptions,
  listAdminSourceGroupsQueryKey,
  listAdminSourcePrincipalsOptions,
  listAdminSourcePrincipalsQueryKey,
  listAdminUsersOptions,
  listAdminUsersQueryKey,
} from "@/lib/hey-api/@tanstack/react-query.gen"

// Administration data is small and changes only when an administrator acts, so it is
// cached briefly and invalidated explicitly after every mutation.
const ADMIN_STALE_TIME = 15_000

export function adminUsersQueryOptions() {
  return queryOptions({ ...listAdminUsersOptions(), staleTime: ADMIN_STALE_TIME })
}

export function adminSourcePrincipalsQueryOptions() {
  return queryOptions({ ...listAdminSourcePrincipalsOptions(), staleTime: ADMIN_STALE_TIME })
}

export function adminSourceConnectionsQueryOptions() {
  return queryOptions({ ...listAdminSourceConnectionsOptions(), staleTime: ADMIN_STALE_TIME })
}

export function adminSourceGroupsQueryOptions() {
  return queryOptions({ ...listAdminSourceGroupsOptions(), staleTime: ADMIN_STALE_TIME })
}

/**
 * Confirming or revoking a mapping changes the counts on every other administration
 * screen, so the whole area is refreshed together rather than guessing which parts moved.
 */
export async function invalidateAdminData(queryClient: QueryClient) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: listAdminUsersQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourcePrincipalsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourceConnectionsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourceGroupsQueryKey() }),
  ])
}

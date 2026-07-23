import { queryOptions, type QueryClient } from "@tanstack/react-query"

import {
  getAdminConnectionActivityOptions,
  listAdminConnectionScopesOptions,
  listAdminConnectionsOptions,
  listAdminConnectionsQueryKey,
  listAdminConnectorSourcesOptions,
  listAdminSourceConnectionsOptions,
  listAdminSourceConnectionsQueryKey,
  listAdminSourceGroupsOptions,
  listAdminSourceGroupsQueryKey,
  listAdminSourcePrincipalsOptions,
  listAdminSourcePrincipalsQueryKey,
  listAdminUsersOptions,
  listAdminUsersQueryKey,
  listKnowledgeSpaceUploadTargetsOptions,
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

/** A source's connections. The path is the source system, so every source uses this one. */
export function adminConnectionsQueryOptions(sourceSystem: string) {
  return queryOptions({
    ...listAdminConnectionsOptions({ path: { sourceSystem } }),
    staleTime: ADMIN_STALE_TIME,
  })
}

/**
 * What a connection could be pointed at, read from the source with its stored credential. Only
 * once there is a connection to read it with, which is why the key is part of the path.
 */
export function adminConnectionScopesQueryOptions(sourceSystem: string, connectionKey: string) {
  return queryOptions({
    ...listAdminConnectionScopesOptions({ path: { sourceSystem, connectionKey } }),
    staleTime: ADMIN_STALE_TIME,
  })
}

/** Which sources this deployment can actually ingest, from the adapters it has installed. */
export function adminConnectorSourcesQueryOptions() {
  return queryOptions({ ...listAdminConnectorSourcesOptions(), staleTime: ADMIN_STALE_TIME })
}

/**
 * What a connection has done. Kept fresher than the rest of administration because it moves
 * without anybody acting: a crawl runs on the worker's schedule, so a stale answer here is a
 * screen quietly reporting yesterday's failure as the current one.
 */
export function adminConnectionActivityQueryOptions(sourceSystem: string, connectionKey: string) {
  return queryOptions({
    ...getAdminConnectionActivityOptions({ path: { sourceSystem, connectionKey } }),
    staleTime: 5_000,
  })
}

/** The Spaces a crawl may publish into are the same ones an upload may target. */
export function knowledgeSpacesQueryOptions() {
  return queryOptions({ ...listKnowledgeSpaceUploadTargetsOptions(), staleTime: ADMIN_STALE_TIME })
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
    queryClient.invalidateQueries({
      queryKey: listAdminConnectionsQueryKey({ path: { sourceSystem: "slack" } }),
    }),
  ])
}

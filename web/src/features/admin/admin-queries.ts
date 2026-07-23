import { queryOptions, type QueryClient } from "@tanstack/react-query"

import {
  getAdminConnectionActivityOptions,
  listAdminConnectionScopesOptions,
  listAdminConnectionScopesQueryKey,
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
 *
 * <p>It is also the one screen that refetches on a timer. Everywhere else in administration a
 * stale answer waits for the administrator's next action to correct it, and there always is
 * one; here the whole purpose of the page is watching something happen elsewhere, and without
 * this the reader has to reload to find out that it did.
 */
export function adminConnectionActivityQueryOptions(sourceSystem: string, connectionKey: string) {
  return queryOptions({
    ...getAdminConnectionActivityOptions({ path: { sourceSystem, connectionKey } }),
    staleTime: 5_000,
    refetchInterval: 10_000,
  })
}

/** The Spaces a crawl may publish into are the same ones an upload may target. */
export function knowledgeSpacesQueryOptions() {
  return queryOptions({ ...listKnowledgeSpaceUploadTargetsOptions(), staleTime: ADMIN_STALE_TIME })
}

/**
 * The same query for every source, whichever path it was made with.
 *
 * <p>A generated key is a single object carrying the operation, the base URL and the path
 * parameters, and TanStack matches keys partially — so dropping the path leaves a key that
 * matches every source's copy of that query. The operation is read back off a generated key
 * rather than written out, because a hand-copied one goes stale silently: the name still
 * compiles after the operation is renamed, and simply stops matching anything.
 */
function everySourceOf(key: readonly [{ _id: string; baseUrl?: unknown }]) {
  const [{ _id, baseUrl }] = key
  return [{ _id, baseUrl }]
}

/**
 * Confirming or revoking a mapping changes the counts on every other administration
 * screen, so the whole area is refreshed together rather than guessing which parts moved.
 *
 * <p>Connections and scopes are matched across every source rather than named one at a time.
 * Naming them is how this went wrong before: it listed Slack, so storing a Google Drive
 * credential left the Drive table showing the state from before the credential existed.
 */
export async function invalidateAdminData(queryClient: QueryClient) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: listAdminUsersQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourcePrincipalsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourceConnectionsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourceGroupsQueryKey() }),
    queryClient.invalidateQueries({
      queryKey: everySourceOf(listAdminConnectionsQueryKey({ path: { sourceSystem: "" } })),
    }),
    // What a connection can be pointed at is read with its credential, so storing or forgetting
    // one changes the answer — including from "a list" to "there is nothing to read it with".
    queryClient.invalidateQueries({
      queryKey: everySourceOf(
        listAdminConnectionScopesQueryKey({ path: { sourceSystem: "", connectionKey: "" } }),
      ),
    }),
  ])
}

import { queryOptions, type QueryClient } from "@tanstack/react-query"

import {
  contextOptions,
  getAdminConnectionActivityOptions,
  listAdminConnectionScopesQueryKey,
  listAdminConnectionsQueryKey,
  listAdminSourceConnectionsQueryKey,
  listAdminSourceGroupsQueryKey,
  listAdminSourcePrincipalsQueryKey,
  listAdminInvitationsQueryKey,
  listAdminKnowledgeSpaceGrantOptionsOptions,
  listAdminKnowledgeSpacesQueryKey,
  listAdminRolesQueryKey,
  listAdminUserPermissionsOptions,
  listAdminUserPermissionsQueryKey,
  listAdminUsersQueryKey,
  listProvisioningConnectionsQueryKey,
  listProvisioningCredentialsQueryKey,
} from "@/lib/hey-api/@tanstack/react-query.gen"

// Administration data is small and changes only when an administrator acts, so it is
// cached briefly and invalidated explicitly after every mutation.
const ADMIN_STALE_TIME = 15_000

export function adminQuery<TOptions extends object>(options: TOptions) {
  return { ...options, staleTime: ADMIN_STALE_TIME }
}

/**
 * Which subject each Knowledge Space relation accepts. It comes from the server rather than a
 * table held here, because it mirrors type restrictions in the authorization model and a second
 * copy would drift into offering grants that can only be refused. It changes only with the model,
 * so it is cached for the session.
 */
export function adminKnowledgeSpaceGrantOptionsQueryOptions() {
  return queryOptions({ ...listAdminKnowledgeSpaceGrantOptionsOptions(), staleTime: Infinity })
}

/** The organization and its departments, which is where a space's audience is chosen from. */
export function organizationContextQueryOptions() {
  return queryOptions({ ...contextOptions(), staleTime: 5 * 60_000 })
}

/**
 * A permission is computed when asked, never stored, so a cached copy is a claim about the
 * past. This holds it only long enough to render the screen once and refetches on return,
 * because an administrator arriving here is usually checking whether a change took effect.
 */
export function adminUserPermissionsQueryOptions(userId: string) {
  return queryOptions({
    ...listAdminUserPermissionsOptions({ path: { userId } }),
    staleTime: 0,
    refetchOnMount: "always",
  })
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
    queryClient.invalidateQueries({ queryKey: listAdminRolesQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminInvitationsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminKnowledgeSpacesQueryKey() }),
    // A role assignment changes what every user resolves to, and the answer is recomputed
    // rather than stored, so the whole permission view has to be asked again.
    queryClient.invalidateQueries({
      queryKey: listAdminUserPermissionsQueryKey({ path: { userId: "" } }).slice(0, 1),
    }),
    queryClient.invalidateQueries({ queryKey: listAdminSourcePrincipalsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourceConnectionsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listAdminSourceGroupsQueryKey() }),
    queryClient.invalidateQueries({ queryKey: listProvisioningConnectionsQueryKey() }),
    queryClient.invalidateQueries({
      queryKey: listProvisioningCredentialsQueryKey({ path: { connectionId: "" } }).slice(0, 1),
    }),
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

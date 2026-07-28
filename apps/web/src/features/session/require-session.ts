import type { QueryClient } from "@tanstack/react-query"
import { redirect } from "@tanstack/react-router"

import { browserLoginUrl } from "@/features/session/browser-login"
import { browserSessionQueryOptions } from "@/features/session/session-query"
import type { SessionResponse } from "@/lib/hey-api"

/**
 * The one place a route decides that an unauthenticated visitor is sent to the
 * identity provider, so the product shell and the admin area cannot drift apart.
 */
export async function requireBrowserSession(
  queryClient: QueryClient,
  returnTo: string,
): Promise<SessionResponse> {
  const session = await queryClient.ensureQueryData(browserSessionQueryOptions())

  if (!session.authenticated) {
    throw redirect({ href: browserLoginUrl(returnTo), replace: true })
  }

  return session
}

export function isAdministrator(session: SessionResponse) {
  return session.role === "ADMIN"
}

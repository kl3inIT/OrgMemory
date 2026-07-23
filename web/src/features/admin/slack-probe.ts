import type { AdminSlackProbeResponse } from "@/lib/hey-api"

/** Slack's own vocabulary, and the one code this application adds to it. */
export const PROBE_REASONS: Record<string, string> = {
  invalid_auth: "Slack does not recognise this token.",
  not_authed: "The token is missing or expired.",
  token_revoked: "This token was revoked in Slack.",
  account_inactive: "The account this token belongs to is deactivated.",
  missing_scope: "The token authenticates but was not granted the scope to list channels.",
  no_credential: "No token is stored for this connection yet.",
  unreachable: "Slack could not be reached from this server.",
}

/**
 * Authenticating is not the same as being able to crawl. A token installed without
 * {@code channels:read} passes authentication cleanly and then fails at the first real
 * request, so both answers have to hold.
 */
export function probeIsGood(result: AdminSlackProbeResponse) {
  return Boolean(result.authenticated && result.canListChannels)
}

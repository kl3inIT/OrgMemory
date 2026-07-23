import type { AdminConnectorProbeResponse } from "@/lib/hey-api"

/**
 * Codes every source can produce, because this application produces them.
 *
 * <p>Kept apart from the sources' own vocabularies so that adding a connector adds a map rather
 * than editing a shared one, and so that a source cannot quietly redefine what "no credential"
 * means here.
 */
const SHARED_REASONS: Record<string, string> = {
  no_credential: "No credential is stored for this connection yet.",
  unreachable: "The source could not be reached from this server.",
  unreadable_response: "The source answered with something this connector could not read.",
}

/** Each source's own error codes, in its own words, because that is what an operator can look up. */
const SOURCE_REASONS: Record<string, Record<string, string>> = {
  slack: {
    invalid_auth: "Slack does not recognise this token.",
    not_authed: "The token is missing or expired.",
    token_revoked: "This token was revoked in Slack.",
    account_inactive: "The account this token belongs to is deactivated.",
    missing_scope: "The token authenticates but was not granted the scope to list channels.",
  },
  google_drive: {
    invalid_key: "This does not read as a Google service account key.",
    invalid_grant:
      "Google refused this key. It may have been deleted, or this server's clock may be too far off.",
    unauthorized_client:
      "The service account may not impersonate that user. Domain-wide delegation has to be granted for the Drive scope.",
    access_denied:
      "The key authenticates but Drive returned nothing it may read. Share a folder or a shared drive with the service account.",
    insufficient_scopes: "The key authenticates but was not granted the Drive scope.",
  },
}

export function probeReason(sourceSystem: string, result: AdminConnectorProbeResponse) {
  const code = result.errorCode
  if (!code) return "The credential authenticates and can read what a crawl needs."
  return SOURCE_REASONS[sourceSystem]?.[code] ?? SHARED_REASONS[code] ?? `The source answered: ${code}`
}

/**
 * Authenticating is not the same as being able to read. A Slack token installed without
 * `channels:read`, or a Drive service account nobody shared anything with, passes
 * authentication cleanly and then fails at the first real request, so both answers have to hold.
 */
export function probeIsGood(result: AdminConnectorProbeResponse) {
  return Boolean(result.authenticated && result.canReadContent)
}

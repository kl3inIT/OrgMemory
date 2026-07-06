import { User } from 'oidc-client-ts'
import type { AuthProviderProps } from 'react-oidc-context'

export const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false'

const authority = import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/orgmemory'
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID ?? 'orgmemory-web'

export const oidcConfig: AuthProviderProps = {
  authority,
  client_id: clientId,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  automaticSilentRenew: true,
  onSigninCallback: () => {
    // Strip ?code=...&state=... left over from the redirect so a refresh does not replay it.
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}

/**
 * Reads the current access token from the oidc-client-ts session store.
 * Usable outside the React tree (fetch helpers, chat transport).
 */
export function getAccessToken(): string | null {
  if (!AUTH_ENABLED) return null
  const raw = sessionStorage.getItem(`oidc.user:${authority}:${clientId}`)
  if (!raw) return null
  const user = User.fromStorageString(raw)
  if (!user || user.expired) return null
  return user.access_token
}

export function authHeaders(): Record<string, string> {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

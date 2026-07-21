const apiOrigin = import.meta.env.VITE_API_ORIGIN ?? (import.meta.env.DEV ? "http://localhost:8080" : "")

export function currentReturnPath() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`
}

export function browserLoginUrl(returnTo?: string) {
  const origin = apiOrigin || window.location.origin
  const loginUrl = new URL("/api/session/login", origin)
  if (returnTo) {
    loginUrl.searchParams.set("returnTo", returnTo)
  }
  return loginUrl.toString()
}

export function beginBrowserLogin(returnTo?: string) {
  window.location.replace(browserLoginUrl(returnTo))
}

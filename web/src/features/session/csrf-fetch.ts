import { getBrowserCsrfToken } from "@/lib/hey-api"

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"])

export const csrfFetch: typeof fetch = async (input, init) => {
  const method = init?.method?.toUpperCase() ?? "GET"
  if (SAFE_METHODS.has(method)) {
    return fetch(input, { ...init, credentials: init?.credentials ?? "same-origin" })
  }

  const { data } = await getBrowserCsrfToken({ throwOnError: true })
  if (!data?.headerName || !data.token) {
    throw new Error("The server did not issue a CSRF token.")
  }

  const headers = new Headers(init?.headers)
  headers.set(data.headerName, data.token)

  return fetch(input, {
    ...init,
    credentials: init?.credentials ?? "same-origin",
    headers,
  })
}

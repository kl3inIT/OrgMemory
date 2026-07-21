import { getBrowserCsrfToken } from "@/lib/hey-api"

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"])
const CSRF_COOKIE_NAME = "XSRF-TOKEN"

export async function getBrowserCsrfHeader(): Promise<[string, string]> {
  const { data } = await getBrowserCsrfToken({ throwOnError: true })
  if (!data?.headerName) {
    throw new Error("The server did not issue a CSRF header name.")
  }

  const token = document.cookie
    .split(";")
    .map((cookie) => cookie.trim())
    .find((cookie) => cookie.startsWith(`${CSRF_COOKIE_NAME}=`))
    ?.slice(CSRF_COOKIE_NAME.length + 1)

  if (!token) {
    throw new Error("The server did not issue a CSRF cookie.")
  }
  return [data.headerName, decodeURIComponent(token)]
}

export const csrfFetch: typeof fetch = async (input, init) => {
  const method = init?.method?.toUpperCase() ?? "GET"
  if (SAFE_METHODS.has(method)) {
    return fetch(input, { ...init, credentials: init?.credentials ?? "same-origin" })
  }

  const [headerName, token] = await getBrowserCsrfHeader()
  const headers = new Headers(init?.headers)
  headers.set(headerName, token)

  return fetch(input, {
    ...init,
    credentials: init?.credentials ?? "same-origin",
    headers,
  })
}

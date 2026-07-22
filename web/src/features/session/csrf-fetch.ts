import { getBrowserCsrfToken } from "@/lib/hey-api"

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"])
const CSRF_COOKIE_NAME = "XSRF-TOKEN"
let cachedHeaderName: string | undefined

export async function getBrowserCsrfHeader(): Promise<[string, string]> {
  if (!cachedHeaderName) {
    const { data } = await getBrowserCsrfToken({ throwOnError: true })
    if (!data?.headerName) {
      throw new Error("The server did not issue a CSRF header name.")
    }
    cachedHeaderName = data.headerName
  }

  const token = document.cookie
    .split(";")
    .map((cookie) => cookie.trim())
    .find((cookie) => cookie.startsWith(`${CSRF_COOKIE_NAME}=`))
    ?.slice(CSRF_COOKIE_NAME.length + 1)

  if (!token) {
    throw new Error("The server did not issue a CSRF cookie.")
  }
  return [cachedHeaderName, decodeURIComponent(token)]
}

export const csrfFetch: typeof fetch = async (input, init) => {
  const method = (init?.method ?? (input instanceof Request ? input.method : "GET")).toUpperCase()
  if (SAFE_METHODS.has(method)) {
    return fetch(input, { ...init, credentials: init?.credentials ?? "same-origin" })
  }

  const [headerName, token] = await getBrowserCsrfHeader()
  const headers = new Headers(input instanceof Request ? input.headers : undefined)
  new Headers(init?.headers).forEach((value, name) => headers.set(name, value))
  headers.set(headerName, token)

  return fetch(input, {
    ...init,
    credentials: init?.credentials ?? "same-origin",
    headers,
  })
}

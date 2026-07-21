import { getBrowserCsrfToken } from "@/lib/hey-api"

export async function submitBrowserLogout() {
  const { data } = await getBrowserCsrfToken({ throwOnError: true })

  if (!data?.parameterName || !data.token) {
    throw new Error("The server did not issue a CSRF token.")
  }

  // Spring Security's OIDC logout returns a redirect. A native form submission
  // lets the browser follow it as navigation; fetch() would not leave the app.
  const form = document.createElement("form")
  form.method = "post"
  form.action = "/api/session/logout"

  const csrf = document.createElement("input")
  csrf.type = "hidden"
  csrf.name = data.parameterName
  csrf.value = data.token
  form.append(csrf)

  document.body.append(form)
  form.submit()
}

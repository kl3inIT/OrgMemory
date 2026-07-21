import { createFileRoute } from "@tanstack/react-router"

import { LoginPage } from "@/features/session/login-page"

type LoginSearch = {
  error?: string
  loggedOut?: boolean
}

export const Route = createFileRoute("/login")({
  validateSearch: (search: Record<string, unknown>): LoginSearch => ({
    error: typeof search.error === "string" ? search.error : undefined,
    loggedOut: search.loggedOut === true || search.loggedOut === "true" || undefined,
  }),
  component: LoginPage,
})

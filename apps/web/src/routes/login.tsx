import { createFileRoute } from "@tanstack/react-router"

import { LoginPage } from "@/features/session/login-page"

type LoginSearch = {
  error?: string
}

export const Route = createFileRoute("/login")({
  validateSearch: (search: Record<string, unknown>): LoginSearch => ({
    error: typeof search.error === "string" ? search.error : undefined,
  }),
  component: LoginPage,
})

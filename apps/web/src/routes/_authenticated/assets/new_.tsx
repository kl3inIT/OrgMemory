import { createFileRoute, redirect } from "@tanstack/react-router"

export const Route = createFileRoute("/_authenticated/assets/new_")({
  beforeLoad: () => {
    throw redirect({ to: "/assets/new/skill", replace: true })
  },
})

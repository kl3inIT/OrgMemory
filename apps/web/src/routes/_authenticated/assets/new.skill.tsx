import { createFileRoute, Outlet } from "@tanstack/react-router"

export const Route = createFileRoute("/_authenticated/assets/new/skill")({
  component: Outlet,
})

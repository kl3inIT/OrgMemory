import { createFileRoute } from "@tanstack/react-router"

import { AdminSpacesPage } from "@/features/admin/components/admin-spaces-page"

export const Route = createFileRoute("/admin/spaces")({
  component: AdminSpacesPage,
  staticData: { title: "Knowledge Spaces" },
})

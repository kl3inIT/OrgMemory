import { createFileRoute } from "@tanstack/react-router"

import { AdminMappingsPage } from "@/features/admin/components/admin-mappings-page"

export const Route = createFileRoute("/admin/mappings")({
  component: AdminMappingsPage,
  staticData: { title: "Source mappings" },
})

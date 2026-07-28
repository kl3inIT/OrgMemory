import { createFileRoute } from "@tanstack/react-router"

import { AdminIndexSettingsPage } from "@/features/admin/components/admin-index-settings-page"

export const Route = createFileRoute("/admin/index-settings")({
  component: AdminIndexSettingsPage,
  staticData: { title: "Index Settings" },
})

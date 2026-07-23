import { createFileRoute } from "@tanstack/react-router"

import { AdminGroupsPage } from "@/features/admin/components/admin-groups-page"

export const Route = createFileRoute("/admin/groups")({
  component: AdminGroupsPage,
  staticData: { title: "Source groups" },
})

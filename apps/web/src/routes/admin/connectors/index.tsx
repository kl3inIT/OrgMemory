import { createFileRoute } from "@tanstack/react-router"

import { AdminConnectorsPage } from "@/features/admin/components/admin-connectors-page"

export const Route = createFileRoute("/admin/connectors/")({
  component: AdminConnectorsPage,
  staticData: { title: "Sources" },
})

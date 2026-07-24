import { createFileRoute } from "@tanstack/react-router"

import { AdminAccessPage } from "@/features/admin/components/admin-access-page"

export const Route = createFileRoute("/admin/access")({
  component: AdminAccessPage,
  staticData: { title: "Access check" },
})

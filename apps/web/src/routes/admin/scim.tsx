import { createFileRoute } from "@tanstack/react-router"

import { AdminScimPage } from "@/features/admin/components/admin-scim-page"

export const Route = createFileRoute("/admin/scim")({
  component: AdminScimPage,
  staticData: { title: "SCIM" },
})

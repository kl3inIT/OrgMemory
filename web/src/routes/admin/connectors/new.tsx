import { createFileRoute } from "@tanstack/react-router"

import { ConnectorCatalogPage } from "@/features/admin/components/connector-catalog-page"

export const Route = createFileRoute("/admin/connectors/new")({
  component: ConnectorCatalogPage,
  staticData: { title: "Add a source" },
})

import { createFileRoute } from "@tanstack/react-router"

import { AssetTypeSelectionPage } from "@/features/assets/components/asset-type-selection-page"

export const Route = createFileRoute("/_authenticated/assets/new")({
  component: AssetTypeSelectionPage,
  staticData: { title: "Add an asset" },
})

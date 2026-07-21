import { createFileRoute } from "@tanstack/react-router"

import { SourcesPage } from "@/features/sources/components/sources-page"

export const Route = createFileRoute("/_authenticated/sources")({
  component: SourcesPage,
})

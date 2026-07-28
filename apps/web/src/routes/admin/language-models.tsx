import { createFileRoute } from "@tanstack/react-router"

import { AdminLanguageModelsPage } from "@/features/admin/components/admin-language-models-page"

export const Route = createFileRoute("/admin/language-models")({
  component: AdminLanguageModelsPage,
  staticData: { title: "Language Models" },
})

import { createFileRoute } from "@tanstack/react-router"

import { AssistantPage } from "@/features/assistant/components/assistant-page"

export const Route = createFileRoute("/_authenticated/")({
  component: AssistantPage,
  staticData: { title: "Assistant" },
})

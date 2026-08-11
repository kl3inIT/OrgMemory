import { createFileRoute } from "@tanstack/react-router"

import { PromptCreationPage } from "@/features/assets/components/prompt-creation-page"

export const Route = createFileRoute("/_authenticated/assets/new/prompt")({
  component: PromptCreationPage,
  staticData: { title: "Create a Prompt" },
})

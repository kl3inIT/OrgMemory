import { createFileRoute } from "@tanstack/react-router"

import { AssistantWorkspace } from "@/features/assistant/components/assistant-workspace"

export const Route = createFileRoute("/_authenticated/")({
  component: AssistantWorkspace,
})

import { createFileRoute } from "@tanstack/react-router"

import { AssistantPage } from "@/features/assistant/components/assistant-page"
import { sessionActorKey } from "@/features/session/actor-cache-key"

export const Route = createFileRoute("/_authenticated/")({
  component: AssistantRoute,
  staticData: { title: "Assistant" },
  validateSearch: (search: Record<string, unknown>): { chat?: string } => {
    const chat = typeof search.chat === "string" ? search.chat : ""
    return {
      chat: /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        chat,
      )
        ? chat
        : undefined,
    }
  },
})

function AssistantRoute() {
  const { chat } = Route.useSearch()
  const { session } = Route.useRouteContext()
  const navigate = Route.useNavigate()
  return (
    <AssistantPage
      conversationId={chat}
      actorKey={sessionActorKey(session)}
      onConversationIdChange={(conversationId) =>
        void navigate({
          replace: true,
          search: { chat: conversationId },
        })
      }
    />
  )
}

import { DefaultChatTransport, type UIMessage } from "ai"

import { csrfFetch } from "@/features/session/csrf-fetch"

export function createAssistantTransport({
  conversationId,
  onConversationId,
}: {
  conversationId: () => string | undefined
  onConversationId: (conversationId: string) => void
}) {
  return new DefaultChatTransport({
    api: "/api/assistant/chat",
    credentials: "same-origin",
    fetch: async (input, init) => {
      const response = await csrfFetch(input, init)
      const nextConversationId = response.headers.get("X-Conversation-ID")
      if (nextConversationId) onConversationId(nextConversationId)
      return response
    },
    prepareSendMessagesRequest: ({ messages }) => {
      const latest = messages.at(-1)
      const message = (latest?.parts ?? [])
        .filter((part) => part.type === "text")
        .map((part) => part.text)
        .join("")

      return {
        body: {
          message,
          limit: 5,
          conversationId: conversationId(),
        },
      }
    },
  }) as DefaultChatTransport<UIMessage>
}

import { DefaultChatTransport, type UIMessage } from "ai"

import { csrfFetch } from "@/features/session/csrf-fetch"

export function createAssistantTransport() {
  return new DefaultChatTransport({
    api: "/api/assistant/chat",
    credentials: "same-origin",
    fetch: csrfFetch,
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
        },
      }
    },
  }) as DefaultChatTransport<UIMessage>
}

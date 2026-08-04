import { useCallback, useEffect, useRef, useState } from "react"

import {
  clearAssistantActorDrafts,
  clearAssistantDraft,
  readAssistantDraft,
  writeAssistantDraft,
} from "@/features/assistant/assistant-draft-storage"

export function useAssistantDraft(actorKey: string, conversationId?: string) {
  const [text, setText] = useState(() => readAssistantDraft(actorKey, conversationId))
  const previousActor = useRef(actorKey)

  useEffect(() => {
    if (previousActor.current !== actorKey) {
      clearAssistantActorDrafts(previousActor.current)
      previousActor.current = actorKey
    }
    setText(readAssistantDraft(actorKey, conversationId))
  }, [actorKey, conversationId])

  const update = useCallback(
    (value: string) => {
      const bounded = writeAssistantDraft(actorKey, conversationId, value)
      setText(bounded)
    },
    [actorKey, conversationId],
  )

  const clear = useCallback(() => {
    clearAssistantDraft(actorKey, conversationId)
    setText("")
  }, [actorKey, conversationId])

  return { text, setText: update, clear }
}

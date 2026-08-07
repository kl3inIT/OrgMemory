import { beforeEach, describe, expect, it } from "vitest"

import {
  clearAllAssistantDrafts,
  clearAssistantActorDrafts,
  clearAssistantDraft,
  readAssistantDraft,
  writeAssistantDraft,
} from "@/features/assistant/assistant-draft-storage"

describe("assistant draft storage", () => {
  beforeEach(() => sessionStorage.clear())

  it("isolates new and existing conversation drafts by actor", () => {
    writeAssistantDraft("actor-a", undefined, "new draft")
    writeAssistantDraft("actor-a", "conversation-1", "existing draft")
    writeAssistantDraft("actor-b", "conversation-1", "other actor")

    expect(readAssistantDraft("actor-a")).toBe("new draft")
    expect(readAssistantDraft("actor-a", "conversation-1")).toBe("existing draft")
    expect(readAssistantDraft("actor-b", "conversation-1")).toBe("other actor")
  })

  it("bounds drafts persisted by an older client", () => {
    sessionStorage.setItem(
      "orgmemory:assistant-draft:v1:actor-a:new",
      "x".repeat(1_100),
    )

    expect(readAssistantDraft("actor-a")).toHaveLength(1_000)
  })

  it("caps drafts at the server message limit and clears lifecycle scopes", () => {
    const bounded = writeAssistantDraft("actor-a", "conversation-1", "x".repeat(1_100))
    expect(bounded).toHaveLength(1_000)

    clearAssistantDraft("actor-a", "conversation-1")
    expect(readAssistantDraft("actor-a", "conversation-1")).toBe("")

    writeAssistantDraft("actor-a", undefined, "a")
    writeAssistantDraft("actor-b", undefined, "b")
    clearAssistantActorDrafts("actor-a")
    expect(readAssistantDraft("actor-a")).toBe("")
    expect(readAssistantDraft("actor-b")).toBe("b")

    clearAllAssistantDrafts()
    expect(readAssistantDraft("actor-b")).toBe("")
  })
})

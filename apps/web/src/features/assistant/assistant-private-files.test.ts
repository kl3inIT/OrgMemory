import { describe, expect, it } from "vitest"

import {
  assistantFileShouldPoll,
  assistantFileStatusLabel,
  MAX_ASSISTANT_PRIVATE_FILES,
} from "@/features/assistant/assistant-private-files"

describe("Assistant private files", () => {
  it("keeps a small turn selection", () => {
    expect(MAX_ASSISTANT_PRIVATE_FILES).toBe(3)
  })

  it("polls only while upload processing can still advance", () => {
    expect(assistantFileShouldPoll(undefined)).toBe(true)
    expect(assistantFileShouldPoll("UPLOADED")).toBe(true)
    expect(assistantFileShouldPoll("PROCESSING")).toBe(true)
    expect(assistantFileShouldPoll("READY")).toBe(false)
    expect(assistantFileShouldPoll("FAILED")).toBe(false)
    expect(assistantFileShouldPoll("EXPIRED")).toBe(false)
  })

  it("uses product-facing lifecycle labels", () => {
    expect(assistantFileStatusLabel("UPLOADED")).toBe("Queued")
    expect(assistantFileStatusLabel("READY")).toBe("Ready")
    expect(assistantFileStatusLabel("EXPIRED")).toBe("Expired")
  })
})

import { describe, expect, it } from "vitest"

import {
  assistantEvidenceReady,
  assistantEvidenceShouldPoll,
  assistantEvidenceStatusLabel,
  assistantEvidenceUploadDisabledReason,
} from "@/features/assistant/assistant-evidence"

describe("Assistant governed file evidence", () => {
  it("requires every selected file to be ready", () => {
    expect(assistantEvidenceReady([{ status: "READY" }, { status: "READY" }])).toBe(true)
    expect(assistantEvidenceReady([{ status: "READY" }, { status: "INDEXING" }])).toBe(false)
    expect(assistantEvidenceReady([{ status: "READY" }, { status: "FAILED" }])).toBe(false)
  })

  it("keeps upload closed without a governed Space and after three selections", () => {
    const base = {
      busy: false,
      uploading: false,
      targetsLoading: false,
      targetsError: false,
      targetCount: 1,
      selectedCount: 0,
    }
    expect(assistantEvidenceUploadDisabledReason({ ...base, targetCount: 0 }))
      .toBe("No Knowledge Space is available for governed upload")
    expect(assistantEvidenceUploadDisabledReason({ ...base, selectedCount: 3 }))
      .toBe("A turn can include at most three files")
    expect(assistantEvidenceUploadDisabledReason(base)).toBeUndefined()
  })

  it("keeps polling through missing responses and stops only at terminal states", () => {
    expect(assistantEvidenceShouldPoll(undefined)).toBe(true)
    expect(assistantEvidenceShouldPoll("PROCESSING")).toBe(true)
    expect(assistantEvidenceShouldPoll("INDEXING")).toBe(true)
    expect(assistantEvidenceShouldPoll("READY")).toBe(false)
    expect(assistantEvidenceShouldPoll("FAILED")).toBe(false)
    expect(assistantEvidenceShouldPoll("UNAVAILABLE")).toBe(false)
  })

  it("provides product-facing evidence status labels", () => {
    expect(assistantEvidenceStatusLabel("PROCESSING")).toBe("Processing")
    expect(assistantEvidenceStatusLabel("INDEXING")).toBe("Indexing")
    expect(assistantEvidenceStatusLabel("READY")).toBe("Ready")
    expect(assistantEvidenceStatusLabel("UNAVAILABLE")).toBe("Unavailable")
    expect(assistantEvidenceStatusLabel(undefined)).toBe("Status unavailable")
  })
})

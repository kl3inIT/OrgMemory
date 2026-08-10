import { describe, expect, it } from "vitest"

import {
  assistantEvidenceReady,
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
})

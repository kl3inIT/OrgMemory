import { describe, expect, it } from "vitest"

import {
  configFrom,
  CONNECTOR_FORMS,
  draftFrom,
  invalidFields,
} from "@/features/admin/connector-forms"

describe("Google Drive connector administration", () => {
  it("serializes the aggregate text budget into the adapter configuration", () => {
    const descriptor = CONNECTOR_FORMS.google_drive
    const draft = draftFrom(descriptor, {})

    expect(configFrom(descriptor, draft)).toMatchObject({
      maxFiles: 500,
      maxBatchBytes: 67_108_864,
    })
  })

  it("blocks an aggregate budget below one admissible Drive response", () => {
    const descriptor = CONNECTOR_FORMS.google_drive
    const draft = draftFrom(descriptor, {})
    draft.maxBatchBytes = "26214399"

    expect(invalidFields(descriptor, draft)).toContain("maxBatchBytes")
  })
})

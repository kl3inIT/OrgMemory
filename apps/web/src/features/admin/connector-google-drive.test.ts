import { describe, expect, it } from "vitest"

import {
  changedBy,
  formatConnectionSetting,
} from "@/features/admin/components/connection-detail-page"
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

  it("presents the stored text budget in operator-readable units", () => {
    const budgetField = CONNECTOR_FORMS.google_drive.advanced.find(
      (field) => field.name === "maxBatchBytes",
    )

    if (!budgetField) {
      throw new Error("Google Drive text budget field is missing")
    }
    expect(formatConnectionSetting(budgetField, 67_108_864)).toBe("64.0 MiB")
  })

  it("describes crawl changes without exposing ledger terminology", () => {
    expect(
      changedBy({
        objectsMaterialized: 42,
        objectsRematerialized: 3,
        objectsRotated: 8,
        objectsRetired: 1,
      }),
    ).toBe("42 added · 3 content updated · 8 permissions updated · 1 retired")
  })
})

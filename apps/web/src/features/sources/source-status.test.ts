import { describe, expect, it } from "vitest"

import { sourceStatusCountFromPage } from "@/features/sources/source-status"
import type { SourcePageResponse } from "@/lib/hey-api"

describe("sourceStatusCountFromPage", () => {
  it("uses envelope totals instead of the loaded page", () => {
    const page: SourcePageResponse = {
      items: [{ id: "one", status: "READY" }],
      total: 87,
      statusCounts: { processing: 12, ready: 70, attention: 5 },
      pageSize: 25,
    }

    expect(sourceStatusCountFromPage(page, "ALL")).toBe(87)
    expect(sourceStatusCountFromPage(page, "PROCESSING")).toBe(12)
    expect(sourceStatusCountFromPage(page, "READY")).toBe(70)
    expect(sourceStatusCountFromPage(page, "ATTENTION")).toBe(5)
  })
})

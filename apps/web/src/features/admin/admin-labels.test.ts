import { describe, expect, it } from "vitest"

import { clearanceLabel, CLEARANCES } from "@/features/admin/admin-labels"

describe("clearance labels", () => {
  it("exposes only the two closed clearance values", () => {
    expect(CLEARANCES).toEqual(["STANDARD", "EXECUTIVE"])
  })

  it("uses business-facing labels", () => {
    expect(clearanceLabel("STANDARD")).toBe("Standard")
    expect(clearanceLabel("EXECUTIVE")).toBe("Executive")
  })
})

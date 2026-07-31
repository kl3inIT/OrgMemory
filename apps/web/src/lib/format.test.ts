import { describe, expect, it } from "vitest"

import { formatBytes, formatDate } from "@/lib/format"

describe("formatBytes", () => {
  it("uses binary math with binary unit labels", () => {
    expect(formatBytes(1024)).toBe("1.0 KiB")
    expect(formatBytes(1024 * 1024)).toBe("1.0 MiB")
  })

  it("supports a caller-specific missing-value fallback", () => {
    expect(formatBytes(undefined, "—")).toBe("—")
  })
})

describe("formatDate", () => {
  it("uses the fallback for missing or invalid values", () => {
    expect(formatDate()).toBe("—")
    expect(formatDate("not-a-date", { fallback: "Never" })).toBe("Never")
  })

  it("can omit the time portion", () => {
    const value = "2026-08-01T12:34:56Z"
    expect(formatDate(value, { dateOnly: true })).not.toContain(":")
  })
})

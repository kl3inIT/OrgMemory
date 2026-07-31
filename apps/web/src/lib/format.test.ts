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

  it("keeps sub-KiB values in bytes", () => {
    expect(formatBytes(0)).toBe("0 B")
    expect(formatBytes(1)).toBe("1 B")
    expect(formatBytes(1023)).toBe("1023 B")
  })

  it("switches units exactly at the MiB boundary", () => {
    expect(formatBytes(1024 * 1024 - 1)).toBe("1024.0 KiB")
    expect(formatBytes(1024 * 1024 + 1)).toBe("1.0 MiB")
  })

  it("rejects negative and non-finite values", () => {
    expect(formatBytes(-1)).toBe("0 B")
    expect(formatBytes(Number.NaN, "—")).toBe("—")
    expect(formatBytes(Number.POSITIVE_INFINITY, "—")).toBe("—")
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

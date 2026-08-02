import { describe, expect, it } from "vitest"

import { sourcePreviewKind } from "@/features/sources/source-preview"

describe("sourcePreviewKind", () => {
  it("allows only exact browser-safe inline representations", () => {
    expect(sourcePreviewKind("application/pdf")).toBe("pdf")
    expect(sourcePreviewKind("image/png")).toBe("image")
    expect(sourcePreviewKind("image/svg+xml")).toBe("download")
    expect(sourcePreviewKind("text/plain; charset=UTF-8")).toBe("text")
    expect(sourcePreviewKind("text/html")).toBe("download")
    expect(sourcePreviewKind("application/json")).toBe("download")
  })
})

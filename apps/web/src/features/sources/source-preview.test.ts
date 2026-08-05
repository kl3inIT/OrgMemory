import { describe, expect, it } from "vitest"

import { sourceFormatLabel, sourcePreviewKind } from "@/features/sources/source-preview"

describe("sourcePreviewKind", () => {
  it("allows only exact browser-safe inline representations", () => {
    expect(sourcePreviewKind("application/pdf")).toBe("pdf")
    expect(sourcePreviewKind("image/png")).toBe("image")
    expect(sourcePreviewKind("image/svg+xml")).toBe("download")
    expect(sourcePreviewKind("text/plain; charset=UTF-8")).toBe("text")
    expect(sourcePreviewKind("text/plain; charset=UTF-8", "text/markdown")).toBe("markdown")
    expect(sourcePreviewKind("text/markdown")).toBe("markdown")
    expect(sourcePreviewKind("application/octet-stream", "text/markdown")).toBe("download")
    expect(sourcePreviewKind("text/plain", "text/html")).toBe("text")
    expect(sourcePreviewKind("text/html")).toBe("download")
    expect(sourcePreviewKind("application/json")).toBe("download")
  })

  it("uses concise product labels instead of raw media types", () => {
    expect(sourceFormatLabel("text/markdown", "support.md")).toBe("Markdown")
    expect(
      sourceFormatLabel(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "handover.docx",
      ),
    ).toBe("Word document")
    expect(sourceFormatLabel("application/octet-stream", "archive.zip")).toBe("ZIP file")
    expect(sourceFormatLabel(undefined, undefined)).toBe("Document")
  })
})

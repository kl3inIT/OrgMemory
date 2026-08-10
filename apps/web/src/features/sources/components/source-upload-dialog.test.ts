import { describe, expect, it } from "vitest"

import {
  ACCEPTED_EXTENSIONS,
  ACCEPTED_FILE_TYPES,
  sourceUploadFileError,
} from "@/features/sources/source-upload-policy"

describe("source upload format policy", () => {
  it("keeps the browser gate open for every admitted organizational format", () => {
    expect(ACCEPTED_EXTENSIONS).toEqual([
      "csv",
      "doc",
      "docx",
      "htm",
      "html",
      "md",
      "odp",
      "ods",
      "odt",
      "pdf",
      "ppt",
      "pptx",
      "rtf",
      "txt",
      "xls",
      "xlsx",
    ])
    expect(ACCEPTED_FILE_TYPES).not.toContain(".zip")
  })

  it("reports the spreadsheet-specific limit", () => {
    const spreadsheet = new File(["fixture"], "headcount.xlsx")
    Object.defineProperty(spreadsheet, "size", { value: 16 * 1024 * 1024 })

    expect(sourceUploadFileError(spreadsheet)).toBe(
      "The .xlsx file must be 15 MB or smaller.",
    )
  })

  it("fails closed for an unsupported archive", () => {
    expect(sourceUploadFileError(new File(["fixture"], "evidence.zip"))).toBe(
      "That file type is not supported.",
    )
  })
})

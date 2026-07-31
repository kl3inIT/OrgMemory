import { describe, expect, it } from "vitest"

import { apiErrorMessage } from "@/lib/api-error"

describe("apiErrorMessage", () => {
  it("prefers a public problem detail", () => {
    expect(
      apiErrorMessage(
        { title: "Conflict", detail: "An Asset already uses this coordinate." },
        "Fallback",
      ),
    ).toBe("An Asset already uses this coordinate.")
  })

  it("supports Error and text responses", () => {
    expect(apiErrorMessage(new Error("Network unavailable"), "Fallback")).toBe(
      "Network unavailable",
    )
    expect(apiErrorMessage("Request rejected", "Fallback")).toBe("Request rejected")
  })

  it("falls back for unknown or empty values", () => {
    expect(apiErrorMessage({ detail: " " }, "Fallback")).toBe("Fallback")
    expect(apiErrorMessage(undefined, "Fallback")).toBe("Fallback")
  })
})

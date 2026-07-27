import { describe, expect, it } from "vitest"

import { parseSkillReference } from "./contracts.js"

describe("parseSkillReference", () => {
  it("accepts one exact server-compatible Skill coordinate", () => {
    expect(parseSkillReference("people/employee-onboarding@1.2.0")).toEqual({
      namespace: "people",
      slug: "employee-onboarding",
      version: "1.2.0",
    })
  })

  it("rejects coordinates the registry cannot resolve", () => {
    expect(() => parseSkillReference("people/employee_onboarding@1.2.0")).toThrow(
      "invalid",
    )
    expect(() =>
      parseSkillReference(`${"a".repeat(129)}/onboarding@1.2.0`),
    ).toThrow("invalid")
    expect(() =>
      parseSkillReference(`people/onboarding@${"1".repeat(65)}`),
    ).toThrow("invalid")
  })
})

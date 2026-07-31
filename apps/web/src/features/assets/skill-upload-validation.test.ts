import { describe, expect, it } from "vitest"

import {
  MAX_SKILL_ARCHIVE_BYTES,
  validateSkillUpload,
} from "@/features/assets/skill-upload-validation"

const validInput = {
  file: { name: "expense-review.zip", size: 1024 },
  namespace: "Finance_Team",
  knowledgeSpaceId: "7e16844e-b0cf-45f3-aeff-d72ac35df782",
}

describe("validateSkillUpload", () => {
  it("normalizes a portable namespace without inspecting package content", () => {
    expect(validateSkillUpload(validInput)).toEqual({
      ok: true,
      namespace: "finance_team",
    })
  })

  it.each([
    [{ ...validInput, file: undefined }, "Choose a Skill ZIP package."],
    [{ ...validInput, file: { name: "SKILL.md", size: 10 } }, "The package must be a ZIP file."],
    [
      { ...validInput, file: { name: "skill.zip", size: MAX_SKILL_ARCHIVE_BYTES + 1 } },
      "The ZIP package must be 20 MiB or smaller.",
    ],
    [{ ...validInput, namespace: "Finance Team" }, "Use lowercase letters and numbers separated by '.', '_', or '-'."],
    [{ ...validInput, knowledgeSpaceId: "" }, "Choose a Knowledge Space."],
  ])("rejects invalid browser preflight input", (input, message) => {
    expect(validateSkillUpload(input)).toEqual({ ok: false, message })
  })
})

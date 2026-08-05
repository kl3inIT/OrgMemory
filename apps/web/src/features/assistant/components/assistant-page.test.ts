import { describe, expect, it } from "vitest"

import { activityLabel } from "@/features/assistant/assistant-activity"

describe("assistant activity labels", () => {
  it("describes progressive Skill disclosure without exposing tool payloads", () => {
    expect(
      activityLabel({ phase: "SKILL_DISCOVERY", state: "ACTIVE" }),
    ).toBe("Looking for a relevant skill…")
    expect(
      activityLabel({
        phase: "SKILL_DISCOVERY",
        state: "COMPLETE",
        evidenceCount: 2,
      }),
    ).toBe("Found 2 available skills")
    expect(
      activityLabel({ phase: "SKILL_ACTIVATION", state: "COMPLETE" }),
    ).toBe("Skill instructions ready")
    expect(
      activityLabel({ phase: "SKILL_RESOURCE", state: "FAILED" }),
    ).toBe("Skill reference unavailable — continuing…")
  })
})

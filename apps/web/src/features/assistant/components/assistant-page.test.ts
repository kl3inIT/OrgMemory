import { describe, expect, it } from "vitest"

import {
  activityLabel,
  hasRenderableAssistantText,
  hasVisibleAssistantOutput,
  reduceSkillReceipts,
} from "@/features/assistant/assistant-activity"

describe("assistant activity labels", () => {
  it("does not treat an unrendered source frame as visible answer output", () => {
    expect(
      hasVisibleAssistantOutput({
        parts: [
          {
            type: "source-url",
            sourceId: "source-1",
            url: "/api/citations/43000000-0000-0000-0000-000000000003/content",
          },
        ],
      }),
    ).toBe(false)
    expect(
      hasVisibleAssistantOutput({
        parts: [{ type: "text", text: "Answer" }],
      }),
    ).toBe(true)
  })

  it("keeps waiting through markdown framing and invisible opening chunks", () => {
    expect(hasRenderableAssistantText("**")).toBe(false)
    expect(hasRenderableAssistantText("```\n")).toBe(false)
    expect(hasRenderableAssistantText("\u200b\ufeff")).toBe(false)
    expect(hasRenderableAssistantText("**Observed")).toBe(true)
    expect(hasRenderableAssistantText("...")).toBe(true)
  })

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
    ).toBe("Preparing the grounded answer…")
    expect(
      activityLabel({ phase: "SKILL_RESOURCE", state: "FAILED" }),
    ).toBe("Skill reference unavailable — continuing…")
  })

  it("creates receipts only from named successful activations", () => {
    const active = reduceSkillReceipts([], {
      phase: "SKILL_ACTIVATION",
      state: "ACTIVE",
      skillOrdinal: 1,
    })
    expect(active).toEqual([
      { ordinal: 1, title: null, activation: "ACTIVE", resource: null },
    ])

    const completed = reduceSkillReceipts(active, {
      phase: "SKILL_ACTIVATION",
      state: "COMPLETE",
      skillOrdinal: 1,
      skillTitle: "Incident response",
    })
    expect(completed).toEqual([
      {
        ordinal: 1,
        title: "Incident response",
        activation: "COMPLETE",
        resource: null,
      },
    ])
    expect(
      reduceSkillReceipts(completed, {
        phase: "SKILL_RESOURCE",
        state: "ACTIVE",
        skillOrdinal: 1,
      }),
    ).toEqual([
      {
        ordinal: 1,
        title: "Incident response",
        activation: "COMPLETE",
        resource: "ACTIVE",
      },
    ])
  })

  it("does not infer a receipt from discovery, failures, or lossy resource events", () => {
    expect(
      reduceSkillReceipts([], {
        phase: "SKILL_DISCOVERY",
        state: "COMPLETE",
        evidenceCount: 2,
      }),
    ).toEqual([])
    expect(
      reduceSkillReceipts([], {
        phase: "SKILL_RESOURCE",
        state: "COMPLETE",
        skillOrdinal: 7,
      }),
    ).toEqual([])
    expect(
      reduceSkillReceipts(
        [{ ordinal: 3, title: null, activation: "ACTIVE", resource: null }],
        { phase: "SKILL_ACTIVATION", state: "FAILED", skillOrdinal: 3 },
      ),
    ).toEqual([])
  })
})

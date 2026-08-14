import { describe, expect, it } from "vitest"

import {
  canOpenGovernance,
  canPublishDirectly,
  initialGovernanceTab,
} from "./governance-policy"

describe("Asset Governance policy", () => {
  it("opens a newly published Draft at its handoff action", () => {
    expect(
      initialGovernanceTab({
        revisions: [],
        reviews: [],
        draft: { id: "draft-1" },
      }),
    ).toBe("draft")
  })

  it("keeps the working copy primary even when legacy review history exists", () => {
    expect(
      initialGovernanceTab({
        revisions: [{ id: "revision-1" }],
        reviews: [{ id: "review-1", state: "IN_REVIEW" }],
        draft: { id: "draft-1" },
      }),
    ).toBe("draft")
  })

  it("opens Release history when no working copy is available", () => {
    expect(
      initialGovernanceTab({
        revisions: [{ id: "revision-1" }],
        reviews: [],
        draft: undefined,
      }),
    ).toBe("releases")
  })

  it("offers direct publication for every Asset type when no legacy review is active", () => {
    const actions = { canPublishDirect: true }

    expect(
      canPublishDirectly(
        { type: "SKILL", reviews: [] },
        actions,
      ),
    ).toBe(true)
    expect(
      canPublishDirectly(
        {
          type: "SKILL",
          reviews: [{ id: "review-1", state: "IN_REVIEW" }],
        },
        actions,
      ),
    ).toBe(false)
    expect(
      canPublishDirectly(
        { type: "PROMPT_TEMPLATE", reviews: [] },
        actions,
      ),
    ).toBe(true)
    expect(
      canPublishDirectly(
        {
          type: "PROMPT_TEMPLATE",
          reviews: [{ id: "review-2", state: "IN_REVIEW" }],
        },
        actions,
      ),
    ).toBe(false)
  })

  it("treats an absent server governance verdict as denied", () => {
    expect(canOpenGovernance(undefined)).toBe(false)
    expect(canOpenGovernance({})).toBe(false)
    expect(canOpenGovernance({ canOpenGovernance: true })).toBe(true)
  })
})

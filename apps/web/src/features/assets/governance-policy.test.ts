import { describe, expect, it } from "vitest"

import {
  canDecideReview,
  canPublishSkillDirectly,
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

  it("opens an active review before historical changes", () => {
    expect(
      initialGovernanceTab({
        revisions: [{ id: "revision-1" }],
        reviews: [{ id: "review-1", state: "IN_REVIEW" }],
        draft: { id: "draft-1" },
      }),
    ).toBe("review")
  })

  it("falls back to Changes when there is no active review", () => {
    expect(
      initialGovernanceTab({
        revisions: [{ id: "revision-1" }],
        reviews: [],
        draft: { id: "draft-1" },
      }),
    ).toBe("changes")
  })

  it("offers direct Skill publication only when no review is active", () => {
    const actions = { canPublishSkill: true }

    expect(
      canPublishSkillDirectly(
        { type: "SKILL", reviews: [] },
        actions,
      ),
    ).toBe(true)
    expect(
      canPublishSkillDirectly(
        {
          type: "SKILL",
          reviews: [{ id: "review-1", state: "IN_REVIEW" }],
        },
        actions,
      ),
    ).toBe(false)
    expect(
      canPublishSkillDirectly(
        { type: "PROMPT_TEMPLATE", reviews: [] },
        actions,
      ),
    ).toBe(false)
  })

  it("does not offer self-approval even when the actor can review", () => {
    const review = {
      id: "review-1",
      revisionId: "revision-1",
      state: "IN_REVIEW" as const,
    }
    const actions = { canReview: true }

    expect(
      canDecideReview(
        review,
        [{ id: "revision-1", createdByUserId: "author-1" }],
        actions,
        "author-1",
      ),
    ).toBe(false)
    expect(
      canDecideReview(
        review,
        [{ id: "revision-1", createdByUserId: "author-1" }],
        actions,
        "reviewer-1",
      ),
    ).toBe(true)
  })
})

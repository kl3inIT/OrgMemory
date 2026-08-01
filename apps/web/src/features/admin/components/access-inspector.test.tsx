import "@testing-library/jest-dom/vitest"

import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { EffectiveAccessDecision } from "@/features/admin/components/access-inspector"

describe("EffectiveAccessDecision", () => {
  it("makes the relationship grant and canonical denial simultaneously visible", () => {
    render(
      <EffectiveAccessDecision
        data={{
          state: "DENIED",
          reasonCode: "CONTENT_POLICY_DENIED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          relationshipReasonCode: "GRANTED",
          contentPolicyState: "DENIED",
          contentPolicyReasonCode: "CANONICAL_RETRIEVAL_POLICY_DENIED",
          policyVersion: "model-1",
          resource: {
            id: "64d065f5-093e-4c88-910d-161b95134a90",
            type: "knowledge_asset",
            label: "Quarterly onboarding runbook",
            contextLabel: "People Operations",
            classification: "INTERNAL",
          },
        }}
      />,
    )

    expect(screen.getByText("Quarterly onboarding runbook")).toBeVisible()
    expect(screen.getByText("People Operations")).toBeVisible()
    expect(screen.getByText("OpenFGA relationship")).toBeVisible()
    expect(screen.getByText("Source ACL and content policy")).toBeVisible()
    expect(screen.getByText("Final content access")).toBeVisible()
    expect(screen.getByText(/Space grants never override source restrictions/)).toBeVisible()
    expect(screen.getAllByText("Allowed")).toHaveLength(1)
    expect(screen.getAllByText("Denied")).toHaveLength(3)
  })

  it("keeps raw relationship identifiers out of the primary relationship-only result", () => {
    render(
      <EffectiveAccessDecision
        data={{
          state: "ALLOWED",
          reasonCode: "GRANTED",
          evaluationKind: "RELATIONSHIP_ONLY",
          relationshipState: "ALLOWED",
          relationshipReasonCode: "GRANTED",
          policyVersion: "model-1",
          path: [
            {
              object: "organization:11111111-1111-1111-1111-111111111111",
              relation: "member",
              kind: "DIRECT",
            },
          ],
        }}
      />,
    )

    expect(screen.getByText("Relationship decision")).toBeVisible()
    expect(screen.getByText(/OpenFGA currently grants this permission/)).toBeVisible()
    expect(screen.queryByText("11111111-1111-1111-1111-111111111111")).not.toBeInTheDocument()
  })

  it("does not collapse an unresolved canonical check into a denial", () => {
    render(
      <EffectiveAccessDecision
        data={{
          state: "UNKNOWN",
          reasonCode: "CONTENT_POLICY_UNKNOWN",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "UNKNOWN",
          contentPolicyReasonCode: "CANONICAL_AUTHORIZATION_UNAVAILABLE",
          resource: { label: "Security handbook", contextLabel: "Operations" },
        }}
      />,
    )

    expect(screen.getByText(/Treat this as unresolved, not as permission/)).toBeVisible()
    expect(screen.getAllByText("Unknown").length).toBeGreaterThanOrEqual(1)
  })
})

import "@testing-library/jest-dom/vitest"

import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { EffectiveAccessDecision } from "@/features/admin/components/access-inspector"

const allowedPath = [
  {
    object: "knowledge_asset:64d065f5-093e-4c88-910d-161b95134a90",
    objectType: "knowledge_asset",
    objectLabel: "DOC037 — 2026 financial forecast",
    relation: "can_view",
    kind: "INHERITED",
  },
  {
    object: "knowledge_space:99999999-093e-4c88-910d-161b95134a90",
    objectType: "knowledge_space",
    objectLabel: "Executive Knowledge",
    relation: "viewer",
    kind: "DIRECT",
  },
  {
    object: "organizational_unit:77777777-093e-4c88-910d-161b95134a90",
    objectType: "organizational_unit",
    objectLabel: "Executive Office",
    relation: "member",
    kind: "DIRECT",
  },
]

const resource = {
  id: "64d065f5-093e-4c88-910d-161b95134a90",
  type: "knowledge_asset",
  label: "DOC037 — 2026 financial forecast",
  contextLabel: "Executive Knowledge",
  classification: "RESTRICTED",
}

function expectInBothResponsivePresentations(text: string) {
  expect(screen.getAllByText(text)).toHaveLength(2)
}

describe("EffectiveAccessDecision", () => {
  it("explains an allowed document through a named department assignment", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "EFFECTIVE_ACCESS_ALLOWED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          relationshipReasonCode: "GRANTED",
          contentPolicyState: "ALLOWED",
          contentPolicyReasonCode: "CANONICAL_RETRIEVAL_POLICY_ALLOWED",
          policyVersion: "model-1",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: allowedPath,
          resource,
        }}
      />,
    )

    expect(screen.getByText("Current access")).toBeVisible()
    expect(screen.getByText("Allowed")).toBeVisible()
    expect(screen.getByText(/Vũ Thị Lan can use this document in secure search/)).toBeVisible()
    expect(screen.getByText("Access granted through")).toBeVisible()
    expectInBothResponsivePresentations("Department member")
    expectInBothResponsivePresentations("Executive Office")
    expect(screen.getAllByText("Executive Knowledge").length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText("Document availability")).toBeVisible()
    expect(screen.getByText("Available")).toBeVisible()
    expect(screen.getByText(/Checked against current permissions/)).toBeVisible()
    expect(screen.getByText(/Evaluated .*2026/)).toBeVisible()

    expect(screen.queryByText(/OpenFGA/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Technical details/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/64d065f5/)).not.toBeInTheDocument()
    expect(screen.queryByText(/CANONICAL_RETRIEVAL/)).not.toBeInTheDocument()
  })

  it("separates an eligible user from a currently unavailable document", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "DENIED",
          reasonCode: "CONTENT_POLICY_DENIED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          relationshipReasonCode: "GRANTED",
          contentPolicyState: "DENIED",
          contentPolicyReasonCode: "CANONICAL_RETRIEVAL_POLICY_DENIED",
          policyVersion: "model-1",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: allowedPath,
          resource,
        }}
      />,
    )

    expect(screen.getByText("Denied")).toBeVisible()
    expect(screen.getByText(/has a valid assignment, but this document cannot currently be used/)).toBeVisible()
    expectInBothResponsivePresentations("Department member")
    expect(screen.getByText("Unavailable")).toBeVisible()
    expect(screen.getByText(/Current document restrictions prevent secure search/)).toBeVisible()
  })

  it("explains a missing user assignment without pretending the document was evaluated", () => {
    render(
      <EffectiveAccessDecision
        userName="Lê Minh Châu"
        data={{
          state: "DENIED",
          reasonCode: "RELATIONSHIP_DENIED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "DENIED",
          relationshipReasonCode: "RELATIONSHIP_DENIED",
          contentPolicyState: "UNKNOWN",
          contentPolicyReasonCode: "NOT_EVALUATED_RELATIONSHIP_NOT_ALLOWED",
          policyVersion: "model-1",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [],
          resource,
        }}
      />,
    )

    expect(screen.getByText(/Lê Minh Châu has no direct or inherited permission/)).toBeVisible()
    expect(screen.getByText("No current assignment")).toBeVisible()
    expect(screen.getByText("Not checked")).toBeVisible()
    expect(screen.getByText(/Document availability was not evaluated/)).toBeVisible()
  })

  it("names an organization role instead of flattening it into direct access", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "EFFECTIVE_ACCESS_ALLOWED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [
            allowedPath[0],
            allowedPath[1],
            allowedPath[2],
            {
              object: "organization:55555555-093e-4c88-910d-161b95134a90",
              objectType: "organization",
              objectLabel: "OrgMemory Demo",
              relation: "knowledge_reader",
              kind: "DIRECT",
            },
          ],
          resource,
        }}
      />,
    )

    expectInBothResponsivePresentations("Assigned role")
    expectInBothResponsivePresentations("Knowledge reader")
    expect(screen.queryByText("Organization member")).not.toBeInTheDocument()
    expect(screen.queryByText("Direct access")).not.toBeInTheDocument()
  })

  it("labels a proven direct grant as direct access", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "EFFECTIVE_ACCESS_ALLOWED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [{ ...allowedPath[0], kind: "DIRECT" }],
          resource,
        }}
      />,
    )

    expectInBothResponsivePresentations("Direct access")
    expect(screen.queryByText("Inherited access")).not.toBeInTheDocument()
  })

  it("does not accept a different same-type object as proof of direct access", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "EFFECTIVE_ACCESS_ALLOWED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [{
            ...allowedPath[0],
            object: "knowledge_asset:different-document",
            kind: "DIRECT",
          }],
          resource,
        }}
      />,
    )

    expect(screen.getByText("Assignment path unavailable")).toBeVisible()
    expect(screen.queryByText("Direct access")).not.toBeInTheDocument()
  })

  it("reports an unreadable allowed path without inventing an assignment", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "EFFECTIVE_ACCESS_ALLOWED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [allowedPath[0], allowedPath[1]],
          resource,
        }}
      />,
    )

    expect(screen.getByText("Assignment source")).toBeVisible()
    expect(screen.getByText("Assignment path unavailable")).toBeVisible()
    expect(screen.getByText(/proves access, but not whether its source is direct or inherited/)).toBeVisible()
    expect(screen.queryByText("Direct access")).not.toBeInTheDocument()
    expect(screen.queryByText("Inherited access")).not.toBeInTheDocument()
  })

  it("reports an unresolved group-derived path as unavailable", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "EFFECTIVE_ACCESS_ALLOWED",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [
            allowedPath[0],
            allowedPath[1],
            {
              object: "group:connected-group-7",
              objectType: "group",
              relation: "member",
              kind: "DIRECT",
            },
          ],
          resource,
        }}
      />,
    )

    expect(screen.getByText("Assignment path unavailable")).toBeVisible()
    expect(screen.queryByText("Direct access")).not.toBeInTheDocument()
    expect(screen.queryByText("Inherited access")).not.toBeInTheDocument()
  })

  it("reports an allowed empty path as unavailable", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "GRANTED_PATH_UNAVAILABLE",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          relationshipReasonCode: "GRANTED_PATH_UNAVAILABLE",
          contentPolicyState: "ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [],
          resource,
        }}
      />,
    )

    expect(screen.getByText("Assignment path unavailable")).toBeVisible()
    expect(screen.queryByText("Direct access")).not.toBeInTheDocument()
    expect(screen.queryByText("Inherited access")).not.toBeInTheDocument()
  })

  it("does not turn an unresolved relationship into a missing assignment", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "UNKNOWN",
          reasonCode: "RELATIONSHIP_UNKNOWN",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "UNKNOWN",
          relationshipReasonCode: "AUTHORIZATION_UNAVAILABLE",
          contentPolicyState: "UNKNOWN",
          contentPolicyReasonCode: "NOT_EVALUATED_RELATIONSHIP_NOT_ALLOWED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [],
          resource,
        }}
      />,
    )

    expect(screen.getByText("Assignment not resolved")).toBeVisible()
    expect(screen.getByText(/Permission evaluation did not complete/)).toBeVisible()
    expect(screen.queryByText("No current assignment")).not.toBeInTheDocument()
    expect(screen.queryByText(/user has no current assignment/)).not.toBeInTheDocument()
  })

  it("uses permission language for a relationship-only resource check", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "ALLOWED",
          reasonCode: "RELATIONSHIP_ALLOWED",
          evaluationKind: "RELATIONSHIP_ONLY",
          relationshipState: "ALLOWED",
          relationshipReasonCode: "GRANTED",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: [{
            object: "organization:55555555-093e-4c88-910d-161b95134a90",
            objectType: "organization",
            objectLabel: "OrgMemory Demo",
            relation: "administrator",
            kind: "DIRECT",
          }],
          resource: {
            id: "55555555-093e-4c88-910d-161b95134a90",
            type: "organization",
            label: "OrgMemory Demo",
          },
        }}
      />,
    )

    expect(screen.getByText(/currently has this permission for the selected resource/)).toBeVisible()
    expect(screen.queryByText(/use this document in secure search/)).not.toBeInTheDocument()
    expect(screen.getByText("Not applicable")).toBeVisible()
  })

  it("keeps an unresolved check distinct from a denial", () => {
    render(
      <EffectiveAccessDecision
        userName="Vũ Thị Lan"
        data={{
          state: "UNKNOWN",
          reasonCode: "CONTENT_POLICY_UNKNOWN",
          evaluationKind: "CANONICAL_CONTENT",
          relationshipState: "ALLOWED",
          contentPolicyState: "UNKNOWN",
          contentPolicyReasonCode: "CANONICAL_AUTHORIZATION_UNAVAILABLE",
          evaluatedAt: "2026-08-15T14:32:00Z",
          path: allowedPath,
          resource,
        }}
      />,
    )

    expect(screen.getByText("Unknown")).toBeVisible()
    expect(screen.getByText(/could not produce a current access result/)).toBeVisible()
    expect(screen.queryByText(/Vũ Thị Lan cannot use/)).not.toBeInTheDocument()
  })
})

import { describe, expect, it } from "vitest"

import {
  buildPromptAssetDraft,
  createEmptyPromptForm,
  extractPromptPlaceholders,
  parsePromptDraft,
  slugifyPromptTitle,
} from "@/features/assets/prompt-template-form"

const baseForm = {
  ...createEmptyPromptForm(),
  title: "Support ticket classifier",
  summary: "Classifies an incoming support ticket.",
  namespace: "support",
  slug: "support-ticket-classifier",
  knowledgeSpaceId: "7e16844e-b0cf-45f3-aeff-d72ac35df782",
  objective: "Classify incoming support tickets",
  audience: "L1 Support",
  textTemplate: "Classify this ticket:\n\n{{ticket_text}}\n\nReturn a concise category.",
  variables: [
    {
      key: "ticket_text",
      name: "ticket_text",
      type: "STRING" as const,
      required: true,
      sensitive: true,
      defaultValue: "",
      pattern: "",
      allowedValues: "",
    },
  ],
}

describe("Prompt Template browser contract", () => {
  it("extracts unique lower-snake-case placeholders in source order", () => {
    expect(
      extractPromptPlaceholders(
        "{{ticket_text}} {{priority}} {{ticket_text}} {{ Not_valid }} {{also-valid}}",
      ),
    ).toEqual(["ticket_text", "priority"])
  })

  it("creates a stable portable slug from a visible title", () => {
    expect(slugifyPromptTitle("  Support Ticket / Classifier  ")).toBe(
      "support-ticket-classifier",
    )
    expect(slugifyPromptTitle("Đánh giá yêu cầu")).toBe("danh-gia-yeu-cau")
  })

  it("serializes a complete atomic Draft with typed values and optional grounding", () => {
    const result = buildPromptAssetDraft({
      ...baseForm,
      grounding: "OPTIONAL",
      knowledgeRequirements: "support runbook\nSLA escalation policy",
      variables: [
        baseForm.variables[0],
        {
          key: "urgent",
          name: "urgent",
          type: "BOOLEAN",
          required: false,
          sensitive: false,
          defaultValue: "false",
          pattern: "",
          allowedValues: "",
        },
      ],
      evaluationCases: [
        {
          key: "password-reset",
          name: "Password reset",
          values: { ticket_text: "Synthetic password reset request", urgent: "true" },
          expectedContains: "access\npassword",
          forbiddenContains: "secret",
          sensitiveFixtureAcknowledged: true,
        },
      ],
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.request).toMatchObject({
      type: "PROMPT_TEMPLATE",
      namespace: "support",
      slug: "support-ticket-classifier",
      knowledgeSpaceId: baseForm.knowledgeSpaceId,
      draft: {
        title: baseForm.title,
        summary: baseForm.summary,
        classification: "INTERNAL",
        schemaVersion: "1",
      },
    })
    expect(JSON.parse(result.request.draft.payload)).toEqual({
      objective: baseForm.objective,
      audience: baseForm.audience,
      useWhen: [],
      doNotUseWhen: [],
      textTemplate: baseForm.textTemplate,
      messages: [],
      variables: [
        {
          name: "ticket_text",
          type: "STRING",
          required: true,
          sensitive: true,
          pattern: "",
          allowedValues: [],
        },
        {
          name: "urgent",
          type: "BOOLEAN",
          required: false,
          defaultValue: false,
          sensitive: false,
          pattern: "",
          allowedValues: [],
        },
      ],
      outputContract: {},
      dataPolicy: { retainRawVariables: false, retainRawOutput: false },
      compatibility: ["chat"],
      knowledgeRequirements: ["support runbook", "SLA escalation policy"],
      evaluationCases: [
        {
          name: "Password reset",
          variables: {
            ticket_text: "Synthetic password reset request",
            urgent: true,
          },
          expectedContains: ["access", "password"],
          forbiddenContains: ["secret"],
        },
      ],
      knownLimitations: "",
    })
  })

  it("rejects unresolved placeholders and sensitive persisted fixtures without acknowledgement", () => {
    const missingVariable = buildPromptAssetDraft({ ...baseForm, variables: [] })
    expect(missingVariable).toEqual({
      ok: false,
      message: "Define every prompt placeholder before saving: ticket_text.",
    })

    const sensitiveFixture = buildPromptAssetDraft({
      ...baseForm,
      evaluationCases: [
        {
          key: "case-1",
          name: "Synthetic case",
          values: { ticket_text: "example" },
          expectedContains: "category",
          forbiddenContains: "",
          sensitiveFixtureAcknowledged: false,
        },
      ],
    })
    expect(sensitiveFixture).toEqual({
      ok: false,
      message:
        "Confirm that case \"Synthetic case\" uses synthetic data for sensitive variables.",
    })
  })

  it("rejects invalid typed fixtures, missing required inputs, duplicate cases, and the case limit", () => {
    const integerVariable = {
      ...baseForm.variables[0],
      type: "INTEGER" as const,
      sensitive: false,
    }
    const evaluationCase = {
      key: "case-1",
      name: "Boundary case",
      values: { ticket_text: "not-an-integer" },
      expectedContains: "category",
      forbiddenContains: "",
      sensitiveFixtureAcknowledged: false,
    }

    expect(
      buildPromptAssetDraft({
        ...baseForm,
        variables: [integerVariable],
        evaluationCases: [evaluationCase],
      }),
    ).toEqual({ ok: false, message: 'Variable "ticket_text" must be an integer.' })

    expect(
      buildPromptAssetDraft({
        ...baseForm,
        variables: [integerVariable],
        evaluationCases: [{ ...evaluationCase, values: {} }],
      }),
    ).toEqual({ ok: false, message: 'Case "Boundary case" needs variable "ticket_text".' })

    expect(
      buildPromptAssetDraft({
        ...baseForm,
        variables: [integerVariable],
        evaluationCases: [
          { ...evaluationCase, values: { ticket_text: "1", removed_variable: "stale" } },
        ],
      }),
    ).toEqual({
      ok: false,
      message: 'Case "Boundary case" has an unknown variable "removed_variable".',
    })

    expect(
      buildPromptAssetDraft({
        ...baseForm,
        variables: [integerVariable],
        evaluationCases: [
          { ...evaluationCase, values: { ticket_text: "1" } },
          { ...evaluationCase, key: "case-2", values: { ticket_text: "2" } },
        ],
      }),
    ).toEqual({ ok: false, message: 'Test case names must be unique.' })

    expect(
      buildPromptAssetDraft({
        ...baseForm,
        evaluationCases: Array.from({ length: 11 }, (_, index) => ({
          ...evaluationCase,
          key: `case-${index}`,
          name: `Case ${index}`,
        })),
      }),
    ).toEqual({ ok: false, message: "A Prompt supports at most 10 test cases." })
  })

  it("maps None to no Knowledge query requirements", () => {
    const result = buildPromptAssetDraft({
      ...baseForm,
      grounding: "NONE",
      knowledgeRequirements: "must not be serialized",
    })
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(JSON.parse(result.request.draft.payload).knowledgeRequirements).toEqual([])
  })

  it("recognizes message-based Drafts without creating an editable text form", () => {
    expect(
      parsePromptDraft(
        JSON.stringify({
          objective: "Help support",
          audience: "Support",
          textTemplate: "",
          messages: [
            { role: "SYSTEM", content: "Follow policy." },
            { role: "USER", content: "{{ticket_text}}" },
          ],
          variables: [],
          evaluationCases: [],
          knowledgeRequirements: [],
        }),
      ),
    ).toEqual({
      kind: "messages",
      messages: [
        { role: "SYSTEM", content: "Follow policy." },
        { role: "USER", content: "{{ticket_text}}" },
      ],
    })
  })
})

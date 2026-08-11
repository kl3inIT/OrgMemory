import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { PromptTemplateEditor } from "@/features/assets/components/prompt-template-editor"
import { createEmptyPromptForm } from "@/features/assets/prompt-template-form"

describe("PromptTemplateEditor", () => {
  it("uses the shared Asset identity and separates Prompt usage metadata from access", () => {
    render(
      <PromptTemplateEditor
        value={createEmptyPromptForm()}
        onChange={vi.fn()}
        onSubmit={vi.fn()}
        submitLabel="Create private Draft"
      />,
    )

    expect(screen.getByLabelText("Prompt name")).toBeInTheDocument()
    expect(screen.getByLabelText("Description")).toBeInTheDocument()
    expect(screen.queryByLabelText("Summary")).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Audience")).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Objective")).not.toBeInTheDocument()

    expect(screen.getByRole("heading", { name: "Usage contract" })).toBeInTheDocument()
    expect(screen.getByLabelText("Task objective")).toBeInTheDocument()
    expect(screen.getByLabelText("Intended users")).toBeInTheDocument()
    expect(
      screen.getByText("Descriptive metadata only. This does not grant access."),
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Create private Draft" })).toBeInTheDocument()
  })

  it("offers truthful optional grounding without Required or a grounding Scope selector", async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const value = {
      ...createEmptyPromptForm(),
      title: "Support classifier",
      grounding: "NONE" as const,
    }

    const { rerender } = render(
      <PromptTemplateEditor
        value={value}
        onChange={onChange}
        onSubmit={vi.fn()}
        submitLabel="Create private Draft"
      />,
    )

    expect(screen.getByRole("button", { name: "None" })).toHaveAttribute("aria-pressed", "true")
    expect(screen.getByRole("button", { name: /^Optional$/ })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Required" })).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Scope")).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: /^Optional$/ }))
    expect(onChange).toHaveBeenLastCalledWith({ ...value, grounding: "OPTIONAL" })

    rerender(
      <PromptTemplateEditor
        value={{ ...value, grounding: "OPTIONAL" }}
        onChange={onChange}
        onSubmit={vi.fn()}
        submitLabel="Create private Draft"
      />,
    )
    expect(screen.getByLabelText("Knowledge requirements")).toBeInTheDocument()
    expect(screen.getByText(/can still run without evidence/i)).toBeInTheDocument()
  })

  it("discovers a placeholder and creates its variable row on request", async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const value = {
      ...createEmptyPromptForm(),
      textTemplate: "Classify {{ticket_text}}",
    }
    render(
      <PromptTemplateEditor
        value={value}
        onChange={onChange}
        onSubmit={vi.fn()}
        submitLabel="Save working copy"
      />,
    )

    expect(screen.getByText(/Define detected placeholders: ticket_text/i)).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Add detected" }))
    expect(onChange).toHaveBeenCalledWith({
      ...value,
      variables: [
        expect.objectContaining({ name: "ticket_text", type: "STRING", required: true }),
      ],
    })
  })

  it("reorders persisted test cases explicitly", async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const first = {
      key: "first",
      name: "First",
      values: {},
      expectedContains: "one",
      forbiddenContains: "",
      sensitiveFixtureAcknowledged: false,
    }
    const second = { ...first, key: "second", name: "Second", expectedContains: "two" }
    const value = {
      ...createEmptyPromptForm(),
      evaluationCases: [first, second],
    }
    render(
      <PromptTemplateEditor
        value={value}
        onChange={onChange}
        onSubmit={vi.fn()}
        submitLabel="Save working copy"
      />,
    )

    await user.click(screen.getByRole("button", { name: "Move test case 2 up" }))
    expect(onChange).toHaveBeenCalledWith({ ...value, evaluationCases: [second, first] })
  })
})

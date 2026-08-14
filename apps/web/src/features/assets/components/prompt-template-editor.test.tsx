import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest"

import { PromptTemplateEditor } from "@/features/assets/components/prompt-template-editor"
import { createEmptyPromptForm } from "@/features/assets/prompt-template-form"

describe("PromptTemplateEditor", () => {
  beforeAll(() => {
    vi.stubGlobal("ResizeObserver", class {
      observe() {}
      unobserve() {}
      disconnect() {}
    })
  })

  afterAll(() => vi.unstubAllGlobals())

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
    expect(screen.getByLabelText("Description")).toHaveAttribute(
      "aria-describedby",
      "prompt-summary-hint",
    )
    expect(screen.queryByLabelText("Summary")).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Audience")).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Objective")).not.toBeInTheDocument()

    expect(screen.getByRole("heading", { name: "Usage contract" })).toBeInTheDocument()
    expect(screen.getByRole("group", { name: "Knowledge grounding mode" })).toBeInTheDocument()
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

  it("migrates persisted test values when a variable is renamed", () => {
    const onChange = vi.fn()
    const variable = {
      key: "ticket-variable",
      name: "ticket_text",
      type: "STRING" as const,
      required: true,
      sensitive: false,
      defaultValue: "",
      pattern: "",
      allowedValues: "",
    }
    const evaluationCase = {
      key: "case-1",
      name: "Password reset",
      values: { ticket_text: "Synthetic request", unaffected: "keep" },
      expectedContains: "access",
      forbiddenContains: "",
      sensitiveFixtureAcknowledged: false,
    }
    const value = {
      ...createEmptyPromptForm(),
      variables: [variable],
      evaluationCases: [evaluationCase],
    }

    render(
      <PromptTemplateEditor
        value={value}
        onChange={onChange}
        onSubmit={vi.fn()}
        submitLabel="Save working copy"
      />,
    )

    fireEvent.change(screen.getByDisplayValue("ticket_text"), {
      target: { value: "request_text" },
    })

    expect(onChange).toHaveBeenLastCalledWith({
      ...value,
      variables: [{ ...variable, name: "request_text" }],
      evaluationCases: [
        {
          ...evaluationCase,
          values: { request_text: "Synthetic request", unaffected: "keep" },
        },
      ],
    })
  })

  it("distinguishes target loading and permission disabling from submission", () => {
    render(
      <PromptTemplateEditor
        value={{ ...createEmptyPromptForm(), textTemplate: "Classify {{ticket_text}}" }}
        onChange={vi.fn()}
        onSubmit={vi.fn()}
        submitLabel="Create private Draft"
        disabled
        spacesLoading
      />,
    )

    expect(screen.getByLabelText("Knowledge Space")).toHaveValue("Loading creation targets")
    expect(screen.getByRole("button", { name: "Create private Draft" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Add detected" })).toBeDisabled()
  })

  it("shows the loaded empty creation-target state distinctly", () => {
    render(
      <PromptTemplateEditor
        value={createEmptyPromptForm()}
        onChange={vi.fn()}
        onSubmit={vi.fn()}
        submitLabel="Create private Draft"
        spaces={[]}
      />,
    )

    expect(screen.getByLabelText("Knowledge Space")).toHaveTextContent("No creation target")
    expect(screen.getByLabelText("Knowledge Space")).toBeDisabled()
  })

  it("offers an accessible retry action when creation targets fail", async () => {
    const user = userEvent.setup()
    const retry = vi.fn()
    render(
      <PromptTemplateEditor
        value={createEmptyPromptForm()}
        onChange={vi.fn()}
        onSubmit={vi.fn()}
        submitLabel="Create private Draft"
        disabled
        error="Creation targets could not be loaded."
        errorAction={{ label: "Try again", onClick: retry }}
      />,
    )
    await waitFor(() => expect(screen.getByRole("alert")).toHaveFocus())

    await user.click(screen.getByRole("button", { name: "Try again" }))

    expect(retry).toHaveBeenCalledOnce()
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Creation targets could not be loaded.",
    )
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

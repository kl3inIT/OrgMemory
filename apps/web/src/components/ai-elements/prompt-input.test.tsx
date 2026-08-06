import { fireEvent, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest"

import {
  PromptInput,
  PromptInputActionMenu,
  PromptInputActionMenuContent,
  PromptInputActionMenuItem,
  PromptInputActionMenuTrigger,
  PromptInputBody,
  PromptInputButton,
  PromptInputFooter,
  PromptInputHeader,
  type PromptInputProps,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
} from "@/components/ai-elements/prompt-input"
import { TooltipProvider } from "@/components/ui/tooltip"

class TestResizeObserver implements ResizeObserver {
  disconnect() {}
  observe() {}
  unobserve() {}
}

beforeAll(() => vi.stubGlobal("ResizeObserver", TestResizeObserver))
afterAll(() => vi.unstubAllGlobals())

function renderPromptInput({
  onSubmit = vi.fn(),
  onStop,
  status,
}: {
  onSubmit?: PromptInputProps["onSubmit"]
  onStop?: () => void
  status?: "submitted" | "streaming" | "ready" | "error"
} = {}) {
  return render(
    <TooltipProvider>
      <PromptInput onSubmit={onSubmit}>
        <PromptInputHeader data-testid="header">Context</PromptInputHeader>
        <PromptInputBody>
          <PromptInputTextarea aria-label="Message" />
        </PromptInputBody>
        <PromptInputFooter>
          <PromptInputTools>
            <PromptInputButton tooltip={{ content: "Choose model", shortcut: "Ctrl M" }}>
              <span aria-hidden="true">◎</span>
              <span>Model</span>
            </PromptInputButton>
          </PromptInputTools>
          <PromptInputSubmit status={status} onStop={onStop} />
        </PromptInputFooter>
      </PromptInput>
    </TooltipProvider>,
  )
}

describe("PromptInput", () => {
  it("submits the text-only local contract with Enter", async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    renderPromptInput({ onSubmit })

    await user.type(screen.getByRole("textbox", { name: "Message" }), "Leave policy{Enter}")

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit.mock.calls[0]?.[0]).toEqual({ files: [], text: "Leave policy" })
    expect(onSubmit.mock.calls[0]?.[1]).toMatchObject({ type: "submit" })
  })

  it("keeps Shift+Enter and IME Enter inside the textarea", async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    renderPromptInput({ onSubmit })
    const textarea = screen.getByRole("textbox", { name: "Message" })

    await user.type(textarea, "Line one{Shift>}{Enter}{/Shift}Line two")
    fireEvent.compositionStart(textarea)
    fireEvent.keyDown(textarea, { key: "Enter", isComposing: true })
    fireEvent.compositionEnd(textarea)

    expect(onSubmit).not.toHaveBeenCalled()
    expect(textarea).toHaveValue("Line one\nLine two")
  })

  it("stops a streaming turn without submitting the form", async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    const onStop = vi.fn()
    renderPromptInput({ onSubmit, onStop, status: "streaming" })

    await user.click(screen.getByRole("button", { name: "Stop" }))

    expect(onStop).toHaveBeenCalledTimes(1)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it("provides header composition and action-button tooltip metadata", async () => {
    const user = userEvent.setup()
    renderPromptInput()

    expect(screen.getByTestId("header")).toHaveAttribute("data-align", "block-end")
    const action = screen.getByRole("button", { name: "Model" })
    expect(action).toHaveAttribute("type", "button")
    expect(action).toHaveAttribute("data-size", "sm")

    await user.hover(action)
    expect(await screen.findByText("Choose model")).toBeVisible()
    expect(screen.getByText("Ctrl M")).toBeVisible()
  })

  it("provides a side-effect-free action menu primitive", async () => {
    const user = userEvent.setup()
    render(
      <PromptInputActionMenu>
        <PromptInputActionMenuTrigger aria-label="More prompt actions" />
        <PromptInputActionMenuContent>
          <PromptInputActionMenuItem>Use approved template</PromptInputActionMenuItem>
        </PromptInputActionMenuContent>
      </PromptInputActionMenu>,
    )

    await user.click(screen.getByRole("button", { name: "More prompt actions" }))

    expect(screen.getByRole("menuitem", { name: "Use approved template" })).toBeVisible()
  })
})

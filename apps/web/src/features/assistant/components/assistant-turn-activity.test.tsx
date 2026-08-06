import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { AssistantTurnActivity } from "@/features/assistant/components/assistant-turn-activity"

describe("AssistantTurnActivity", () => {
  it("keeps one activity surface through the waiting-to-answer handoff", () => {
    const receipts = [
      {
        ordinal: 1,
        title: "Incident response",
        activation: "COMPLETE" as const,
        resource: null,
      },
    ]
    const { rerender } = render(
      <AssistantTurnActivity
        receipts={receipts}
        settled={false}
        waitingLabel="Preparing the grounded answer…"
      />,
    )

    const surface = screen.getByLabelText("Current turn activity")
    expect(screen.getByRole("status")).toHaveTextContent("Preparing the grounded answer…")
    expect(surface).toContainElement(screen.getByText("Incident response"))

    rerender(
      <AssistantTurnActivity receipts={receipts} settled waitingLabel={null} />,
    )

    expect(screen.getByLabelText("Current turn activity")).toBe(surface)
    expect(screen.queryByRole("status")).toBeNull()
    expect(surface).toContainElement(screen.getByText("Incident response"))
  })
})

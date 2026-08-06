import { render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { AssistantSkillActivity } from "@/features/assistant/components/assistant-skill-activity"

describe("AssistantSkillActivity", () => {
  it("stays open while active and collapses when answer output settles", async () => {
    const receipts = [
      {
        ordinal: 1,
        title: "Incident response",
        activation: "COMPLETE" as const,
        resource: "ACTIVE" as const,
      },
    ]
    const { rerender } = render(
      <AssistantSkillActivity receipts={receipts} settled={false} />,
    )

    const trigger = screen.getByRole("button", {
      name: "Using Incident response skill",
    })
    expect(trigger).toHaveAttribute("aria-expanded", "true")
    expect(screen.getByText("Reading a skill reference")).toBeVisible()

    rerender(<AssistantSkillActivity receipts={receipts} settled />)
    await waitFor(() => expect(trigger).toHaveAttribute("aria-expanded", "false"))
  })

  it("renders the bounded title as plain text", () => {
    render(
      <AssistantSkillActivity
        receipts={[
          {
            ordinal: 1,
            title: '<img src="x" onerror="alert(1)">',
            activation: "COMPLETE",
            resource: null,
          },
        ]}
        settled
      />,
    )

    expect(screen.getByText('<img src="x" onerror="alert(1)">')).toBeVisible()
    expect(document.querySelector("img")).toBeNull()
  })
})

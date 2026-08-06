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

  it("does not offer an empty disclosure for activation-only receipts", () => {
    render(
      <AssistantSkillActivity
        receipts={[
          {
            ordinal: 1,
            title: "Incident response",
            activation: "COMPLETE",
            resource: null,
          },
        ]}
        settled
      />,
    )

    expect(screen.getByText("Incident response")).toBeVisible()
    expect(screen.queryByRole("button")).toBeNull()
    expect(screen.queryByText("Skill instructions loaded")).toBeNull()
  })

  it("opens a failed reference detail instead of burying it", () => {
    render(
      <AssistantSkillActivity
        receipts={[
          {
            ordinal: 1,
            title: "Incident response",
            activation: "COMPLETE",
            resource: "FAILED",
          },
        ]}
        settled
      />,
    )

    expect(
      screen.getByRole("button", { name: "Using Incident response skill" }),
    ).toHaveAttribute("aria-expanded", "true")
    expect(screen.getByText("Skill reference unavailable")).toBeVisible()
  })
})

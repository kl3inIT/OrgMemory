import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it } from "vitest"

import { SkillConsumerInstaller } from "@/features/assets/components/skill-consumer-installer"

describe("SkillConsumerInstaller", () => {
  it("opens one target-specific exact installer without claiming runtime compatibility", async () => {
    const user = userEvent.setup()

    render(
      <SkillConsumerInstaller reference="productivity/decision-record-writer@1.0.0" />,
    )

    expect(screen.getByText("Runtime behavior not certified")).toBeVisible()
    await user.click(screen.getByRole("button", { name: "Install with" }))
    expect(screen.getAllByRole("menuitem")).toHaveLength(2)

    await user.click(screen.getByRole("menuitem", { name: /Claude Code/ }))

    expect(screen.getByRole("dialog")).toBeVisible()
    expect(screen.getByRole("heading", { name: "Install with Claude Code" })).toBeVisible()
    expect(screen.getByText("Install supported")).toBeVisible()
    expect(screen.getByText(".claude/skills/decision-record-writer")).toBeVisible()
    expect(screen.getAllByText("Runtime behavior not certified").length).toBeGreaterThan(0)

    await user.click(screen.getByRole("tab", { name: "Use CLI" }))
    expect(
      screen.getByText(
        "orgmemory skill add productivity/decision-record-writer@1.0.0 --agent claude-code",
      ),
    ).toBeVisible()
    expect(screen.queryByText(/--agent codex/)).not.toBeInTheDocument()
  })
})

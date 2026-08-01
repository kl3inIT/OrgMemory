import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { buildSkillInstallHandoff } from "@/features/assets/agent-handoff/skill-agent-handoffs"
import { AgentHandoffPanel } from "@/features/assets/components/agent-handoff-panel"
import { copyWithToast } from "@/lib/copy"

vi.mock("@/lib/copy", () => ({
  copyWithToast: vi.fn().mockResolvedValue(true),
}))

describe("AgentHandoffPanel", () => {
  it("always renders the confirmation boundary and copies exact agent instructions", async () => {
    const user = userEvent.setup()
    const handoff = buildSkillInstallHandoff("productivity/decision-record-writer@1.0.0")

    render(<AgentHandoffPanel handoff={handoff} />)

    expect(screen.getByRole("alert")).toHaveTextContent(handoff.confirmationBoundary)
    expect(screen.getByText("After completion")).toBeVisible()
    expect(screen.getByText("assets:read")).toBeVisible()

    await user.click(screen.getByRole("button", { name: "Copy agent prompt" }))
    expect(copyWithToast).toHaveBeenLastCalledWith(handoff.promptTemplate, "Agent prompt")

    await user.click(screen.getByRole("tab", { name: "Use CLI" }))
    await user.click(screen.getByRole("button", { name: "Copy Codex command" }))
    expect(copyWithToast).toHaveBeenLastCalledWith(
      "orgmemory skill add productivity/decision-record-writer@1.0.0 --agent codex",
      "Codex",
    )
  })
})

import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { AssetTypeFilter } from "@/features/assets/components/asset-type-filter"

describe("AssetTypeFilter", () => {
  it("exposes every governed Asset profile and the active selection", async () => {
    const user = userEvent.setup()
    render(<AssetTypeFilter value="SKILL" onValueChange={vi.fn()} />)

    const trigger = screen.getByRole("combobox", { name: "Filter assets by type" })
    expect(trigger).toHaveTextContent("Skills")

    await user.click(trigger)
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "All types",
      "Prompt templates",
      "Work instructions",
      "Capability packs",
      "Skills",
    ])
  })

  it("requests the selected type and clears back to the shared catalog", async () => {
    const user = userEvent.setup()
    const onValueChange = vi.fn()

    const { rerender } = render(
      <AssetTypeFilter value={undefined} onValueChange={onValueChange} />,
    )

    await user.click(screen.getByRole("combobox", { name: "Filter assets by type" }))
    await user.click(screen.getByRole("option", { name: "Skills" }))
    expect(onValueChange).toHaveBeenLastCalledWith("SKILL")

    rerender(<AssetTypeFilter value="SKILL" onValueChange={onValueChange} />)
    await user.click(screen.getByRole("combobox", { name: "Filter assets by type" }))
    await user.click(screen.getByRole("option", { name: "All types" }))
    expect(onValueChange).toHaveBeenLastCalledWith(undefined)
  })
})

import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { AssetTypeFilter } from "@/features/assets/components/asset-type-filter"

describe("AssetTypeFilter", () => {
  it("exposes every governed Asset profile and the active selection", () => {
    render(<AssetTypeFilter value="SKILL" onValueChange={vi.fn()} />)

    expect(screen.getByRole("group", { name: "Filter assets by type" })).toBeVisible()
    expect(screen.getByRole("button", { name: "All assets" })).toHaveAttribute(
      "aria-pressed",
      "false",
    )
    expect(screen.getByRole("button", { name: "Prompt templates" })).toBeVisible()
    expect(screen.getByRole("button", { name: "Work instructions" })).toBeVisible()
    expect(screen.getByRole("button", { name: "Capability packs" })).toBeVisible()
    expect(screen.getByRole("button", { name: "Skills" })).toHaveAttribute(
      "aria-pressed",
      "true",
    )
  })

  it("requests the selected type and clears back to the shared catalog", async () => {
    const user = userEvent.setup()
    const onValueChange = vi.fn()

    const { rerender } = render(
      <AssetTypeFilter value={undefined} onValueChange={onValueChange} />,
    )

    await user.click(screen.getByRole("button", { name: "Skills" }))
    expect(onValueChange).toHaveBeenLastCalledWith("SKILL")

    rerender(<AssetTypeFilter value="SKILL" onValueChange={onValueChange} />)
    await user.click(screen.getByRole("button", { name: "All assets" }))
    expect(onValueChange).toHaveBeenLastCalledWith(undefined)
  })
})

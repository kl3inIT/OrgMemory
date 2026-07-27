import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { CollectionPagination } from "@/components/patterns/collection-pagination"

describe("CollectionPagination", () => {
  it("hides pagination when the collection fits on one page", () => {
    render(
      <CollectionPagination
        page={1}
        pageSize={24}
        total={24}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.queryByRole("navigation", { name: "Pagination" })).not.toBeInTheDocument()
  })

  it("reports the server range and requests the next page", async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(
      <CollectionPagination
        page={2}
        pageSize={24}
        total={53}
        onPageChange={onPageChange}
      />,
    )

    expect(screen.getByText("Showing 25–48 of 53")).toBeVisible()
    expect(screen.getByRole("button", { name: "Page 2" })).toHaveAttribute(
      "aria-current",
      "page",
    )

    await user.click(screen.getByRole("button", { name: "Next page" }))

    expect(onPageChange).toHaveBeenCalledWith(3)
  })
})

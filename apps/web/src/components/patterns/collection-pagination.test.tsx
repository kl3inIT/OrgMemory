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

  it("can keep the server range visible without rendering page controls", () => {
    render(
      <CollectionPagination
        page={1}
        pageSize={24}
        total={18}
        showSummaryWhenSinglePage
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText("Showing 1–18 of 18")).toBeVisible()
    expect(screen.queryByRole("navigation", { name: "Pagination" })).not.toBeInTheDocument()
  })

  it("stays out of the way when the collection is empty", () => {
    render(
      <CollectionPagination
        page={1}
        pageSize={24}
        total={0}
        showSummaryWhenSinglePage
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.queryByText("No results")).not.toBeInTheDocument()
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

  it("blocks page changes while the next server page is loading", () => {
    render(
      <CollectionPagination
        page={2}
        pageSize={24}
        total={53}
        disabled
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByRole("navigation", { name: "Pagination" })).toHaveAttribute(
      "aria-busy",
      "true",
    )
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled()
  })
})

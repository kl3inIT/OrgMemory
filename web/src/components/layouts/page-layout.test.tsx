import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { PageLayout } from "@/components/layouts/page-layout"

describe("PageLayout", () => {
  it("exposes a single semantic page identity and its primary action", () => {
    render(
      <PageLayout.Root variant="wide">
        <PageLayout.Header
          title="Asset catalog"
          description="Approved assets"
          actions={<button type="button">Create asset</button>}
        />
      </PageLayout.Root>,
    )

    expect(screen.getByRole("main")).toHaveAttribute("data-variant", "wide")
    expect(
      screen.getByRole("heading", { level: 1, name: "Asset catalog" }),
    ).toBeVisible()
    expect(screen.getByText("Approved assets")).toBeVisible()
    expect(screen.getByRole("button", { name: "Create asset" })).toBeEnabled()
  })

  it("gives a canvas workspace an accessible flex region", () => {
    render(
      <PageLayout.Root variant="canvas">
        <PageLayout.Header title="Knowledge graph" />
        <PageLayout.Canvas aria-label="Knowledge graph explorer" />
      </PageLayout.Root>,
    )

    const canvas = screen.getByRole("region", { name: "Knowledge graph explorer" })

    expect(canvas).toHaveAttribute("data-slot", "page-canvas")
    expect(canvas).toHaveClass("flex")
  })

  it("keeps ordinary page content in a dedicated body region", () => {
    render(
      <PageLayout.Root>
        <PageLayout.Header title="Users" />
        <PageLayout.Body>
          <section aria-label="User directory">Directory</section>
        </PageLayout.Body>
      </PageLayout.Root>,
    )

    const body = screen.getByText("Directory").closest('[data-slot="page-body"]')

    expect(body).toBeInTheDocument()
    expect(body).toHaveClass("pt-6")
  })
})

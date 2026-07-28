import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it } from "vitest"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"

type Person = {
  id: string
  name: string
  role: string
}

const columns: ColumnDef<Person>[] = [
  {
    accessorKey: "name",
    header: "Name",
    enableSorting: true,
  },
  {
    accessorKey: "role",
    header: "Role",
  },
]

const people: Person[] = [
  { id: "2", name: "Charlie", role: "Reviewer" },
  { id: "1", name: "Alice", role: "Owner" },
]

describe("DataTable", () => {
  it("sorts through the accessible column control", async () => {
    const user = userEvent.setup()
    render(
      <DataTable columns={columns} data={people} getRowId={(person) => person.id} />,
    )

    const table = screen.getByRole("table")
    expect(within(table).getAllByRole("row")[1]).toHaveTextContent("Charlie")

    await user.click(screen.getByRole("button", { name: "Name" }))

    expect(screen.getByRole("columnheader", { name: "Name" })).toHaveAttribute(
      "aria-sort",
      "ascending",
    )
    expect(within(table).getAllByRole("row")[1]).toHaveTextContent("Alice")
  })

  it("hands the empty case back to the owning screen", () => {
    render(
      <DataTable columns={columns} data={[]} empty={<p>No matching people</p>} />,
    )

    expect(screen.queryByRole("table")).not.toBeInTheDocument()
    expect(screen.getByText("No matching people")).toBeVisible()
  })
})

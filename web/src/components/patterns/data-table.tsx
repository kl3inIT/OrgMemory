import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  type ColumnDef,
  type Row,
  type SortingState,
  useReactTable,
} from "@tanstack/react-table"
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react"
import { useState, type ReactNode } from "react"

import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { cn } from "@/lib/utils"

declare module "@tanstack/react-table" {
  interface ColumnMeta<TData, TValue> {
    headerClassName?: string
    cellClassName?: string
  }
}

export function DataTable<TData, TValue>({
  columns,
  data,
  getRowId,
  empty,
  rowClassName,
}: {
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  getRowId?: (row: TData, index: number, parent?: Row<TData>) => string
  empty?: ReactNode
  rowClassName?: string | ((row: Row<TData>) => string | undefined)
}) {
  const [sorting, setSorting] = useState<SortingState>([])
  const table = useReactTable({
    columns,
    data,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    state: { sorting },
  })

  if (data.length === 0) return empty

  return (
    <Table>
      <TableHeader>
        {table.getHeaderGroups().map((headerGroup) => (
          <TableRow key={headerGroup.id} className="hover:bg-transparent">
            {headerGroup.headers.map((header) => {
              const sortable = header.column.getCanSort()
              const direction = header.column.getIsSorted()
              const label = flexRender(header.column.columnDef.header, header.getContext())

              return (
                <TableHead
                  key={header.id}
                  colSpan={header.colSpan}
                  className={header.column.columnDef.meta?.headerClassName}
                  aria-sort={
                    direction === "asc"
                      ? "ascending"
                      : direction === "desc"
                        ? "descending"
                        : sortable
                          ? "none"
                          : undefined
                  }
                >
                  {header.isPlaceholder ? null : sortable ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="-ml-3 h-8"
                      onClick={header.column.getToggleSortingHandler()}
                    >
                      {label}
                      {direction === "asc" ? (
                        <ArrowUp aria-hidden="true" />
                      ) : direction === "desc" ? (
                        <ArrowDown aria-hidden="true" />
                      ) : (
                        <ChevronsUpDown aria-hidden="true" />
                      )}
                    </Button>
                  ) : (
                    label
                  )}
                </TableHead>
              )
            })}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {table.getRowModel().rows.map((row) => (
          <TableRow
            key={row.id}
            className={cn(typeof rowClassName === "function" ? rowClassName(row) : rowClassName)}
          >
            {row.getVisibleCells().map((cell) => (
              <TableCell key={cell.id} className={cell.column.columnDef.meta?.cellClassName}>
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export type { ColumnDef }

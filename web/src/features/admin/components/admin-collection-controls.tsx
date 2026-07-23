import { ChevronLeft, ChevronRight, Search } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import { ADMIN_PAGE_SIZE } from "@/features/admin/admin-collection"

export function AdminSearch({
  value,
  onChange,
  placeholder,
}: {
  value: string
  onChange: (value: string) => void
  placeholder: string
}) {
  return (
    <InputGroup className="w-full">
      <InputGroupAddon>
        <Search aria-hidden="true" />
      </InputGroupAddon>
      <InputGroupInput
        type="search"
        value={value}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </InputGroup>
  )
}

export function AdminPagination({
  page,
  pageSize = ADMIN_PAGE_SIZE,
  total,
  onPageChange,
}: {
  page: number
  pageSize?: number
  total: number
  onPageChange: (page: number) => void
}) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(Math.max(page, 1), pageCount)
  const first = total === 0 ? 0 : (safePage - 1) * pageSize + 1
  const last = Math.min(safePage * pageSize, total)
  const pageTokens: (number | "ellipsis")[] = []
  const candidatePages = Array.from(
    new Set([1, pageCount, safePage - 1, safePage, safePage + 1].filter((value) => value >= 1 && value <= pageCount))
  ).sort((left, right) => left - right)
  candidatePages.forEach((candidate, index) => {
    const previous = candidatePages[index - 1]
    if (previous !== undefined && candidate - previous > 1) pageTokens.push("ellipsis")
    pageTokens.push(candidate)
  })

  return (
    <div className="flex w-full flex-col items-start justify-between gap-3 sm:flex-row sm:items-center sm:gap-4">
      <p className="text-sm tabular-nums text-muted-foreground" aria-live="polite">
        {total === 0 ? "No results" : `Showing ${first}–${last} of ${total}`}
      </p>
      <nav className="flex max-w-full items-center gap-1 self-end overflow-x-auto" aria-label="Pagination">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Previous page"
          disabled={safePage <= 1}
          onClick={() => onPageChange(safePage - 1)}
        >
          <ChevronLeft aria-hidden="true" />
        </Button>
        {pageTokens.map((token, index) =>
          token === "ellipsis" ? (
            <span
              key={`ellipsis-${index}`}
              className="grid size-8 place-items-center text-sm text-muted-foreground"
              aria-hidden="true"
            >
              …
            </span>
          ) : (
            <Button
              key={token}
              type="button"
              variant={token === safePage ? "outline" : "ghost"}
              size="icon-sm"
              aria-label={`Page ${token}`}
              aria-current={token === safePage ? "page" : undefined}
              onClick={() => onPageChange(token)}
            >
              {token}
            </Button>
          )
        )}
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Next page"
          disabled={safePage >= pageCount}
          onClick={() => onPageChange(safePage + 1)}
        >
          <ChevronRight aria-hidden="true" />
        </Button>
      </nav>
    </div>
  )
}

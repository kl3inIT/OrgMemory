import { ChevronLeft, ChevronRight } from "lucide-react"

import { Button } from "@/components/ui/button"

export function CollectionPagination({
  page,
  pageSize,
  total,
  onPageChange,
}: {
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
}) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  if (pageCount <= 1) return null

  const safePage = Math.min(Math.max(page, 1), pageCount)
  const first = (safePage - 1) * pageSize + 1
  const last = Math.min(safePage * pageSize, total)
  const pageTokens: (number | "ellipsis")[] = []
  const candidatePages = Array.from(
    new Set(
      [1, pageCount, safePage - 1, safePage, safePage + 1].filter(
        (value) => value >= 1 && value <= pageCount,
      ),
    ),
  ).sort((left, right) => left - right)

  candidatePages.forEach((candidate, index) => {
    const previous = candidatePages[index - 1]
    if (previous !== undefined && candidate - previous > 1) pageTokens.push("ellipsis")
    pageTokens.push(candidate)
  })

  return (
    <div className="flex w-full flex-col items-start justify-between gap-3 sm:flex-row sm:items-center sm:gap-4">
      <p className="text-sm tabular-nums text-content-muted" aria-live="polite">
        {total === 0 ? "No results" : `Showing ${first}–${last} of ${total}`}
      </p>
      <nav
        className="flex max-w-full items-center gap-1 self-end overflow-x-auto"
        aria-label="Pagination"
      >
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
              className="grid size-8 place-items-center text-sm text-content-muted"
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
          ),
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

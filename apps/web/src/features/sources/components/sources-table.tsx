import { Ellipsis, Eye, FileText, SearchX, Trash2 } from "lucide-react"
import { useMemo } from "react"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { EmptyState } from "@/components/patterns/empty-state"
import { Progress } from "@/components/ui/progress"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { SourceStatusBadge } from "@/features/sources/components/source-status-badge"
import {
  ACTIVE_SOURCE_STATUSES,
  sourceProgress,
  titleCase,
} from "@/features/sources/source-status"
import type { SourceResponse } from "@/lib/hey-api"
import { formatBytes, formatDate } from "@/lib/format"

function accessScope(classification?: string) {
  switch (classification) {
    case "PUBLIC":
    case "INTERNAL":
      return "All employees"
    case "CONFIDENTIAL":
      return "Your department"
    case "RESTRICTED":
      return "Executive only"
    default:
      return "Policy controlled"
  }
}

export function SourcesTable({
  sources,
  onView,
  onDelete,
}: {
  sources: SourceResponse[]
  onView: (source: SourceResponse) => void
  onDelete: (source: SourceResponse) => void
}) {
  const columns = useMemo<ColumnDef<SourceResponse>[]>(
    () => [
      {
        id: "document",
        accessorFn: (source) => source.title ?? source.fileName ?? "",
        header: "Document",
        enableSorting: true,
        cell: ({ row }) => {
          const source = row.original
          return (
            <div className="flex min-w-32 items-center gap-3 sm:min-w-64">
              <span className="grid size-9 shrink-0 place-items-center rounded-md border bg-muted/40">
                <FileText className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <button
                  type="button"
                  className="max-w-28 truncate text-left font-medium hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring sm:max-w-96"
                  onClick={() => onView(source)}
                >
                  {source.title ?? source.fileName}
                </button>
                <div className="mt-0.5 max-w-28 truncate text-xs text-muted-foreground sm:max-w-none">
                  {formatBytes(source.contentLength)} · {source.mediaType ?? "Document"}
                </div>
              </div>
            </div>
          )
        },
      },
      {
        id: "access",
        accessorFn: (source) => source.classification ?? "",
        header: "Access",
        enableSorting: true,
        cell: ({ row }) => {
          const source = row.original
          return (
            <div className="space-y-0.5">
              <div className="text-sm">
                {source.classification ? titleCase(source.classification) : "Policy controlled"}
              </div>
              <div className="text-xs text-muted-foreground">
                {accessScope(source.classification)}
              </div>
            </div>
          )
        },
      },
      {
        id: "pipeline",
        accessorFn: (source) => source.status ?? "UNKNOWN",
        header: "Pipeline",
        enableSorting: true,
        meta: {
          headerClassName: "hidden md:table-cell",
          cellClassName: "hidden md:table-cell",
        },
        cell: ({ row }) => {
          const source = row.original
          const status = source.status ?? "UNKNOWN"
          return (
            <div className="w-36 space-y-2">
              <SourceStatusBadge source={source} />
              {ACTIVE_SOURCE_STATUSES.has(status) ? (
                <Progress
                  value={sourceProgress(status)}
                  className="h-1"
                  aria-label={`${status} progress`}
                />
              ) : null}
            </div>
          )
        },
      },
      {
        id: "indexProfile",
        accessorFn: (source) => source.embeddingModel ?? "",
        header: "Index profile",
        enableSorting: true,
        meta: {
          headerClassName: "hidden lg:table-cell",
          cellClassName: "hidden lg:table-cell",
        },
        cell: ({ row }) => {
          const source = row.original
          return source.embeddingModel ? (
            <div className="space-y-0.5">
              <div className="text-sm">{source.embeddingModel}</div>
              <div className="font-mono text-xs text-muted-foreground">
                {source.embeddingDimensions}d · {source.embeddingProvider}
              </div>
            </div>
          ) : (
            <span className="text-sm text-muted-foreground">Pending</span>
          )
        },
      },
      {
        id: "updated",
        accessorFn: (source) => source.updatedAt ?? "",
        header: "Updated",
        enableSorting: true,
        meta: {
          headerClassName: "hidden text-right xl:table-cell",
          cellClassName: "hidden text-right text-sm text-muted-foreground xl:table-cell",
        },
        cell: ({ row }) => formatDate(row.original.updatedAt),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">Actions</span>,
        enableSorting: false,
        meta: {
          headerClassName: "w-12 text-right",
          cellClassName: "w-12 text-right",
        },
        cell: ({ row }) => {
          const source = row.original
          const title = source.title ?? source.fileName ?? "document"
          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${title}`}>
                  <Ellipsis aria-hidden="true" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => onView(source)}>
                  <Eye aria-hidden="true" /> View
                </DropdownMenuItem>
                <DropdownMenuItem
                  variant="destructive"
                  disabled={!source.deletionAllowed}
                  onSelect={() => onDelete(source)}
                >
                  <Trash2 aria-hidden="true" />
                  {source.deletionAllowed ? "Delete" : "Delete unavailable"}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )
        },
      },
    ],
    [onDelete, onView],
  )

  return (
    <DataTable
      columns={columns}
      data={sources}
      getRowId={(source, index) => source.id ?? String(index)}
      empty={
        <EmptyState
          title="No matching documents"
          description="Change the search or status filter."
          icon={<SearchX className="size-4" aria-hidden="true" />}
        />
      }
    />
  )
}

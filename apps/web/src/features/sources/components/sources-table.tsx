import { FileText, SearchX } from "lucide-react"
import { useMemo } from "react"

import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { EmptyState } from "@/components/patterns/empty-state"
import { Progress } from "@/components/ui/progress"
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

export function SourcesTable({ sources }: { sources: SourceResponse[] }) {
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
                <div className="max-w-28 truncate font-medium sm:max-w-96">
                  {source.title ?? source.fileName}
                </div>
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
    ],
    [],
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

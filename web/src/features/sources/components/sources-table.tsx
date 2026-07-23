import { FileText, SearchX } from "lucide-react"

import { Progress } from "@/components/ui/progress"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { SourceStatusBadge } from "@/features/sources/components/source-status-badge"
import {
  ACTIVE_SOURCE_STATUSES,
  formatBytes,
  formatDate,
  sourceProgress,
  titleCase,
} from "@/features/sources/source-status"
import type { SourceResponse } from "@/lib/hey-api"

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
  if (sources.length === 0) {
    return (
      <div className="grid min-h-72 place-items-center px-6 text-center">
        <div className="space-y-2">
          <span className="mx-auto grid size-10 place-items-center rounded-full border bg-muted/40">
            <SearchX className="size-4" aria-hidden="true" />
          </span>
          <p className="text-sm font-medium">No matching documents</p>
          <p className="text-sm text-muted-foreground">Change the search or status filter.</p>
        </div>
      </div>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead>Document</TableHead>
          <TableHead>Access</TableHead>
          <TableHead className="hidden md:table-cell">Pipeline</TableHead>
          <TableHead className="hidden lg:table-cell">Index profile</TableHead>
          <TableHead className="hidden text-right xl:table-cell">Updated</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sources.map((source) => {
          const status = source.status ?? "UNKNOWN"
          return (
            <TableRow key={source.id}>
              <TableCell>
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
              </TableCell>
              <TableCell>
                <div className="space-y-0.5">
                  <div className="text-sm">
                    {source.classification ? titleCase(source.classification) : "Policy controlled"}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {accessScope(source.classification)}
                  </div>
                </div>
              </TableCell>
              <TableCell className="hidden md:table-cell">
                <div className="w-36 space-y-2">
                  <SourceStatusBadge source={source} />
                  {ACTIVE_SOURCE_STATUSES.has(status) ? (
                    <Progress value={sourceProgress(status)} className="h-1" aria-label={`${status} progress`} />
                  ) : null}
                </div>
              </TableCell>
              <TableCell className="hidden lg:table-cell">
                {source.embeddingModel ? (
                  <div className="space-y-0.5">
                    <div className="text-sm">{source.embeddingModel}</div>
                    <div className="font-mono text-xs text-muted-foreground">
                      {source.embeddingDimensions}d · {source.embeddingProvider}
                    </div>
                  </div>
                ) : (
                  <span className="text-sm text-muted-foreground">Pending</span>
                )}
              </TableCell>
              <TableCell className="hidden text-right text-sm text-muted-foreground xl:table-cell">
                {formatDate(source.updatedAt)}
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

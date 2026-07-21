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
import { ACTIVE_SOURCE_STATUSES, formatBytes, formatDate, sourceProgress } from "@/features/sources/source-status"
import type { SourceResponse } from "@/lib/hey-api"

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
          <TableHead>Pipeline</TableHead>
          <TableHead>Index profile</TableHead>
          <TableHead className="text-right">Updated</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sources.map((source) => {
          const status = source.status ?? "UNKNOWN"
          return (
            <TableRow key={source.id}>
              <TableCell>
                <div className="flex min-w-64 items-center gap-3">
                  <span className="grid size-9 shrink-0 place-items-center rounded-md border bg-muted/40">
                    <FileText className="size-4" aria-hidden="true" />
                  </span>
                  <div className="min-w-0">
                    <div className="max-w-96 truncate font-medium">{source.title ?? source.fileName}</div>
                    <div className="mt-0.5 text-xs text-muted-foreground">
                      {formatBytes(source.contentLength)} · {source.mediaType ?? "Document"}
                    </div>
                  </div>
                </div>
              </TableCell>
              <TableCell>
                <div className="space-y-0.5">
                  <div className="text-sm capitalize">{source.classification?.toLowerCase()}</div>
                  <div className="text-xs text-muted-foreground">
                    {source.classification === "INTERNAL" ? "All employees" : "Your department"}
                  </div>
                </div>
              </TableCell>
              <TableCell>
                <div className="w-36 space-y-2">
                  <SourceStatusBadge source={source} />
                  {ACTIVE_SOURCE_STATUSES.has(status) ? (
                    <Progress value={sourceProgress(status)} className="h-1" aria-label={`${status} progress`} />
                  ) : null}
                </div>
              </TableCell>
              <TableCell>
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
              <TableCell className="text-right text-sm text-muted-foreground">
                {formatDate(source.updatedAt)}
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

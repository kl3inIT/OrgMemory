import { AlertTriangle, Check, LoaderCircle } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { ACTIVE_SOURCE_STATUSES, titleCase } from "@/features/sources/source-status"
import type { SourceResponse } from "@/lib/hey-api"

export function SourceStatusBadge({ source }: { source: SourceResponse }) {
  const status = source.status ?? "UNKNOWN"
  if (status === "READY") {
    return (
      <Badge variant="success">
        <Check aria-hidden="true" />
        Ready
      </Badge>
    )
  }
  if (status === "FAILED" || status === "QUARANTINED") {
    return (
      <Badge variant="destructive" title={source.failureMessage}>
        <AlertTriangle aria-hidden="true" />
        {status === "FAILED" ? "Failed" : "Quarantined"}
      </Badge>
    )
  }
  if (ACTIVE_SOURCE_STATUSES.has(status)) {
    return (
      <Badge variant="warning">
        <LoaderCircle className="animate-spin" aria-hidden="true" />
        {titleCase(status)}
      </Badge>
    )
  }
  return <Badge variant="muted">{titleCase(status)}</Badge>
}

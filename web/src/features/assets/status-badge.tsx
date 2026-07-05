import { Badge } from "@/components/ui/badge"
import type { AssetStatus } from "@/lib/api"

export function StatusBadge({ status }: { status: AssetStatus }) {
  if (status === "APPROVED") return <Badge variant="success">Approved</Badge>
  if (status === "IN_REVIEW") return <Badge variant="warning">Needs Review</Badge>
  if (status === "DEPRECATED") return <Badge variant="secondary">Deprecated</Badge>
  if (status === "REJECTED") return <Badge variant="destructive">Rejected</Badge>
  return <Badge variant="warning">Draft</Badge>
}

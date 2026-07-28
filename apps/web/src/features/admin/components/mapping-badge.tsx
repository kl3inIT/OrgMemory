import { Badge } from "@/components/ui/badge"
import { MAPPING_METHODS } from "@/features/admin/admin-labels"
import type { AdminSourceMappingResponse } from "@/lib/hey-api"

/** The tier that established a mapping, or the absence of one — which means denied. */
export function MappingBadge({ mapping }: { mapping?: AdminSourceMappingResponse }) {
  if (!mapping?.method) {
    return <Badge variant="destructive">Unmapped</Badge>
  }

  const method = MAPPING_METHODS[mapping.method]
  return (
    <Badge variant={mapping.method === "IDP_JOIN" ? "default" : "secondary"} title={method?.hint}>
      {method?.label ?? mapping.method}
    </Badge>
  )
}

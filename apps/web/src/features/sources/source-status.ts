import type { SourcePageResponse, SourceResponse } from "@/lib/hey-api"

export const ACTIVE_SOURCE_STATUSES = new Set([
  "RECEIVED",
  "VALIDATING",
  "PARSING",
  "CHUNKING",
  "EMBEDDING",
  "PUBLISHING",
])

export type SourceStatusFilter = "ALL" | "PROCESSING" | "READY" | "ATTENTION"

export const SOURCE_STATUS_FILTERS: Array<{
  label: string
  compactLabel?: string
  value: SourceStatusFilter
}> = [
  { label: "All documents", compactLabel: "All docs", value: "ALL" },
  { label: "Processing", value: "PROCESSING" },
  { label: "Ready", value: "READY" },
  { label: "Needs attention", compactLabel: "Attention", value: "ATTENTION" },
]

export function matchesSourceStatus(source: SourceResponse, filter: SourceStatusFilter) {
  const status = source.status ?? "UNKNOWN"
  if (filter === "ALL") return true
  if (filter === "PROCESSING") return ACTIVE_SOURCE_STATUSES.has(status)
  if (filter === "READY") return status === "READY"
  return status === "FAILED" || status === "QUARANTINED"
}

export function sourceStatusCount(sources: SourceResponse[], filter: SourceStatusFilter) {
  return sources.filter((source) => matchesSourceStatus(source, filter)).length
}

export function sourceStatusCountFromPage(
  page: SourcePageResponse | undefined,
  filter: SourceStatusFilter,
) {
  if (!page) return 0
  if (filter === "ALL") return page.total ?? 0
  if (filter === "PROCESSING") return page.statusCounts?.processing ?? 0
  if (filter === "READY") return page.statusCounts?.ready ?? 0
  return page.statusCounts?.attention ?? 0
}

export function sourceProgress(status?: string) {
  switch (status) {
    case "RECEIVED":
      return 8
    case "VALIDATING":
      return 20
    case "PARSING":
      return 38
    case "CHUNKING":
      return 56
    case "EMBEDDING":
      return 74
    case "PUBLISHING":
      return 92
    case "READY":
      return 100
    default:
      return 0
  }
}

export function titleCase(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase())
}

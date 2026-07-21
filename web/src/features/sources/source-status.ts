import type { SourceResponse } from "@/lib/hey-api"

export const ACTIVE_SOURCE_STATUSES = new Set([
  "RECEIVED",
  "VALIDATING",
  "PARSING",
  "CHUNKING",
  "EMBEDDING",
  "PUBLISHING",
])

export type SourceStatusFilter = "ALL" | "PROCESSING" | "READY" | "ATTENTION"

export const SOURCE_STATUS_FILTERS: Array<{ label: string; value: SourceStatusFilter }> = [
  { label: "All documents", value: "ALL" },
  { label: "Processing", value: "PROCESSING" },
  { label: "Ready", value: "READY" },
  { label: "Needs attention", value: "ATTENTION" },
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

export function formatBytes(value?: number) {
  if (!value) return "0 B"
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function formatDate(value?: string) {
  if (!value) return "—"
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
}

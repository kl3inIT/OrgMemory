export type FormatDateOptions = {
  dateOnly?: boolean
  fallback?: string
}

export function formatDate(value?: string, options: FormatDateOptions = {}) {
  const fallback = options.fallback ?? "—"
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.valueOf())) return fallback
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    ...(options.dateOnly ? {} : { timeStyle: "short" as const }),
  }).format(date)
}

export function formatBytes(value?: number, fallback = "0 B") {
  if (value === undefined) return fallback
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`
  return `${(value / (1024 * 1024)).toFixed(1)} MiB`
}

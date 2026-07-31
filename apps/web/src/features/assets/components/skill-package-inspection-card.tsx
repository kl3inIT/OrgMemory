import { CheckCircle2, FileArchive } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { SkillPackageInspection } from "@/lib/hey-api"

export function SkillPackageInspectionCard({
  inspection,
}: {
  inspection: SkillPackageInspection
}) {
  const files = inspection.files ?? []
  return (
    <Card className="h-fit gap-0 border-status-success-border bg-status-success-surface/20 py-0 shadow-none">
      <CardHeader className="border-b border-status-success-border/50 px-5 py-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="size-5 text-status-success-content" aria-hidden="true" />
            <CardTitle>Validated package</CardTitle>
          </div>
          <Badge variant="outline">{files.length} files</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-5 p-5">
        <div>
          <p className="font-mono text-label text-content-primary">{inspection.name}</p>
          <p className="mt-2 text-sm leading-6 text-content-secondary">
            {inspection.description}
          </p>
        </div>
        <div className="grid gap-px overflow-hidden rounded-lg border border-border-default bg-border-default sm:grid-cols-2 lg:grid-cols-1">
          <Metric label="Archive" value={formatBytes(inspection.contentLength)} />
          <Metric label="Compatibility" value={inspection.compatibility || "Not declared"} />
        </div>
        <div>
          <p className="text-metadata text-content-muted">SHA-256</p>
          <p className="mt-2 break-all font-mono text-metadata text-content-secondary">
            {inspection.sha256}
          </p>
        </div>
        <div className="max-h-44 overflow-y-auto rounded-lg border border-border-default bg-surface-raised">
          {files.slice(0, 12).map((file, index) => (
            <div
              key={`${file.path ?? "file"}:${index}`}
              className="flex items-center justify-between gap-3 border-b border-border-subtle px-3 py-2.5 last:border-b-0"
            >
              <span className="min-w-0 truncate font-mono text-metadata">{file.path}</span>
              <span className="shrink-0 text-metadata text-content-muted">
                {formatBytes(file.size)}
              </span>
            </div>
          ))}
          {files.length > 12 ? (
            <p className="px-3 py-2.5 text-metadata text-content-muted">
              {files.length - 12} more files
            </p>
          ) : null}
        </div>
        <p className="flex gap-2 text-xs leading-5 text-content-muted">
          <FileArchive className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Structural validation is not content or malware review.
        </p>
      </CardContent>
    </Card>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-surface-subtle p-3">
      <p className="text-metadata text-content-muted">{label}</p>
      <p className="mt-1 text-label">{value}</p>
    </div>
  )
}

function formatBytes(value?: number) {
  if (value === undefined) return "—"
  if (value < 1_024) return `${value} B`
  const kibibytes = value / 1_024
  if (kibibytes < 1_024) return `${kibibytes.toFixed(1)} KB`
  return `${(kibibytes / 1_024).toFixed(1)} MB`
}

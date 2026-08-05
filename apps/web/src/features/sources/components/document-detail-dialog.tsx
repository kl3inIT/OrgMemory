import { GovernedDocumentViewer } from "@/features/sources/components/governed-document-viewer"
import type { SourceResponse } from "@/lib/hey-api"

export function DocumentDetailDialog({
  source,
  onOpenChange,
  onUploadCorrection,
}: {
  source: SourceResponse | null
  onOpenChange: (open: boolean) => void
  onUploadCorrection: () => void
}) {
  return (
    <GovernedDocumentViewer
      target={source ? { kind: "source", source, onUploadCorrection } : null}
      onOpenChange={onOpenChange}
    />
  )
}

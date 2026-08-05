import { useQuery } from "@tanstack/react-query"
import { Download, FileQuestion, LoaderCircle, RefreshCw, Upload, X } from "lucide-react"
import { useEffect, useState } from "react"
import type { ReactNode } from "react"

import { SplitLayout } from "@/components/layouts/split-layout"
import { RestrictedMarkdown } from "@/components/patterns/restricted-markdown"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import {
  SourceFailureDetail,
  SourceStatusBadge,
} from "@/features/sources/components/source-status-badge"
import {
  sourcePreviewKind,
  sourceFormatLabel,
  type SourcePreviewKind,
} from "@/features/sources/source-preview"
import { titleCase } from "@/features/sources/source-status"
import { readSourceContent } from "@/lib/hey-api"
import type { SourceResponse } from "@/lib/hey-api"
import { formatBytes, formatDate } from "@/lib/format"

interface PreviewPayload {
  blob: Blob
  kind: SourcePreviewKind
  mediaType: string
  text?: string
}

export function DocumentDetailSheet({
  source,
  onOpenChange,
  onUploadCorrection,
}: {
  source: SourceResponse | null
  onOpenChange: (open: boolean) => void
  onUploadCorrection: () => void
}) {
  return (
    <Sheet open={source !== null} onOpenChange={onOpenChange}>
      <SheetContent
        className="flex min-w-0 w-full flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl"
        showCloseButton={false}
      >
        <SheetHeader className="sr-only">
          <SheetTitle>{source?.title ?? source?.fileName ?? "Document"}</SheetTitle>
          <SheetDescription>
            Governed metadata and original evidence from the current revision.
          </SheetDescription>
        </SheetHeader>
        {source ? (
          <DocumentDetailContent
            source={source}
            onClose={() => onOpenChange(false)}
            onUploadCorrection={onUploadCorrection}
          />
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

export function DocumentDetailPanel({
  source,
  onClose,
  onUploadCorrection,
}: {
  source: SourceResponse
  onClose: () => void
  onUploadCorrection: () => void
}) {
  return (
    <SplitLayout.Aside className="hidden w-[min(44rem,48vw)] overflow-hidden lg:flex lg:flex-col">
      <DocumentDetailContent
        source={source}
        onClose={onClose}
        onUploadCorrection={onUploadCorrection}
      />
    </SplitLayout.Aside>
  )
}

function DocumentDetailContent({
  source,
  onClose,
  onUploadCorrection,
}: {
  source: SourceResponse
  onClose: () => void
  onUploadCorrection: () => void
}) {
  const sourceId = source.id
  const preview = useQuery({
    queryKey: ["source-content-preview", sourceId],
    enabled: Boolean(sourceId && source.contentAvailable),
    queryFn: async (): Promise<PreviewPayload> => {
      if (!sourceId) throw new Error("Document is unavailable")
      const { data } = await readSourceContent({
        path: { sourceId },
        parseAs: "blob",
        throwOnError: true,
      })
      if (!(data instanceof Blob)) throw new Error("Document is unavailable")
      const mediaType = data.type || "application/octet-stream"
      const kind = sourcePreviewKind(mediaType, source.mediaType)
      const text = kind === "text" || kind === "markdown" ? await data.text() : undefined
      return { blob: data, kind, mediaType, text }
    },
    gcTime: 30_000,
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
  const [blobUrl, setBlobUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!preview.data) {
      setBlobUrl(null)
      return
    }
    const url = URL.createObjectURL(preview.data.blob)
    setBlobUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [preview.data])

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-background">
      <header className="shrink-0 border-b border-border-subtle px-5 py-4 text-left sm:px-6">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              <h2 className="min-w-0 truncate font-semibold">
                {source.title ?? source.fileName ?? "Document"}
              </h2>
              <SourceStatusBadge source={source} />
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              Governed metadata and original evidence from the current revision.
            </p>
          </div>
          <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label="Close document">
            <X aria-hidden="true" />
          </Button>
        </div>
      </header>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        {source.status === "FAILED" || source.status === "QUARANTINED" ? (
          <div className="mx-4 mt-4 rounded-lg border border-destructive/30 bg-destructive/5 p-4 sm:mx-6">
            <p className="text-sm font-medium text-status-danger-content">
              {source.status === "QUARANTINED"
                ? "Evidence needs correction"
                : "Processing failed"}
            </p>
            <div className="mt-1">
              <SourceFailureDetail source={source} />
            </div>
            {source.status === "QUARANTINED" ? (
              <Button
                className="mt-3"
                variant="outline"
                size="sm"
                onClick={onUploadCorrection}
              >
                <Upload aria-hidden="true" />
                Upload corrected document
              </Button>
            ) : null}
          </div>
        ) : null}
        <div className="mx-4 my-4 grid min-w-0 shrink-0 grid-cols-2 gap-x-4 gap-y-3 rounded-lg border bg-surface-subtle p-4 text-sm sm:mx-6">
          <Metadata label="File" value={source.fileName ?? "—"} />
          <Metadata label="Size" value={formatBytes(source.contentLength)} />
          <Metadata
            label="Classification"
            value={source.classification ? titleCase(source.classification) : "Policy controlled"}
          />
          <Metadata label="Updated" value={formatDate(source.updatedAt)} />
          <Metadata
            label="Format"
            value={sourceFormatLabel(source.mediaType, source.fileName)}
          />
          <Metadata
            label="Source"
            value={source.sourceSystem ? titleCase(source.sourceSystem) : "Unknown"}
          />
        </div>

        <section
          className="mx-4 mb-4 flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-xl border bg-background sm:mx-6 sm:mb-6"
          aria-label="Original evidence"
        >
          <div className="flex min-w-0 shrink-0 items-center justify-between gap-4 border-b px-4 py-3">
            <div className="min-w-0">
              <h3 className="font-medium">Original evidence</h3>
              <p className="mt-0.5 truncate text-xs text-muted-foreground">
                {previewDescription(preview.data?.kind)}
              </p>
            </div>
            {preview.data && blobUrl ? (
              <Button variant="outline" size="sm" className="shrink-0" asChild>
                <a href={blobUrl} download={source.fileName ?? source.title ?? "document"}>
                  <Download aria-hidden="true" /> Download
                </a>
              </Button>
            ) : null}
          </div>
          <div className="flex min-h-0 min-w-0 flex-1 bg-surface-sunken">
            {!source.contentAvailable ? (
              <EmptyPreview message="Original content becomes available after governed publication completes." />
            ) : preview.isError ? (
              <EmptyPreview
                message="The document is no longer available or permission has changed."
                action={
                  <Button variant="outline" size="sm" onClick={() => preview.refetch()}>
                    <RefreshCw aria-hidden="true" /> Retry
                  </Button>
                }
              />
            ) : preview.isPending || preview.isFetching || !blobUrl ? (
              <div
                className="flex size-full items-center justify-center gap-2 text-sm text-muted-foreground"
                role="status"
              >
                <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                Loading document
              </div>
            ) : preview.data ? (
              <PreviewContent
                payload={preview.data}
                blobUrl={blobUrl}
                title={source.title ?? source.fileName ?? "Document"}
              />
            ) : null}
          </div>
        </section>
      </div>
    </div>
  )
}

function PreviewContent({
  payload,
  blobUrl,
  title,
}: {
  payload: PreviewPayload
  blobUrl: string
  title: string
}) {
  switch (payload.kind) {
    case "pdf":
      return <iframe title={title} src={blobUrl} sandbox="" className="size-full bg-white" />
    case "image":
      return (
        <div className="flex size-full items-center justify-center overflow-auto p-4">
          <img src={blobUrl} alt={title} className="max-h-full max-w-full object-contain" />
        </div>
      )
    case "markdown":
      return <MarkdownPreview content={payload.text ?? ""} />
    case "text":
      return (
        <pre className="size-full overflow-auto whitespace-pre-wrap break-words bg-background p-6 font-mono text-sm leading-relaxed">
          {payload.text}
        </pre>
      )
    default:
      return <EmptyPreview message="Preview is unavailable for this file type. Download the original file." />
  }
}

function MarkdownPreview({ content }: { content: string }) {
  const [view, setView] = useState<"rendered" | "raw">("rendered")
  return (
    <div className="flex size-full min-h-0 flex-col bg-background">
      <div className="flex shrink-0 items-center gap-1 border-b border-border-subtle px-4 py-2">
        {(["rendered", "raw"] as const).map((option) => (
          <Button
            key={option}
            type="button"
            size="sm"
            variant={view === option ? "secondary" : "ghost"}
            aria-pressed={view === option}
            onClick={() => setView(option)}
          >
            {option === "rendered" ? "Rendered" : "Raw"}
          </Button>
        ))}
      </div>
      <div className="min-h-0 flex-1 overflow-auto">
        {view === "rendered" ? (
          <RestrictedMarkdown content={content} />
        ) : (
          <pre className="min-h-full whitespace-pre-wrap p-6 font-mono text-sm leading-relaxed text-foreground">
            {content}
          </pre>
        )}
      </div>
    </div>
  )
}

function EmptyPreview({
  message,
  action,
}: {
  message: string
  action?: ReactNode
}) {
  return (
    <div className="flex size-full flex-col items-center justify-center gap-3 p-6 text-center text-sm text-muted-foreground">
      <FileQuestion className="size-7" aria-hidden="true" />
      <p className="max-w-sm">{message}</p>
      {action}
    </div>
  )
}

function Metadata({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 break-words" title={value}>{value}</p>
    </div>
  )
}

function previewDescription(kind?: SourcePreviewKind) {
  switch (kind) {
    case "markdown":
      return "Safe rendered Markdown with the original source available."
    case "pdf":
      return "PDF preview from the permission-verified original."
    case "image":
      return "Image preview from the permission-verified original."
    case "text":
      return "Plain-text preview from the permission-verified original."
    case "download":
      return "This format is download-only."
    default:
      return "Permission-verified original for the current revision."
  }
}

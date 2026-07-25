import { useQuery } from "@tanstack/react-query"
import {
  Check,
  Copy,
  Download,
  ExternalLink,
  FileText,
  FileQuestion,
  LoaderCircle,
  Search,
  X,
} from "lucide-react"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { useIsMobile } from "@/hooks/use-mobile"
import { cn } from "@/lib/utils"

export interface AssistantSourceRef {
  id: string
  citationNumber: number
  title: string
  url: string
}

interface PreviewPayload {
  blob: Blob
  mediaType: string
  text?: string
}

interface AssistantSourcesPanelProps {
  open: boolean
  sources: AssistantSourceRef[]
  selectedSourceId: string | null
  onClose: () => void
  onSelect: (sourceId: string) => void
}

export function AssistantSourcesPanel(props: AssistantSourcesPanelProps) {
  const isMobile = useIsMobile()

  if (isMobile) {
    return (
      <Sheet open={props.open} onOpenChange={(open: boolean) => !open && props.onClose()}>
        <SheetContent className="w-full gap-0 p-0 sm:max-w-xl" showCloseButton={false}>
          <SheetHeader className="sr-only">
            <SheetTitle>Sources</SheetTitle>
            <SheetDescription>Permission-verified evidence used for this answer</SheetDescription>
          </SheetHeader>
          <SourcesPanelContent {...props} />
        </SheetContent>
      </Sheet>
    )
  }

  return (
    <aside
      aria-label="Answer sources"
      className={cn(
        "min-h-0 shrink-0 overflow-hidden border-l border-border-subtle bg-background transition-[width] duration-200",
        props.open ? "w-[min(24rem,32vw)]" : "w-0 border-l-0",
      )}
    >
      {props.open ? <SourcesPanelContent {...props} /> : null}
    </aside>
  )
}

function SourcesPanelContent({
  sources,
  selectedSourceId,
  onClose,
  onSelect,
}: AssistantSourcesPanelProps) {
  const [previewOpen, setPreviewOpen] = useState(false)
  const selected = sources.find((source) => source.id === selectedSourceId) ?? sources[0] ?? null
  const canPreview = selected?.url.startsWith("/api/citations/") ?? false
  const selectedUrl = canPreview ? selected?.url : undefined
  const previewQuery = useQuery({
    queryKey: ["assistant-citation-preview", selectedUrl],
    enabled: selectedUrl !== undefined && previewOpen,
    queryFn: async (): Promise<PreviewPayload> => {
      if (!selectedUrl) throw new Error("Citation URL is unavailable")
      const response = await fetch(selectedUrl, {
        credentials: "same-origin",
      })
      if (!response.ok) throw new Error("Source is unavailable")
      const blob = await response.blob()
      const mediaType = blob.type || "application/octet-stream"
      const text = isTextPreview(mediaType) ? await blob.text() : undefined
      return { blob, mediaType, text }
    },
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  })
  const [blobUrl, setBlobUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!previewQuery.data || previewQuery.isFetching) {
      setBlobUrl(null)
      return
    }

    const objectUrl = URL.createObjectURL(previewQuery.data.blob)
    setBlobUrl(objectUrl)

    return () => {
      URL.revokeObjectURL(objectUrl)
    }
  }, [previewQuery.data, previewQuery.isFetching])

  const ready =
    previewQuery.data && blobUrl && !previewQuery.isFetching
      ? { ...previewQuery.data, blobUrl }
      : null

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-border-subtle px-4">
        <div className="flex min-w-0 items-center gap-2">
          <Search className="size-5 shrink-0 text-content-muted" aria-hidden="true" />
          <h2 className="truncate text-section-title text-content-primary">Cited sources</h2>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close sources">
          <X className="size-4" aria-hidden="true" />
        </Button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
        {sources.length ? (
          <div className="space-y-1">
            {sources.map((source) => (
              <SourceListItem
                key={source.id}
                source={source}
                selected={source.id === selected?.id}
                onOpen={() => {
                  onSelect(source.id)
                  setPreviewOpen(true)
                }}
              />
            ))}
          </div>
        ) : (
          <EmptyPreview message="No source was attached to this answer." />
        )}
      </div>

      <CitationPreviewDialog
        open={previewOpen}
        onOpenChange={setPreviewOpen}
        source={selected}
        canPreview={canPreview}
        preview={ready}
        loading={
          Boolean(selected && canPreview) &&
          !previewQuery.isError &&
          (previewQuery.isPending || previewQuery.isFetching || !blobUrl)
        }
        error={previewQuery.isError}
      />
    </div>
  )
}

function SourceListItem({
  source,
  selected,
  onOpen,
}: {
  source: AssistantSourceRef
  selected: boolean
  onOpen: () => void
}) {
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-current={selected ? "true" : undefined}
      className={cn(
        "group flex w-full gap-2.5 rounded-xl p-3 text-left transition-colors",
        selected ? "bg-action-ghost-hover" : "hover:bg-surface-subtle",
      )}
    >
      <span className="mt-0.5 grid size-5 shrink-0 place-items-center text-content-secondary">
        <FileText className="size-[18px]" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-baseline gap-2">
          <span className="line-clamp-2 text-label text-content-primary">{source.title}</span>
          <span className="shrink-0 text-metadata tabular-nums text-content-muted">
            [{source.citationNumber}]
          </span>
        </span>
        <span className="mt-1 block truncate text-supporting text-content-muted">
          {sourceOrigin(source)}
        </span>
      </span>
    </button>
  )
}

function CitationPreviewDialog({
  open,
  onOpenChange,
  source,
  canPreview,
  preview,
  loading,
  error,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  source: AssistantSourceRef | null
  canPreview: boolean
  preview: (PreviewPayload & { blobUrl: string }) | null
  loading: boolean
  error: boolean
}) {
  const [copied, setCopied] = useState(false)
  const metadata = preview ? previewMetadata(preview) : ""

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-[min(82vh,52rem)] w-[min(92vw,64rem)] max-w-none flex-col gap-0 overflow-hidden p-0 sm:max-w-none">
        <DialogHeader className="shrink-0 border-b border-border-subtle px-5 py-4 pr-12">
          <DialogTitle className="truncate">{source?.title ?? "Source"}</DialogTitle>
          <DialogDescription>{metadata || "Permission-verified source evidence"}</DialogDescription>
        </DialogHeader>

        <div className="min-h-0 flex-1 overflow-hidden bg-surface-subtle">
          {!source ? <EmptyPreview message="No source was selected." /> : null}
          {source && !canPreview ? (
            <div className="flex h-full flex-col items-center justify-center gap-4 p-6 text-center">
              <FileQuestion className="size-7 text-muted-foreground" aria-hidden="true" />
              <p className="text-sm text-muted-foreground">
                This source opens outside the secure evidence preview.
              </p>
              <Button variant="outline" asChild>
                <a href={source.url} target="_blank" rel="noreferrer">
                  <ExternalLink className="size-4" aria-hidden="true" />
                  Open source
                </a>
              </Button>
            </div>
          ) : null}
          {source && canPreview && loading ? (
            <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
              <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
              Loading source…
            </div>
          ) : null}
          {source && canPreview && error ? (
            <EmptyPreview message="The source changed or you no longer have access." />
          ) : null}
          {preview && source ? <CitationPreviewContent preview={preview} title={source.title} /> : null}
        </div>

        {preview && source ? (
          <DialogFooter className="shrink-0 flex-row items-center justify-between border-t border-border-subtle px-4 py-3 sm:justify-between">
            <span className="text-xs text-muted-foreground">{metadata}</span>
            <div className="flex items-center gap-2">
              {preview.text !== undefined ? (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label="Copy source content"
                  onClick={() => {
                    void navigator.clipboard.writeText(preview.text ?? "").then(() => {
                      setCopied(true)
                      window.setTimeout(() => setCopied(false), 1_500)
                    })
                  }}
                >
                  {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
                </Button>
              ) : null}
              <Button variant="outline" size="sm" asChild>
                <a href={preview.blobUrl} download={source.title}>
                  <Download className="size-4" aria-hidden="true" />
                  Download
                </a>
              </Button>
            </div>
          </DialogFooter>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function CitationPreviewContent({
  preview,
  title,
}: {
  preview: PreviewPayload & { blobUrl: string }
  title: string
}) {
  if (preview.mediaType === "application/pdf") {
    return <iframe title={title} src={preview.blobUrl} className="h-full w-full bg-white" />
  }
  if (preview.mediaType.startsWith("image/")) {
    return (
      <div className="flex h-full items-center justify-center overflow-auto p-4">
        <img src={preview.blobUrl} alt={title} className="max-h-full max-w-full object-contain" />
      </div>
    )
  }
  if (preview.text !== undefined) {
    return (
      <pre className="h-full overflow-auto whitespace-pre-wrap p-6 font-mono text-sm leading-relaxed text-foreground">
        {preview.text}
      </pre>
    )
  }
  return <EmptyPreview message="Preview is unavailable for this file type. Download the original file." />
}

function EmptyPreview({ message }: { message: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
      <FileQuestion className="size-7 text-muted-foreground" aria-hidden="true" />
      <p className="max-w-xs text-sm text-muted-foreground">{message}</p>
    </div>
  )
}

function isTextPreview(mediaType: string) {
  return (
    mediaType.startsWith("text/") ||
    mediaType === "application/json" ||
    mediaType === "application/xml" ||
    mediaType.endsWith("+json") ||
    mediaType.endsWith("+xml")
  )
}

function previewMetadata(preview: PreviewPayload) {
  const parts: string[] = []
  if (preview.text !== undefined) {
    const lineCount = preview.text.split(/\r?\n/).length
    parts.push(`${lineCount} ${lineCount === 1 ? "line" : "lines"}`)
  } else {
    parts.push(preview.mediaType.split(";")[0] || "File")
  }
  parts.push(formatBytes(preview.blob.size))
  return parts.join(" · ")
}

function formatBytes(bytes: number) {
  if (bytes < 1_024) return `${bytes} B`
  const kibibytes = bytes / 1_024
  if (kibibytes < 1_024) return `${kibibytes.toFixed(1)} KB`
  return `${(kibibytes / 1_024).toFixed(1)} MB`
}

function sourceOrigin(source: AssistantSourceRef) {
  if (source.url.startsWith("/api/citations/")) return "OrgMemory document"
  try {
    return new URL(source.url).hostname
  } catch {
    return "External source"
  }
}

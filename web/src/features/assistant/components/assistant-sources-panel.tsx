import {
  BookOpen,
  Download,
  ExternalLink,
  FileQuestion,
  LoaderCircle,
  X,
} from "lucide-react"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
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
  title: string
  url: string
}

type PreviewState =
  | { status: "idle" | "loading" }
  | { status: "error" }
  | {
      status: "ready"
      blobUrl: string
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
        props.open ? "w-[min(32rem,38vw)]" : "w-0 border-l-0",
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
  const selected = sources.find((source) => source.id === selectedSourceId) ?? sources[0] ?? null
  const [preview, setPreview] = useState<PreviewState>({ status: "idle" })
  const canPreview = selected?.url.startsWith("/api/citations/") ?? false

  useEffect(() => {
    if (!selected || !canPreview) {
      setPreview({ status: "idle" })
      return
    }

    const controller = new AbortController()
    let objectUrl: string | undefined
    setPreview({ status: "loading" })

    void fetch(selected.url, {
      credentials: "same-origin",
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw new Error("Source is unavailable")
        const blob = await response.blob()
        objectUrl = URL.createObjectURL(blob)
        const mediaType = blob.type || "application/octet-stream"
        const text = isTextPreview(mediaType) ? await blob.text() : undefined
        if (!controller.signal.aborted) {
          setPreview({ status: "ready", blobUrl: objectUrl, mediaType, text })
        }
      })
      .catch((error: unknown) => {
        if (
          !controller.signal.aborted &&
          !(error instanceof DOMException && error.name === "AbortError")
        ) {
          setPreview({ status: "error" })
        }
      })

    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [canPreview, selected])

  const ready = preview.status === "ready" ? preview : null

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-border-subtle px-4">
        <div className="flex min-w-0 items-center gap-2">
          <BookOpen className="size-4 shrink-0 text-content-secondary" aria-hidden="true" />
          <h2 className="truncate font-medium">Sources</h2>
          <span className="text-xs tabular-nums text-muted-foreground">{sources.length}</span>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close sources">
          <X className="size-4" aria-hidden="true" />
        </Button>
      </header>

      <div className="grid min-h-0 flex-1 grid-rows-[auto_minmax(0,1fr)]">
        <div className="max-h-48 overflow-y-auto border-b border-border-subtle p-2">
          {sources.map((source, index) => (
            <button
              key={source.id}
              type="button"
              onClick={() => onSelect(source.id)}
              className={cn(
                "flex w-full items-start gap-3 rounded-lg px-3 py-2.5 text-left transition-colors",
                source.id === selected?.id
                  ? "bg-action-ghost-hover text-content-primary"
                  : "text-content-secondary hover:bg-surface-subtle hover:text-content-primary",
              )}
            >
              <span className="mt-0.5 text-xs tabular-nums text-muted-foreground">
                {index + 1}
              </span>
              <span className="line-clamp-2 text-sm font-medium">{source.title}</span>
            </button>
          ))}
        </div>

        <div className="min-h-0 overflow-auto bg-surface-subtle">
          {!selected ? (
            <EmptyPreview message="No source was attached to this answer." />
          ) : null}
          {selected && !canPreview ? (
            <div className="flex h-full flex-col items-center justify-center gap-4 p-6 text-center">
              <FileQuestion className="size-7 text-muted-foreground" aria-hidden="true" />
              <p className="text-sm text-muted-foreground">
                This source opens outside the secure evidence preview.
              </p>
              <Button variant="outline" asChild>
                <a href={selected.url} target="_blank" rel="noreferrer">
                  <ExternalLink className="size-4" aria-hidden="true" />
                  Open source
                </a>
              </Button>
            </div>
          ) : null}
          {preview.status === "loading" ? (
            <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
              <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
              Loading source…
            </div>
          ) : null}
          {preview.status === "error" ? (
            <EmptyPreview message="The source changed or you no longer have access." />
          ) : null}
          {ready && selected ? (
            <div className="flex min-h-full flex-col">
              <CitationPreviewContent preview={ready} title={selected.title} />
              <div className="sticky bottom-0 mt-auto flex justify-end border-t border-border-subtle bg-background/95 p-3 backdrop-blur">
                <Button variant="outline" size="sm" asChild>
                  <a href={ready.blobUrl} download={selected.title}>
                    <Download className="size-4" aria-hidden="true" />
                    Download
                  </a>
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function CitationPreviewContent({
  preview,
  title,
}: {
  preview: Extract<PreviewState, { status: "ready" }>
  title: string
}) {
  if (preview.mediaType === "application/pdf") {
    return <iframe title={title} src={preview.blobUrl} className="h-[calc(100vh-16rem)] w-full bg-white" />
  }
  if (preview.mediaType.startsWith("image/")) {
    return (
      <img
        src={preview.blobUrl}
        alt={title}
        className="mx-auto max-h-[calc(100vh-16rem)] max-w-full object-contain p-4"
      />
    )
  }
  if (preview.text !== undefined) {
    return (
      <pre className="min-h-full whitespace-pre-wrap p-5 font-mono text-sm leading-relaxed text-foreground">
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

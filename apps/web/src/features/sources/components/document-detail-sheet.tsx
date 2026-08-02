import { useQuery } from "@tanstack/react-query"
import { Download, FileQuestion, LoaderCircle } from "lucide-react"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { SourceStatusBadge } from "@/features/sources/components/source-status-badge"
import { sourcePreviewKind } from "@/features/sources/source-preview"
import { titleCase } from "@/features/sources/source-status"
import { readSourceContent } from "@/lib/hey-api"
import type { SourceResponse } from "@/lib/hey-api"
import { formatBytes, formatDate } from "@/lib/format"

interface PreviewPayload {
  blob: Blob
  mediaType: string
  text?: string
}

export function DocumentDetailSheet({
  source,
  onOpenChange,
}: {
  source: SourceResponse | null
  onOpenChange: (open: boolean) => void
}) {
  const sourceId = source?.id
  const preview = useQuery({
    queryKey: ["source-content-preview", sourceId],
    enabled: Boolean(sourceId && source?.contentAvailable),
    queryFn: async (): Promise<PreviewPayload> => {
      if (!sourceId) throw new Error("Document is unavailable")
      const { data } = await readSourceContent({
        path: { sourceId },
        parseAs: "blob",
        throwOnError: true,
      })
      if (!(data instanceof Blob)) throw new Error("Document is unavailable")
      const mediaType = data.type || "application/octet-stream"
      const text = sourcePreviewKind(mediaType) === "text" ? await data.text() : undefined
      return { blob: data, mediaType, text }
    },
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
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
    <Sheet open={source !== null} onOpenChange={onOpenChange}>
      <SheetContent className="flex w-full flex-col gap-0 p-0 sm:max-w-2xl">
        <SheetHeader className="border-b border-border-subtle px-6 py-5 text-left">
          <div className="flex flex-wrap items-center gap-2 pr-8">
            <SheetTitle className="min-w-0 truncate">
              {source?.title ?? source?.fileName ?? "Document"}
            </SheetTitle>
            {source ? <SourceStatusBadge source={source} /> : null}
          </div>
          <SheetDescription>
            Permission-verified metadata and original evidence for the current revision.
          </SheetDescription>
        </SheetHeader>

        {source ? (
          <div className="min-h-0 flex-1 overflow-y-auto p-6">
            <div className="grid gap-3 rounded-lg border bg-surface-subtle p-4 text-sm sm:grid-cols-2">
              <Metadata label="File" value={source.fileName ?? "—"} />
              <Metadata label="Size" value={formatBytes(source.contentLength)} />
              <Metadata label="Access" value={source.classification ? titleCase(source.classification) : "Policy controlled"} />
              <Metadata label="Updated" value={formatDate(source.updatedAt)} />
              <Metadata label="Media type" value={source.mediaType ?? "Document"} />
              <Metadata label="Source" value={source.sourceSystem ? titleCase(source.sourceSystem) : "Unknown"} />
            </div>

            <div className="mt-5 overflow-hidden rounded-lg border bg-background">
              <div className="flex items-center justify-between border-b px-4 py-3">
                <div>
                  <h3 className="font-medium">Original evidence</h3>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    Office files download instead of rendering inline.
                  </p>
                </div>
                {preview.data && blobUrl ? (
                  <Button variant="outline" size="sm" asChild>
                    <a href={blobUrl} download={source.fileName ?? source.title ?? "document"}>
                      <Download aria-hidden="true" /> Download
                    </a>
                  </Button>
                ) : null}
              </div>
              <div className="grid min-h-80 place-items-center bg-surface-sunken p-4">
                {!source.contentAvailable ? (
                  <EmptyPreview message="Original content becomes available after governed publication completes." />
                ) : preview.isPending || preview.isFetching || !blobUrl ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
                    <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                    Loading document
                  </div>
                ) : preview.isError ? (
                  <EmptyPreview message="The document is no longer available or permission has changed." />
                ) : preview.data ? (
                  <PreviewContent payload={preview.data} blobUrl={blobUrl} title={source.title ?? source.fileName ?? "Document"} />
                ) : null}
              </div>
            </div>
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

function PreviewContent({ payload, blobUrl, title }: { payload: PreviewPayload; blobUrl: string; title: string }) {
  switch (sourcePreviewKind(payload.mediaType)) {
    case "pdf":
      return <iframe title={title} src={blobUrl} sandbox="" className="h-[34rem] w-full bg-white" />
    case "image":
      return <img src={blobUrl} alt={title} className="max-h-[34rem] max-w-full object-contain" />
    case "text":
      return <pre className="max-h-[34rem] w-full overflow-auto whitespace-pre-wrap break-words rounded-md bg-background p-4 text-sm">{payload.text}</pre>
    default:
      return <EmptyPreview message="This file type is download-only." />
  }
}

function EmptyPreview({ message }: { message: string }) {
  return (
    <div className="flex max-w-sm flex-col items-center gap-3 text-center text-sm text-muted-foreground">
      <FileQuestion className="size-6" aria-hidden="true" />
      <p>{message}</p>
    </div>
  )
}

function Metadata({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 truncate">{value}</p>
    </div>
  )
}

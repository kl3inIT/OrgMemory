import { useQuery, type UseQueryResult } from "@tanstack/react-query"
import {
  Check,
  Copy,
  Download,
  ExternalLink,
  FileQuestion,
  LoaderCircle,
  RefreshCw,
  Upload,
} from "lucide-react"
import { useEffect, useState, type ReactNode } from "react"

import { RestrictedMarkdown } from "@/components/patterns/restricted-markdown"
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
  SourceFailureDetail,
  SourceStatusBadge,
} from "@/features/sources/components/source-status-badge"
import {
  sourceFormatLabel,
  sourcePreviewKind,
  type SourcePreviewKind,
} from "@/features/sources/source-preview"
import { titleCase } from "@/features/sources/source-status"
import { copyWithToast } from "@/lib/copy"
import { formatBytes, formatDate } from "@/lib/format"
import { readCitationContent, readCitationExcerpt, readSourceContent } from "@/lib/hey-api"
import type { CitationEvidenceExcerpt, SourceResponse } from "@/lib/hey-api"

export interface GovernedCitationSource {
  id: string
  citationNumber: number
  title: string
  url: string
  excerptUrl?: string
}

export type GovernedDocumentTarget =
  | {
      kind: "source"
      source: SourceResponse
      onUploadCorrection?: () => void
    }
  | {
      kind: "citation"
      source: GovernedCitationSource
    }

interface PreviewPayload {
  blob: Blob
  kind: SourcePreviewKind
  mediaType: string
  text?: string
}

export function GovernedDocumentViewer({
  target,
  onOpenChange,
}: {
  target: GovernedDocumentTarget | null
  onOpenChange: (open: boolean) => void
}) {
  const sourceTarget = target?.kind === "source" ? target : null
  const citationTarget = target?.kind === "citation" ? target : null
  const sourceId = sourceTarget?.source.id
  const citationId = citationTarget ? citationChunkId(citationTarget.source) : undefined

  const sourcePreview = useQuery({
    queryKey: ["source-content-preview", sourceId],
    enabled: Boolean(sourceId && sourceTarget?.source.contentAvailable),
    queryFn: async (): Promise<PreviewPayload> => {
      if (!sourceId || !sourceTarget) throw new Error("Document is unavailable")
      const { data } = await readSourceContent({
        path: { sourceId },
        parseAs: "blob",
        throwOnError: true,
      })
      if (!(data instanceof Blob)) throw new Error("Document is unavailable")
      const mediaType = data.type || "application/octet-stream"
      const kind = sourcePreviewKind(mediaType, sourceTarget.source.mediaType)
      const text = kind === "text" || kind === "markdown" ? await data.text() : undefined
      return { blob: data, kind, mediaType, text }
    },
    gcTime: 30_000,
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })

  const excerpt = useQuery({
    queryKey: ["assistant-citation-excerpt", citationId],
    enabled: Boolean(citationId && citationTarget),
    queryFn: async (): Promise<CitationEvidenceExcerpt> => {
      if (!citationId) throw new Error("Citation is unavailable")
      const { data } = await readCitationExcerpt({
        path: { chunkId: citationId },
        throwOnError: true,
      })
      return data
    },
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  })

  const citationPreview = useQuery({
    queryKey: ["assistant-citation-preview", citationId],
    enabled: Boolean(
      citationId &&
        citationTarget &&
        excerpt.isSuccess &&
        excerpt.data.presentationKind !== "DOWNLOAD",
    ),
    queryFn: async (): Promise<PreviewPayload> => {
      if (!citationId || !excerpt.data) throw new Error("Citation is unavailable")
      const { data } = await readCitationContent({
        path: { chunkId: citationId },
        parseAs: "blob",
        throwOnError: true,
      })
      if (!(data instanceof Blob)) throw new Error("Source is unavailable")
      const mediaType = data.type || "application/octet-stream"
      const kind = citationPresentationKind(excerpt.data.presentationKind)
      const text = kind === "text" || kind === "markdown" ? await data.text() : undefined
      return { blob: data, kind, mediaType, text }
    },
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  })

  const preview = sourceTarget ? sourcePreview : citationPreview
  const [blobUrl, setBlobUrl] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!preview.data || preview.isFetching) {
      setBlobUrl(null)
      return
    }
    const objectUrl = URL.createObjectURL(preview.data.blob)
    setBlobUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [preview.data, preview.isFetching])

  const title = documentTitle(target)
  const readyPreview = preview.data && blobUrl && !preview.isFetching
    ? { ...preview.data, blobUrl }
    : null
  const description = viewerDescription(target, readyPreview, excerpt.data)
  const downloadHref = readyPreview?.blobUrl ?? citationTarget?.source.url
  const copyText = readyPreview?.text ?? excerpt.data?.excerpt

  return (
    <Dialog open={target !== null} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-[100dvh] w-screen max-w-none flex-col gap-0 overflow-hidden rounded-none border-0 p-0 sm:h-[min(86dvh,58rem)] sm:w-[min(94vw,76rem)] sm:max-w-none sm:rounded-xl sm:border">
        <DialogHeader className="shrink-0 gap-2 border-b border-border-subtle px-5 py-4 pr-12 text-left sm:px-6">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <DialogTitle className="min-w-0 truncate text-section-title">{title}</DialogTitle>
            {sourceTarget ? <SourceStatusBadge source={sourceTarget.source} /> : null}
          </div>
          <DialogDescription className="truncate">{description}</DialogDescription>
        </DialogHeader>

        {sourceTarget ? <SourceContext target={sourceTarget} /> : null}

        <div className="min-h-0 flex-1 overflow-hidden bg-surface-sunken">
          <ViewerBody
            target={target}
            citationId={citationId}
            excerpt={excerpt}
            preview={preview}
            readyPreview={readyPreview}
          />
        </div>

        {target && (downloadHref || copyText) ? (
          <DialogFooter className="shrink-0 flex-row items-center justify-between border-t border-border-subtle px-4 py-3 sm:justify-between sm:px-5">
            <span className="min-w-0 truncate text-xs text-muted-foreground">{description}</span>
            <div className="flex shrink-0 items-center gap-2">
              {copyText ? (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label="Copy document content"
                  onClick={() =>
                    void copyWithToast(copyText, "Document content").then((didCopy) => {
                      if (!didCopy) return
                      setCopied(true)
                      window.setTimeout(() => setCopied(false), 1_500)
                    })
                  }
                >
                  {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
                </Button>
              ) : null}
              {downloadHref ? (
                <Button variant="outline" size="sm" asChild>
                  <a href={downloadHref} download={title}>
                    <Download className="size-4" aria-hidden="true" />
                    Download
                  </a>
                </Button>
              ) : null}
            </div>
          </DialogFooter>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function SourceContext({ target }: { target: Extract<GovernedDocumentTarget, { kind: "source" }> }) {
  const { source } = target
  const terminalFailure = source.status === "FAILED" || source.status === "QUARANTINED"
  return (
    <div className="shrink-0 border-b border-border-subtle bg-background px-5 py-3 sm:px-6">
      <dl className="flex min-w-0 flex-wrap gap-x-5 gap-y-1 text-xs text-muted-foreground">
        <InlineMetadata label="File" value={source.fileName ?? "—"} />
        <InlineMetadata label="Format" value={sourceFormatLabel(source.mediaType, source.fileName)} />
        <InlineMetadata label="Size" value={formatBytes(source.contentLength)} />
        <InlineMetadata
          label="Classification"
          value={source.classification ? titleCase(source.classification) : "Policy controlled"}
        />
        <InlineMetadata
          label="Space"
          value={source.knowledgeSpaceName ?? source.knowledgeSpaceKey ?? "—"}
        />
        {source.owningDepartmentName ? (
          <InlineMetadata label="Owned by" value={source.owningDepartmentName} />
        ) : null}
        {source.uploadedByName ? (
          <InlineMetadata label="Uploaded by" value={source.uploadedByName} />
        ) : null}
        <InlineMetadata label="Updated" value={formatDate(source.updatedAt)} />
      </dl>
      {terminalFailure ? (
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2">
          <SourceFailureDetail source={source} />
          {source.status === "QUARANTINED" && target.onUploadCorrection ? (
            <Button variant="outline" size="sm" onClick={target.onUploadCorrection}>
              <Upload aria-hidden="true" /> Upload corrected document
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function ViewerBody({
  target,
  citationId,
  excerpt,
  preview,
  readyPreview,
}: {
  target: GovernedDocumentTarget | null
  citationId?: string
  excerpt: UseQueryResult<CitationEvidenceExcerpt, Error>
  preview: UseQueryResult<PreviewPayload, Error>
  readyPreview: (PreviewPayload & { blobUrl: string }) | null
}) {
  if (!target) return null

  if (target.kind === "citation" && !citationId) {
    return (
      <CenteredState icon={<FileQuestion className="size-7" aria-hidden="true" />}>
        <p>This source opens outside the secure evidence preview.</p>
        <Button variant="outline" asChild>
          <a href={target.source.url} target="_blank" rel="noreferrer">
            <ExternalLink className="size-4" aria-hidden="true" /> Open source
          </a>
        </Button>
      </CenteredState>
    )
  }

  if (target.kind === "citation") {
    if (excerpt.isPending || excerpt.isFetching) return <LoadingState label="Loading source" />
    if (excerpt.isError) {
      return <EmptyPreview message="The source changed or you no longer have access." />
    }
    if (excerpt.data.presentationKind === "DOWNLOAD") {
      return <CitationExcerpt excerpt={excerpt.data} />
    }
    if (readyPreview) return <PreviewContent preview={readyPreview} title={target.source.title} />
    if (preview.isError) {
      return (
        <CitationExcerpt
          excerpt={excerpt.data}
          note="The excerpt is available, but the original preview could not be loaded."
        />
      )
    }
    return <CitationExcerpt excerpt={excerpt.data} note="Loading the original preview…" loading />
  }

  if (!target.source.contentAvailable) {
    if (target.source.publicationComplete) {
      const contact = target.source.owningDepartmentName
        ? ` Contact ${target.source.owningDepartmentName} to request access.`
        : ""
      return (
        <EmptyPreview
          message={`This document's content is outside your access scope.${contact}`}
        />
      )
    }
    return (
      <EmptyPreview message="Original content becomes available after governed publication completes." />
    )
  }
  if (preview.isError) {
    return (
      <CenteredState icon={<FileQuestion className="size-7" aria-hidden="true" />}>
        <p>The document is no longer available or permission has changed.</p>
        <Button variant="outline" size="sm" onClick={() => preview.refetch()}>
          <RefreshCw aria-hidden="true" /> Retry
        </Button>
      </CenteredState>
    )
  }
  if (preview.isPending || preview.isFetching || !readyPreview) {
    return <LoadingState label="Loading document" />
  }
  return <PreviewContent preview={readyPreview} title={documentTitle(target)} />
}

function PreviewContent({
  preview,
  title,
}: {
  preview: PreviewPayload & { blobUrl: string }
  title: string
}) {
  switch (preview.kind) {
    case "pdf":
      return <iframe title={title} src={preview.blobUrl} sandbox="" className="size-full bg-white" />
    case "image":
      return (
        <div className="flex size-full items-center justify-center overflow-auto p-5">
          <img src={preview.blobUrl} alt={title} className="max-h-full max-w-full object-contain" />
        </div>
      )
    case "markdown":
      return <MarkdownPreview content={preview.text ?? ""} />
    case "text":
      return (
        <pre className="size-full overflow-auto whitespace-pre-wrap break-words bg-background p-6 font-mono text-sm leading-relaxed">
          {preview.text}
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

function CitationExcerpt({
  excerpt,
  note,
  loading = false,
}: {
  excerpt: CitationEvidenceExcerpt
  note?: string
  loading?: boolean
}) {
  return (
    <div className="size-full overflow-auto p-5 sm:p-8">
      <article className="mx-auto max-w-3xl rounded-xl border border-border-subtle bg-background p-5 sm:p-6">
        <p className="text-metadata font-medium uppercase tracking-wide text-content-muted">
          Evidence excerpt
        </p>
        {excerpt.heading ? (
          <h3 className="mt-2 text-section-title text-content-primary">{excerpt.heading}</h3>
        ) : null}
        <p className="mt-3 whitespace-pre-wrap text-body leading-7 text-content-primary">
          {excerpt.excerpt}
          {excerpt.truncated ? "…" : ""}
        </p>
        {note ? (
          <p className="mt-4 flex items-center gap-2 text-supporting text-content-muted">
            {loading ? <LoaderCircle className="size-3.5 animate-spin" aria-hidden="true" /> : null}
            {note}
          </p>
        ) : null}
      </article>
    </div>
  )
}

function LoadingState({ label }: { label: string }) {
  return (
    <div className="flex size-full items-center justify-center gap-2 text-sm text-muted-foreground" role="status">
      <LoaderCircle className="size-4 animate-spin" aria-hidden="true" /> {label}
    </div>
  )
}

function EmptyPreview({ message }: { message: string }) {
  return (
    <CenteredState icon={<FileQuestion className="size-7" aria-hidden="true" />}>
      <p>{message}</p>
    </CenteredState>
  )
}

function CenteredState({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex size-full flex-col items-center justify-center gap-3 p-6 text-center text-sm text-muted-foreground">
      {icon}
      {children}
    </div>
  )
}

function InlineMetadata({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-w-0 gap-1.5">
      <dt className="font-medium text-foreground/70">{label}</dt>
      <dd className="max-w-56 truncate" title={value}>{value}</dd>
    </div>
  )
}

function documentTitle(target: GovernedDocumentTarget | null) {
  if (!target) return "Document"
  return target.kind === "source"
    ? target.source.title ?? target.source.fileName ?? "Document"
    : target.source.title
}

function viewerDescription(
  target: GovernedDocumentTarget | null,
  preview: (PreviewPayload & { blobUrl: string }) | null,
  excerpt?: CitationEvidenceExcerpt,
) {
  if (preview) {
    const lineCount = preview.text?.split(/\r?\n/).length
    return [lineCount ? `${lineCount} ${lineCount === 1 ? "line" : "lines"}` : sourceFormatLabel(preview.mediaType), formatBytes(preview.blob.size)]
      .filter(Boolean)
      .join(" · ")
  }
  if (target?.kind === "citation") return excerptMetadata(excerpt) || "Permission-verified source evidence"
  if (target?.kind === "source") {
    return [sourceFormatLabel(target.source.mediaType, target.source.fileName), target.source.sourceSystem ? titleCase(target.source.sourceSystem) : undefined]
      .filter(Boolean)
      .join(" · ")
  }
  return "Permission-verified evidence"
}

function citationChunkId(source: GovernedCitationSource) {
  const content = /^\/api\/citations\/([^/]+)\/content$/.exec(source.url)?.[1]
  const excerpt = source.excerptUrl
    ? /^\/api\/citations\/([^/]+)\/excerpt$/.exec(source.excerptUrl)?.[1]
    : content
  return content && excerpt && decodeURIComponent(content) === decodeURIComponent(excerpt)
    ? decodeURIComponent(content)
    : undefined
}

function citationPresentationKind(kind?: CitationEvidenceExcerpt["presentationKind"]): SourcePreviewKind {
  switch (kind) {
    case "PDF":
      return "pdf"
    case "IMAGE":
      return "image"
    case "MARKDOWN":
      return "markdown"
    case "PLAIN_TEXT":
      return "text"
    default:
      return "download"
  }
}

function excerptMetadata(excerpt?: CitationEvidenceExcerpt) {
  if (!excerpt) return ""
  const pages = excerpt.startPage
    ? excerpt.endPage && excerpt.endPage !== excerpt.startPage
      ? `pages ${excerpt.startPage}–${excerpt.endPage}`
      : `page ${excerpt.startPage}`
    : undefined
  return [pages, excerpt.presentationKind?.replace("_", " ")]
    .filter(Boolean)
    .join(" · ")
}

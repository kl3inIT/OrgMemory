import { FileText, Search, X } from "lucide-react"

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
  citationNumber: number
  title: string
  url: string
  excerptUrl?: string
  available: boolean
}

interface AssistantSourcesPanelProps {
  open: boolean
  sources: AssistantSourceRef[]
  citedSourceIds: string[]
  selectedSourceId: string | null
  onClose: () => void
  onSelect: (sourceId: string) => void
  onPreview: (source: AssistantSourceRef) => void
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
  citedSourceIds,
  selectedSourceId,
  onClose,
  onSelect,
  onPreview,
}: AssistantSourcesPanelProps) {
  const citedIds = new Set(citedSourceIds)
  const citedSources = citedSourceIds
    .map((sourceId) => sources.find((source) => source.id === sourceId))
    .filter((source): source is AssistantSourceRef => source !== undefined)
  const otherSources = sources.filter((source) => !citedIds.has(source.id))

  const openSource = (source: AssistantSourceRef) => {
    if (!source.available) return
    onSelect(source.id)
    onPreview(source)
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-border-subtle px-4">
        <div className="flex min-w-0 items-center gap-2">
          <Search className="size-5 shrink-0 text-content-muted" aria-hidden="true" />
          <h2 className="truncate text-section-title text-content-primary">Sources</h2>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close sources">
          <X className="size-4" aria-hidden="true" />
        </Button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
        {sources.length ? (
          <div className="space-y-6">
            <SourceSection
              title="Cited sources"
              sources={citedSources}
              selectedSourceId={selectedSourceId}
              onOpen={openSource}
            />
            <SourceSection
              title={citedSources.length > 0 ? "More" : "Found sources"}
              sources={otherSources}
              selectedSourceId={selectedSourceId}
              onOpen={openSource}
            />
          </div>
        ) : (
          <div className="flex h-full items-center justify-center p-6 text-center text-sm text-muted-foreground">
            No source was attached to this answer.
          </div>
        )}
      </div>
    </div>
  )
}

function SourceSection({
  title,
  sources,
  selectedSourceId,
  onOpen,
}: {
  title: string
  sources: AssistantSourceRef[]
  selectedSourceId: string | null
  onOpen: (source: AssistantSourceRef) => void
}) {
  if (sources.length === 0) return null

  return (
    <section aria-label={title}>
      <h3 className="px-3 pb-2 text-metadata font-medium uppercase tracking-wide text-content-muted">
        {title}
      </h3>
      <div className="space-y-1">
        {sources.map((source) => (
          <SourceListItem
            key={source.id}
            source={source}
            selected={source.id === selectedSourceId}
            onOpen={() => onOpen(source)}
          />
        ))}
      </div>
    </section>
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
      disabled={!source.available}
      aria-label={
        source.available
          ? `Preview source ${source.citationNumber}: ${source.title}`
          : `Source ${source.citationNumber} is no longer available`
      }
      aria-current={selected ? "true" : undefined}
      className={cn(
        "group flex w-full gap-2.5 rounded-xl p-3 text-left transition-colors",
        !source.available && "cursor-default opacity-60",
        selected
          ? "bg-action-ghost-hover"
          : source.available
            ? "hover:bg-surface-subtle"
            : undefined,
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

function sourceOrigin(source: AssistantSourceRef) {
  if (!source.available) return "No longer available"
  if (source.url.startsWith("/api/citations/")) return "OrgMemory document"
  if (source.url.startsWith("/api/assistant/files/")) return "Private Assistant file"
  try {
    return new URL(source.url).hostname
  } catch {
    return "External source"
  }
}

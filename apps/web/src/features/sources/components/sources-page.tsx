import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight, Files, LoaderCircle, RefreshCw, Search } from "lucide-react"
import { lazy, Suspense, useCallback, useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { PageLayout } from "@/components/layouts/page-layout"
import { EmptyState } from "@/components/patterns/empty-state"
import { FilterBar } from "@/components/patterns/filter-bar"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { SourceUploadDialog } from "@/features/sources/components/source-upload-dialog"
import { SourcesTable } from "@/features/sources/components/sources-table"
import { DocumentDetailDialog } from "@/features/sources/components/document-detail-dialog"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import {
  SOURCE_STATUS_FILTERS,
  sourceStatusCountFromPage,
  type SourceStatusFilter,
} from "@/features/sources/source-status"
import { useDocumentManagerStore } from "@/features/sources/store/document-manager-store"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import {
  listKnowledgeSpaceUploadTargetsOptions,
  listSourcesOptions,
  listSourcesQueryKey,
  listVisibleKnowledgeSpacesOptions,
  uploadSourceMutation,
  deleteSourceMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { SourceResponse } from "@/lib/hey-api"
import { apiErrorMessage } from "@/lib/api-error"

type ClassificationFilter = "ALL" | "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"

const SOURCE_PAGE_SIZE = 25

const KnowledgeGraphPanel = lazy(() =>
  import("@/features/sources/components/knowledge-graph-panel").then((module) => ({
    default: module.KnowledgeGraphPanel,
  })),
)

export function SourcesPage({
  search,
  view,
  onSearchChange,
  onViewChange,
}: {
  search: string
  view: "documents" | "graph"
  onSearchChange: (search: string) => void
  onViewChange: (view: "documents" | "graph") => void
}) {
  const queryClient = useQueryClient()
  const statusFilter = useDocumentManagerStore((state) => state.statusFilter)
  const setStatusFilter = useDocumentManagerStore((state) => state.setStatusFilter)
  const [viewingId, setViewingId] = useState<string | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [deleteCandidate, setDeleteCandidate] = useState<SourceResponse | null>(null)
  const [knowledgeSpaceFilter, setKnowledgeSpaceFilter] = useState("ALL")
  const [classificationFilter, setClassificationFilter] = useState<ClassificationFilter>("ALL")
  const [cursorHistory, setCursorHistory] = useState<Array<string | undefined>>([undefined])
  const currentCursor = cursorHistory.at(-1)
  const debouncedSearch = useDebouncedValue(search.trim())
  const sources = useQuery({
    ...listSourcesOptions({
      query: {
        knowledgeSpaceId: knowledgeSpaceFilter === "ALL" ? undefined : knowledgeSpaceFilter,
        classification: classificationFilter === "ALL" ? undefined : classificationFilter,
        status: statusFilter === "ALL" ? undefined : statusFilter,
        q: debouncedSearch || undefined,
        cursor: currentCursor,
        pageSize: SOURCE_PAGE_SIZE,
      },
    }),
    refetchInterval: (query) =>
      (query.state.data?.statusCounts?.processing ?? 0) > 0 ? 2000 : false,
  })
  const uploadTargets = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const visibleSpaces = useQuery(listVisibleKnowledgeSpacesOptions())
  const upload = useMutation({
    ...uploadSourceMutation(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: listSourcesQueryKey() })
      toast.success("Document uploaded. Ingestion has started.")
    },
  })
  const remove = useMutation({
    ...deleteSourceMutation(),
    onSuccess: async () => {
      setDeleteCandidate(null)
      setViewingId(null)
      await queryClient.invalidateQueries({ queryKey: listSourcesQueryKey() })
      toast.success("Document deleted from active knowledge.")
    },
    onError: (error) =>
      toast.error(apiErrorMessage(error, "The document could not be deleted.")),
  })

  const documents = sources.data?.items ?? []
  const viewing = documents.find((source) => source.id === viewingId) ?? null
  const viewDocument = useCallback((source: SourceResponse) => {
    setViewingId(source.id ?? null)
  }, [])
  const closeDocument = useCallback(() => {
    if (viewingId) {
      queryClient.removeQueries({
        queryKey: ["source-content-preview", viewingId],
        exact: true,
      })
    }
    setViewingId(null)
  }, [queryClient, viewingId])
  const uploadCorrection = useCallback(() => {
    closeDocument()
    setUploadOpen(true)
  }, [closeDocument])
  const visibleDocumentCount = sourceStatusCountFromPage(sources.data, statusFilter)
  const visibleDocumentLabel = formatVisibleDocumentCount(
    visibleDocumentCount,
    debouncedSearch.length > 0,
  )
  const outOfRange = Boolean(currentCursor && sources.data && documents.length === 0)
  const filtersActive =
    knowledgeSpaceFilter !== "ALL" ||
    classificationFilter !== "ALL" ||
    statusFilter !== "ALL" ||
    debouncedSearch.length > 0

  return (
    <PageLayout.Root variant={view === "graph" ? "canvas" : "wide"}>
      <Tabs
        value={view}
        onValueChange={(value: string) => onViewChange(value as "documents" | "graph")}
        className={view === "graph" ? "min-h-0 flex-1 gap-4" : "gap-6"}
      >
        <PageLayout.Tabs>
          <TabsList aria-label="Knowledge workspace" className="h-10 gap-1">
            <TabsTrigger value="documents" className="min-w-28 px-4">
              Documents
            </TabsTrigger>
            <TabsTrigger value="graph" className="min-w-36 px-4">
              Knowledge graph
            </TabsTrigger>
          </TabsList>
        </PageLayout.Tabs>

        <TabsContent value="documents" className="space-y-6">
          <PageLayout.Header
            title="Documents"
            actions={
              <SourceUploadDialog
                open={uploadOpen}
                onOpenChange={setUploadOpen}
                pending={upload.isPending}
                spaces={uploadTargets.data ?? []}
                spacesPending={uploadTargets.isPending}
                spacesError={uploadTargets.isError}
                onRetrySpaces={() => uploadTargets.refetch()}
                onUpload={async (input) =>
                  upload
                    .mutateAsync({
                      body: { file: input.file },
                      query: {
                        classification: input.classification,
                        knowledgeSpaceId: input.knowledgeSpaceId,
                      },
                    })
                    .then(() => undefined)
                }
              />
            }
          />

          <Tabs
            value={statusFilter}
            onValueChange={(value: string) => {
              setCursorHistory([undefined])
              setStatusFilter(value as SourceStatusFilter)
            }}
          >
            <TabsList
              variant="line"
              className="h-auto w-full justify-start gap-4 overflow-x-auto overflow-y-hidden border-b p-0 [scrollbar-width:none] sm:gap-6 [&::-webkit-scrollbar]:hidden"
              aria-label="Document status"
            >
              {SOURCE_STATUS_FILTERS.map((filter) => {
                const count = sourceStatusCountFromPage(sources.data, filter.value)
                return (
                  <TabsTrigger
                    key={filter.value}
                    value={filter.value}
                    className="flex-none gap-2 px-0 py-3"
                    aria-label={`${filter.label}, ${formatDocumentCount(count)}`}
                  >
                    {filter.compactLabel ? (
                      <>
                        <span className="sm:hidden">{filter.compactLabel}</span>
                        <span className="hidden sm:inline">{filter.label}</span>
                      </>
                    ) : (
                      filter.label
                    )}
                    {count > 0 ? (
                      <Badge
                        variant="muted"
                        className="h-5 min-w-5 justify-center rounded-full px-1.5 text-[11px] tabular-nums"
                        aria-hidden="true"
                      >
                        {count.toLocaleString()}
                      </Badge>
                    ) : null}
                  </TabsTrigger>
                )
              })}
            </TabsList>
          </Tabs>

          <section
            className="min-h-[32rem] overflow-hidden rounded-lg border bg-card"
            aria-label="Documents"
          >
            <div className="sticky top-0 z-10 border-b bg-card p-3">
              <FilterBar
                search={
                  <InputGroup className="max-w-md shadow-none">
                    <InputGroupAddon>
                      <Search aria-hidden="true" />
                    </InputGroupAddon>
                    <InputGroupInput
                      type="search"
                      value={search}
                      placeholder="Search documents"
                      aria-label="Search documents"
                      onChange={(event) => {
                        setCursorHistory([undefined])
                        onSearchChange(event.target.value)
                      }}
                    />
                  </InputGroup>
                }
                filters={
                  <>
                    <Select
                      value={knowledgeSpaceFilter}
                      onValueChange={(value: string) => {
                        setCursorHistory([undefined])
                        setKnowledgeSpaceFilter(value)
                      }}
                    >
                      <SelectTrigger
                        className="w-full sm:w-52"
                        aria-label="Filter by Knowledge Space"
                        disabled={visibleSpaces.isPending || visibleSpaces.isError}
                      >
                        <SelectValue placeholder="All spaces" />
                      </SelectTrigger>
                      <SelectContent align="start">
                        <SelectItem value="ALL">All spaces</SelectItem>
                        {(visibleSpaces.data ?? []).map((space) =>
                          space.id ? (
                            <SelectItem key={space.id} value={space.id}>
                              {space.name ?? space.key ?? "Knowledge space"}
                            </SelectItem>
                          ) : null,
                        )}
                      </SelectContent>
                    </Select>
                    <Select
                      value={classificationFilter}
                      onValueChange={(value: string) => {
                        setCursorHistory([undefined])
                        setClassificationFilter(value as ClassificationFilter)
                      }}
                    >
                      <SelectTrigger className="w-full sm:w-44" aria-label="Filter by classification">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent align="start">
                        <SelectItem value="ALL">All classifications</SelectItem>
                        <SelectItem value="PUBLIC">Public</SelectItem>
                        <SelectItem value="INTERNAL">Internal</SelectItem>
                        <SelectItem value="CONFIDENTIAL">Confidential</SelectItem>
                        <SelectItem value="RESTRICTED">Restricted</SelectItem>
                      </SelectContent>
                    </Select>
                  </>
                }
                result={visibleDocumentLabel}
                actions={
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={sources.isFetching}
                    onClick={() => sources.refetch()}
                  >
                    <RefreshCw
                      className={sources.isFetching ? "animate-spin" : ""}
                      aria-hidden="true"
                    />
                    Refresh
                  </Button>
                }
              />
            </div>

            {sources.isPending ? <SourcesLoading /> : null}
            {sources.isError ? <SourcesError onRetry={() => sources.refetch()} /> : null}
            {outOfRange ? (
              <SourcesPageCorrection
                onPrevious={() => setCursorHistory((current) => current.slice(0, -1))}
              />
            ) : null}
            {!outOfRange && sources.data && documents.length === 0 ? (
              filtersActive ? <SourcesNoResults /> : <SourcesEmpty />
            ) : null}
            {documents.length > 0 ? (
              <SourcesTable
                sources={documents}
                onView={viewDocument}
                onDelete={setDeleteCandidate}
                onUploadCorrection={uploadCorrection}
              />
            ) : null}
            {!sources.isPending && !sources.isError && !outOfRange && visibleDocumentCount > 0 ? (
              <SourceCursorPagination
                page={cursorHistory.length}
                total={visibleDocumentCount}
                hasPrevious={cursorHistory.length > 1}
                hasNext={Boolean(sources.data?.nextCursor)}
                disabled={sources.isFetching}
                onPrevious={() => {
                  setViewingId(null)
                  setCursorHistory((current) => current.slice(0, -1))
                }}
                onNext={() => {
                  const nextCursor = sources.data?.nextCursor
                  if (!nextCursor) return
                  setViewingId(null)
                  setCursorHistory((current) => [...current, nextCursor])
                }}
              />
            ) : null}
          </section>
        </TabsContent>
        <TabsContent value="graph" className="flex min-h-0 flex-1">
          <Suspense fallback={<KnowledgeGraphLoading />}>
            <KnowledgeGraphPanel />
          </Suspense>
        </TabsContent>
      </Tabs>

      <DocumentDetailDialog
        source={viewing}
        onOpenChange={(open) => !open && closeDocument()}
        onUploadCorrection={uploadCorrection}
      />

      <AlertDialog
        open={deleteCandidate !== null}
        onOpenChange={(open) => {
          if (!open && !remove.isPending) setDeleteCandidate(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this document?</AlertDialogTitle>
            <AlertDialogDescription>
              “{deleteCandidate?.title ?? deleteCandidate?.fileName ?? "This document"}” will
              disappear from Documents, retrieval, and the knowledge graph. Retained evidence
              continues to follow the organization retention policy.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>Keep document</AlertDialogCancel>
            <AlertDialogAction
              disabled={!deleteCandidate?.id || !deleteCandidate.deletionAllowed || remove.isPending}
              onClick={() => {
                if (!deleteCandidate?.id || !deleteCandidate.deletionAllowed) return
                remove.mutate({ path: { sourceId: deleteCandidate.id } })
              }}
            >
              Delete document
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </PageLayout.Root>
  )
}

function KnowledgeGraphLoading() {
  return (
    <div
      className="flex min-h-72 flex-1 items-center justify-center gap-2 text-sm text-muted-foreground"
      role="status"
    >
      <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
      Loading knowledge graph
    </div>
  )
}

function formatDocumentCount(count: number) {
  return `${count.toLocaleString()} ${count === 1 ? "document" : "documents"}`
}

function formatVisibleDocumentCount(count: number, hasSearch: boolean) {
  if (count === 0) return hasSearch ? "No results" : "No documents"
  if (hasSearch) return `${count.toLocaleString()} ${count === 1 ? "result" : "results"}`
  return formatDocumentCount(count)
}

function SourcesLoading() {
  return (
    <div
      className="flex min-h-72 items-center justify-center gap-2 text-sm text-muted-foreground"
      role="status"
    >
      <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
      Loading documents
    </div>
  )
}

function SourcesError({ onRetry }: { onRetry: () => void }) {
  return (
    <Card className="m-4 border-destructive/30 shadow-none">
      <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
        <p className="text-sm text-destructive">Documents could not be loaded.</p>
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      </CardContent>
    </Card>
  )
}

function SourcesEmpty() {
  return (
    <EmptyState
      title="No documents yet"
      description="Upload one clean document to start the knowledge index."
      icon={<Files className="size-5" aria-hidden="true" />}
    />
  )
}

function SourcesNoResults() {
  return (
    <EmptyState
      title="No documents found"
      description="Try another space, classification, status, or search term."
      icon={<Search className="size-5" aria-hidden="true" />}
    />
  )
}

function SourcesPageCorrection({ onPrevious }: { onPrevious: () => void }) {
  return (
    <EmptyState
      title="This page is no longer available"
      description="Documents changed while you were browsing. Return to the previous page to continue."
      icon={<Files className="size-5" aria-hidden="true" />}
      action={
        <Button variant="outline" onClick={onPrevious}>
          <ChevronLeft aria-hidden="true" />
          Previous page
        </Button>
      }
    />
  )
}

function SourceCursorPagination({
  page,
  total,
  hasPrevious,
  hasNext,
  disabled,
  onPrevious,
  onNext,
}: {
  page: number
  total: number
  hasPrevious: boolean
  hasNext: boolean
  disabled: boolean
  onPrevious: () => void
  onNext: () => void
}) {
  const first = Math.min((page - 1) * SOURCE_PAGE_SIZE + 1, total)
  const last = Math.min(page * SOURCE_PAGE_SIZE, total)
  return (
    <div className="flex flex-col gap-3 border-t px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm tabular-nums text-content-muted" aria-live="polite">
        Showing {first.toLocaleString()}–{last.toLocaleString()} of {total.toLocaleString()}
      </p>
      <nav className="flex items-center gap-1 self-end" aria-label="Document pages" aria-busy={disabled}>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={disabled || !hasPrevious}
          onClick={onPrevious}
        >
          <ChevronLeft aria-hidden="true" />
          Previous
        </Button>
        <span className="px-2 text-sm tabular-nums text-content-muted">Page {page}</span>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={disabled || !hasNext}
          onClick={onNext}
        >
          Next
          <ChevronRight aria-hidden="true" />
        </Button>
      </nav>
    </div>
  )
}

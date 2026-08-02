import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Files, LoaderCircle, RefreshCw, Search } from "lucide-react"
import { lazy, Suspense, useState } from "react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { PageLayout } from "@/components/layouts/page-layout"
import { EmptyState } from "@/components/patterns/empty-state"
import { FilterBar } from "@/components/patterns/filter-bar"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { SourceUploadDialog } from "@/features/sources/components/source-upload-dialog"
import { SourcesTable } from "@/features/sources/components/sources-table"
import { DocumentDetailSheet } from "@/features/sources/components/document-detail-sheet"
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
  ACTIVE_SOURCE_STATUSES,
  matchesSourceStatus,
  SOURCE_STATUS_FILTERS,
  sourceStatusCount,
  type SourceStatusFilter,
} from "@/features/sources/source-status"
import { useDocumentManagerStore } from "@/features/sources/store/document-manager-store"
import {
  listKnowledgeSpaceUploadTargetsOptions,
  listSourcesOptions,
  listSourcesQueryKey,
  uploadSourceMutation,
  deleteSourceMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { SourceResponse } from "@/lib/hey-api"
import { apiErrorMessage } from "@/lib/api-error"

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
  const [viewing, setViewing] = useState<SourceResponse | null>(null)
  const [deleteCandidate, setDeleteCandidate] = useState<SourceResponse | null>(null)
  const sources = useQuery({
    ...listSourcesOptions(),
    refetchInterval: (query) =>
      query.state.data?.some((source) => ACTIVE_SOURCE_STATUSES.has(source.status ?? ""))
        ? 2000
        : false,
  })
  const uploadTargets = useQuery(listKnowledgeSpaceUploadTargetsOptions())
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
      setViewing(null)
      await queryClient.invalidateQueries({ queryKey: listSourcesQueryKey() })
      toast.success("Document deleted from active knowledge.")
    },
    onError: (error) =>
      toast.error(apiErrorMessage(error, "The document could not be deleted.")),
  })

  const documents = sources.data ?? []
  const normalizedSearch = search.trim().toLocaleLowerCase()
  const filteredDocuments = documents.filter((source) => {
    if (!matchesSourceStatus(source, statusFilter)) return false
    if (!normalizedSearch) return true
    return [source.title, source.fileName, source.mediaType]
      .filter(Boolean)
      .some((value) => value?.toLocaleLowerCase().includes(normalizedSearch))
  })
  const visibleDocumentLabel = formatVisibleDocumentCount(
    filteredDocuments.length,
    normalizedSearch.length > 0,
  )

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
            onValueChange={(value: string) => setStatusFilter(value as SourceStatusFilter)}
          >
            <TabsList
              variant="line"
              className="h-auto w-full justify-start gap-4 overflow-x-auto overflow-y-hidden border-b p-0 [scrollbar-width:none] sm:gap-6 [&::-webkit-scrollbar]:hidden"
              aria-label="Document status"
            >
              {SOURCE_STATUS_FILTERS.map((filter) => {
                const count = sourceStatusCount(documents, filter.value)
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

          <section className="overflow-hidden rounded-lg border bg-card" aria-label="Documents">
            <div className="border-b p-3">
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
                      onChange={(event) => onSearchChange(event.target.value)}
                    />
                  </InputGroup>
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
            {sources.data?.length === 0 ? <SourcesEmpty /> : null}
            {sources.data && sources.data.length > 0 ? (
              <SourcesTable
                sources={filteredDocuments}
                onView={setViewing}
                onDelete={setDeleteCandidate}
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

      <DocumentDetailSheet
        source={viewing}
        onOpenChange={(open) => !open && setViewing(null)}
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

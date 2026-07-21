import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Files, LoaderCircle, RefreshCw, Search } from "lucide-react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { uploadSourceWithCsrf } from "@/features/sources/api/upload-source"
import { SourceUploadDialog } from "@/features/sources/components/source-upload-dialog"
import { SourcesTable } from "@/features/sources/components/sources-table"
import {
  ACTIVE_SOURCE_STATUSES,
  matchesSourceStatus,
  SOURCE_STATUS_FILTERS,
  sourceStatusCount,
  type SourceStatusFilter,
} from "@/features/sources/source-status"
import { useDocumentManagerStore } from "@/features/sources/store/document-manager-store"
import { listSourcesOptions, listSourcesQueryKey } from "@/lib/hey-api/@tanstack/react-query.gen"

export function SourcesPage() {
  const queryClient = useQueryClient()
  const search = useDocumentManagerStore((state) => state.search)
  const setSearch = useDocumentManagerStore((state) => state.setSearch)
  const statusFilter = useDocumentManagerStore((state) => state.statusFilter)
  const setStatusFilter = useDocumentManagerStore((state) => state.setStatusFilter)
  const sources = useQuery({
    ...listSourcesOptions(),
    refetchInterval: (query) =>
      query.state.data?.some((source) => ACTIVE_SOURCE_STATUSES.has(source.status ?? "")) ? 2000 : false,
  })
  const upload = useMutation({
    mutationFn: uploadSourceWithCsrf,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: listSourcesQueryKey() })
      toast.success("Document uploaded. Ingestion has started.")
    },
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

  return (
    <main className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-7xl space-y-6 p-4 md:p-8">
        <Tabs value="documents" className="gap-6">
          <TabsList aria-label="Knowledge workspace" className="h-10 gap-1">
            <TabsTrigger
              value="documents"
              className="min-w-28 px-4 data-[state=active]:bg-emerald-500 data-[state=active]:text-white"
            >
              Documents
            </TabsTrigger>
            <TabsTrigger value="knowledge-graph" className="min-w-36 px-4" disabled>
              Knowledge graph
            </TabsTrigger>
          </TabsList>

          <TabsContent value="documents" className="space-y-6">
            <header className="flex items-center justify-between gap-5">
              <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
              <SourceUploadDialog
                pending={upload.isPending}
                onUpload={async (input) => upload.mutateAsync(input).then(() => undefined)}
              />
            </header>

            <Tabs
              value={statusFilter}
              onValueChange={(value: string) => setStatusFilter(value as SourceStatusFilter)}
            >
              <TabsList
                variant="line"
                className="h-auto w-full justify-start gap-6 overflow-x-auto overflow-y-hidden border-b p-0"
                aria-label="Document status"
              >
                {SOURCE_STATUS_FILTERS.map((filter) => (
                  <TabsTrigger key={filter.value} value={filter.value} className="flex-none gap-2 px-0 py-3">
                    {filter.label}
                    <span className="font-mono text-xs tabular-nums text-muted-foreground">
                      {sourceStatusCount(documents, filter.value)}
                    </span>
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>

            <section className="overflow-hidden rounded-lg border bg-card" aria-label="Documents">
              <div className="flex flex-col gap-3 border-b p-3 sm:flex-row sm:items-center sm:justify-between">
                <InputGroup className="max-w-md shadow-none">
                  <InputGroupAddon>
                    <Search aria-hidden="true" />
                  </InputGroupAddon>
                  <InputGroupInput
                    type="search"
                    value={search}
                    placeholder="Search documents"
                    aria-label="Search documents"
                    onChange={(event) => setSearch(event.target.value)}
                  />
                </InputGroup>
                <div className="flex items-center justify-between gap-3 sm:justify-end">
                  <span className="text-xs tabular-nums text-muted-foreground">
                    {filteredDocuments.length} of {documents.length}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={sources.isFetching}
                    onClick={() => sources.refetch()}
                  >
                    <RefreshCw className={sources.isFetching ? "animate-spin" : ""} aria-hidden="true" />
                    Refresh
                  </Button>
                </div>
              </div>

              {sources.isPending ? <SourcesLoading /> : null}
              {sources.isError ? <SourcesError onRetry={() => sources.refetch()} /> : null}
              {sources.data?.length === 0 ? <SourcesEmpty /> : null}
              {sources.data && sources.data.length > 0 ? <SourcesTable sources={filteredDocuments} /> : null}
            </section>
          </TabsContent>
        </Tabs>
      </div>
    </main>
  )
}

function SourcesLoading() {
  return (
    <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-muted-foreground" role="status">
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
    <div className="grid min-h-72 place-items-center px-6 text-center">
      <div className="space-y-3">
        <span className="mx-auto grid size-11 place-items-center rounded-full border bg-muted/40">
          <Files className="size-5" aria-hidden="true" />
        </span>
        <div>
          <p className="font-medium">No documents yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Upload one clean document to start the knowledge index.</p>
        </div>
      </div>
    </div>
  )
}

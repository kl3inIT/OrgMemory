import { useQuery } from "@tanstack/react-query"
import { ChevronRight, Search } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { CollectionPagination } from "@/components/patterns/collection-pagination"
import { PageLayout } from "@/components/layouts/page-layout"
import { EmptyState } from "@/components/patterns/empty-state"
import { FilterBar } from "@/components/patterns/filter-bar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  ASSET_TYPE_META,
  ASSET_TYPES,
  type AssetType,
  formatAssetCoordinate,
} from "@/features/assets/asset-format"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import { AssetPageError, AssetPageLoading } from "@/features/assets/components/asset-state"
import { listAssetCatalogOptions } from "@/lib/hey-api/@tanstack/react-query.gen"

const ASSET_PAGE_SIZE = 24

export type AssetCatalogSort = "RECENTLY_RELEASED" | "NAME"

function assetActionLabel(type: AssetType) {
  switch (type) {
    case "PROMPT_TEMPLATE":
      return "Use prompt"
    case "WORK_INSTRUCTION":
      return "View instructions"
    case "CAPABILITY_PACK":
      return "View pack"
    case "SKILL":
      return "View skill"
  }
}

export function AssetCatalogPage({
  actorKey,
  query,
  type,
  sort,
  page,
  onQueryChange,
  onTypeChange,
  onSortChange,
  onPageChange,
}: {
  actorKey: string
  query: string
  type?: AssetType
  sort: AssetCatalogSort
  page: number
  onQueryChange: (query: string) => void
  onTypeChange: (type?: AssetType) => void
  onSortChange: (sort: AssetCatalogSort) => void
  onPageChange: (page: number) => void
}) {
  const catalogOptions = listAssetCatalogOptions({
    query: {
      q: query || undefined,
      type,
      sort,
      page,
      pageSize: ASSET_PAGE_SIZE,
    },
  })
  const assets = useQuery({
    ...catalogOptions,
    queryKey: scopeAssetQueryKey(catalogOptions.queryKey, actorKey),
  })

  if (assets.isPending) return <AssetPageLoading />
  if (assets.isError) return <AssetPageError onRetry={() => void assets.refetch()} />

  const recommendations = assets.data?.items ?? []
  const total = assets.data?.total ?? 0
  const hasFilters = query.length > 0 || type !== undefined

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header title="Assets">
        <FilterBar
          search={
            <InputGroup>
              <InputGroupAddon>
                <Search aria-hidden="true" />
              </InputGroupAddon>
              <InputGroupInput
                value={query}
                onChange={(event) => onQueryChange(event.currentTarget.value)}
                placeholder="Search by task, role, or outcome"
                aria-label="Search visible assets"
              />
            </InputGroup>
          }
          filters={
            <>
              <Select
                value={type ?? "ALL"}
                onValueChange={(value: string) =>
                  onTypeChange(value === "ALL" ? undefined : (value as AssetType))
                }
              >
                <SelectTrigger aria-label="Filter asset type" className="w-full sm:w-48">
                  <SelectValue placeholder="All asset types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All asset types</SelectItem>
                  {ASSET_TYPES.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={sort}
                onValueChange={(value: string) => onSortChange(value as AssetCatalogSort)}
              >
                <SelectTrigger aria-label="Sort assets" className="w-full sm:w-48">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RECENTLY_RELEASED">Recently released</SelectItem>
                  <SelectItem value="NAME">Name</SelectItem>
                </SelectContent>
              </Select>
            </>
          }
          result={`${total} ${total === 1 ? "asset" : "assets"}`}
        />
      </PageLayout.Header>

      <PageLayout.Body>
        {recommendations.length === 0 ? (
          <Card className="border-dashed bg-surface-subtle">
            <EmptyState
              title={hasFilters ? "No matches" : "No assets available"}
              action={
                hasFilters ? (
                  <Button
                    variant="outline"
                    onClick={() => {
                      onQueryChange("")
                      onTypeChange(undefined)
                    }}
                  >
                    Clear filters
                  </Button>
                ) : undefined
              }
            />
          </Card>
        ) : (
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Visible assets">
            {recommendations.map((asset) => {
              if (!asset.assetId || !asset.releaseId || !asset.type) return null
              const meta = ASSET_TYPE_META[asset.type]
              const Icon = meta.icon
              return (
                <Card
                  key={`${asset.assetId}:${asset.releaseId}`}
                  className="group overflow-hidden border-border-default bg-surface-raised transition-[border-color,transform,box-shadow] hover:-translate-y-0.5 hover:border-border-strong hover:shadow-md"
                >
                  <CardHeader className="gap-4">
                    <div className="flex items-start justify-between gap-3">
                      <span className={`grid size-10 place-items-center rounded-xl ${meta.tone}`}>
                        <Icon className="size-5" aria-hidden="true" />
                      </span>
                      <Badge variant="outline" className="font-mono text-metadata">
                        {asset.versionLabel}
                      </Badge>
                    </div>
                    <div>
                      <p className="text-metadata font-mono text-content-muted">
                        {formatAssetCoordinate(asset)}
                      </p>
                      <h2 className="mt-2 text-section-title text-content-primary">{asset.title}</h2>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <p className="line-clamp-3 text-body text-content-secondary">{asset.summary}</p>
                    <div className="mt-5 flex flex-wrap gap-2">
                      <Badge className={meta.tone}>{meta.label}</Badge>
                      {asset.availability === "DEPRECATED" ? (
                        <Badge className="bg-status-warning-surface text-status-warning-content">
                          Update available
                        </Badge>
                      ) : null}
                    </div>
                  </CardContent>
                  <CardFooter className="border-t border-border-subtle bg-surface-subtle/50 p-0">
                    <Link
                      to="/assets/$assetId"
                      params={{ assetId: asset.assetId }}
                      search={{ release: asset.releaseId }}
                      className="flex w-full items-center justify-between px-6 py-4 text-label text-content-primary outline-none transition-colors hover:bg-action-ghost-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
                    >
                      {assetActionLabel(asset.type)}
                      <ChevronRight className="size-4 transition-transform group-hover:translate-x-0.5" />
                    </Link>
                  </CardFooter>
                </Card>
              )
            })}
          </section>
        )}
        <CollectionPagination
          page={page}
          pageSize={ASSET_PAGE_SIZE}
          total={total}
          onPageChange={onPageChange}
        />
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

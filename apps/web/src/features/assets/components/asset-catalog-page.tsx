import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import {
  ArrowUpRight,
  Boxes,
  ChevronRight,
  LayoutGrid,
  List,
  Plus,
  Search,
} from "lucide-react"
import { useMemo, type ReactNode } from "react"

import { PageLayout } from "@/components/layouts/page-layout"
import { CollectionPagination } from "@/components/patterns/collection-pagination"
import { DataTable, type ColumnDef } from "@/components/patterns/data-table"
import { EmptyState } from "@/components/patterns/empty-state"
import { FilterBar } from "@/components/patterns/filter-bar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ButtonGroup } from "@/components/ui/button-group"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import type {
  AssetCatalogScope,
  AssetCatalogSort,
  AssetCatalogView,
} from "@/features/assets/asset-catalog-state"
import {
  ASSET_TYPE_META,
  type AssetType,
  formatAssetCoordinate,
} from "@/features/assets/asset-format"
import { AssetPageError, AssetPageLoading } from "@/features/assets/components/asset-state"
import { AssetTypeFilter } from "@/features/assets/components/asset-type-filter"
import type { AssetRecommendation, AssetSummary } from "@/lib/hey-api"
import {
  listAssetCatalogOptions,
  listOwnedAssetsOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"

const ASSET_PAGE_SIZE = 24

type CatalogAsset = AssetRecommendation & {
  assetId: string
  releaseId: string
  type: AssetType
}

type OwnedAsset = AssetSummary & {
  id: string
  type: AssetType
  namespace: string
  slug: string
  title: string
  summary: string
  portfolioState: "DRAFT_ONLY" | "ACTIVE" | "SUNSETTING" | "RETIRED"
}

function isCatalogAsset(asset: AssetRecommendation): asset is CatalogAsset {
  return Boolean(asset.assetId && asset.releaseId && asset.type)
}

function isOwnedAsset(asset: AssetSummary): asset is OwnedAsset {
  return Boolean(
    asset.id &&
      asset.type &&
      asset.namespace &&
      asset.slug &&
      asset.title &&
      asset.summary &&
      asset.portfolioState,
  )
}

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

function portfolioLabel(state: OwnedAsset["portfolioState"]) {
  switch (state) {
    case "DRAFT_ONLY":
      return "Draft"
    case "ACTIVE":
      return "Active"
    case "SUNSETTING":
      return "Sunsetting"
    case "RETIRED":
      return "Retired"
  }
}

function formatDate(value?: string) {
  if (!value) return "—"
  const date = new Date(value)
  if (Number.isNaN(date.valueOf())) return "—"
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(date)
}

function AssetLink({
  asset,
  children,
  className,
}: {
  asset: CatalogAsset
  children: ReactNode
  className?: string
}) {
  return (
    <Link
      to="/assets/$assetId"
      params={{ assetId: asset.assetId }}
      search={{ release: asset.releaseId }}
      className={className}
    >
      {children}
    </Link>
  )
}

function OwnedAssetLink({
  asset,
  children,
  className,
}: {
  asset: OwnedAsset
  children: ReactNode
  className?: string
}) {
  return (
    <Link
      to="/assets/$assetId/governance"
      params={{ assetId: asset.id }}
      className={className}
    >
      {children}
    </Link>
  )
}

function AssetGrid({ assets }: { assets: CatalogAsset[] }) {
  return (
    <section
      className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
      aria-label="Visible assets"
    >
      {assets.map((asset) => {
        const meta = ASSET_TYPE_META[asset.type]
        const Icon = meta.icon
        return (
          <Card
            key={`${asset.assetId}:${asset.releaseId}`}
            className="group min-h-64 gap-0 overflow-hidden border-border-default bg-surface-raised py-0 transition-[border-color,transform,box-shadow] hover:-translate-y-0.5 hover:border-border-strong hover:shadow-md"
          >
            <CardHeader className="gap-4 px-5 pt-5 pb-3">
              <div className="flex items-start justify-between gap-3">
                <span className={`grid size-10 place-items-center rounded-xl ${meta.tone}`}>
                  <Icon className="size-5" strokeWidth={1.9} aria-hidden="true" />
                </span>
                <Badge variant="outline" className="font-mono text-metadata">
                  {asset.versionLabel}
                </Badge>
              </div>
              <div>
                <p className="truncate text-metadata font-mono text-content-muted">
                  {formatAssetCoordinate(asset)}
                </p>
                <AssetLink
                  asset={asset}
                  className="mt-2 block rounded-sm outline-none hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  <h2 className="line-clamp-2 text-section-title text-content-primary">
                    {asset.title}
                  </h2>
                </AssetLink>
              </div>
            </CardHeader>
            <CardContent className="px-5 pb-4">
              <p className="line-clamp-2 text-sm text-content-secondary">{asset.summary}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                <Badge className={meta.tone}>{meta.label}</Badge>
                {asset.availability === "DEPRECATED" ? (
                  <Badge className="bg-status-warning-surface text-status-warning-content">
                    Update available
                  </Badge>
                ) : null}
              </div>
            </CardContent>
            <CardFooter className="mt-auto px-5 pt-0 pb-5">
              <AssetLink
                asset={asset}
                className="flex w-full items-center justify-between rounded-md text-label text-content-primary outline-none transition-colors hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                {assetActionLabel(asset.type)}
                <ChevronRight
                  className="size-4 transition-transform group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </AssetLink>
            </CardFooter>
          </Card>
        )
      })}
    </section>
  )
}

function OwnedAssetGrid({ assets }: { assets: OwnedAsset[] }) {
  return (
    <section
      className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
      aria-label="Visible owned assets"
    >
      {assets.map((asset) => {
        const meta = ASSET_TYPE_META[asset.type]
        const Icon = meta.icon
        return (
          <Card
            key={asset.id}
            className="group min-h-64 gap-0 overflow-hidden border-border-default bg-surface-raised py-0 transition-[border-color,transform,box-shadow] hover:-translate-y-0.5 hover:border-border-strong hover:shadow-md"
          >
            <CardHeader className="gap-4 px-5 pt-5 pb-3">
              <div className="flex items-start justify-between gap-3">
                <span className={`grid size-10 place-items-center rounded-xl ${meta.tone}`}>
                  <Icon className="size-5" strokeWidth={1.9} aria-hidden="true" />
                </span>
                <Badge variant="outline">{portfolioLabel(asset.portfolioState)}</Badge>
              </div>
              <div>
                <p className="truncate text-metadata font-mono text-content-muted">
                  {asset.namespace}/{asset.slug}
                </p>
                <OwnedAssetLink
                  asset={asset}
                  className="mt-2 block rounded-sm outline-none hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  <h2 className="line-clamp-2 text-section-title text-content-primary">
                    {asset.title}
                  </h2>
                </OwnedAssetLink>
              </div>
            </CardHeader>
            <CardContent className="px-5 pb-4">
              <p className="line-clamp-2 text-sm text-content-secondary">{asset.summary}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                <Badge className={meta.tone}>{meta.label}</Badge>
              </div>
            </CardContent>
            <CardFooter className="mt-auto px-5 pt-0 pb-5">
              <OwnedAssetLink
                asset={asset}
                className="flex w-full items-center justify-between rounded-md text-label text-content-primary outline-none transition-colors hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                Manage asset
                <ChevronRight
                  className="size-4 transition-transform group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </OwnedAssetLink>
            </CardFooter>
          </Card>
        )
      })}
    </section>
  )
}

export function AssetCatalogPage({
  actorKey,
  query,
  type,
  scope,
  sort,
  view,
  page,
  onQueryChange,
  onTypeChange,
  onScopeChange,
  onSortChange,
  onViewChange,
  onPageChange,
}: {
  actorKey: string
  query: string
  type?: AssetType
  scope: AssetCatalogScope
  sort: AssetCatalogSort
  view: AssetCatalogView
  page: number
  onQueryChange: (query: string) => void
  onTypeChange: (type?: AssetType) => void
  onScopeChange: (scope: AssetCatalogScope) => void
  onSortChange: (sort: AssetCatalogSort) => void
  onViewChange: (view: AssetCatalogView) => void
  onPageChange: (page: number) => void
}) {
  const catalogOptions = listAssetCatalogOptions({
    query: {
      q: query || undefined,
      type,
      sort: sort === "NAME" ? "NAME" : "RECENTLY_RELEASED",
      page,
      pageSize: ASSET_PAGE_SIZE,
    },
  })
  const catalog = useQuery({
    ...catalogOptions,
    queryKey: scopeAssetQueryKey(catalogOptions.queryKey, actorKey),
    placeholderData: keepPreviousData,
    enabled: scope === "ALL",
  })
  const ownedOptions = listOwnedAssetsOptions({
    query: {
      q: query || undefined,
      type,
      sort: sort === "NAME" ? "NAME" : "RECENTLY_UPDATED",
      page,
      pageSize: ASSET_PAGE_SIZE,
    },
  })
  const owned = useQuery({
    ...ownedOptions,
    queryKey: scopeAssetQueryKey(ownedOptions.queryKey, actorKey),
    placeholderData: keepPreviousData,
    enabled: scope === "MINE",
  })

  const catalogColumns = useMemo<ColumnDef<CatalogAsset, unknown>[]>(
    () => [
      {
        id: "asset",
        header: "Asset",
        enableSorting: false,
        meta: {
          headerClassName: "min-w-80 px-5",
          cellClassName: "whitespace-normal px-5 py-4",
        },
        cell: ({ row }) => {
          const asset = row.original
          const meta = ASSET_TYPE_META[asset.type]
          const Icon = meta.icon
          return (
            <div className="flex min-w-0 items-start gap-3">
              <span className={`grid size-9 shrink-0 place-items-center rounded-lg ${meta.tone}`}>
                <Icon className="size-[18px]" strokeWidth={1.9} aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <AssetLink
                  asset={asset}
                  className="font-semibold text-content-primary outline-none hover:text-action-link-hover focus-visible:rounded-sm focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  {asset.title}
                </AssetLink>
                <p className="mt-0.5 truncate font-mono text-metadata text-content-muted">
                  {formatAssetCoordinate(asset)}
                </p>
                <p className="mt-1 line-clamp-1 max-w-2xl text-sm text-content-secondary">
                  {asset.summary}
                </p>
              </div>
            </div>
          )
        },
      },
      {
        id: "type",
        header: "Type",
        enableSorting: false,
        meta: {
          headerClassName: "hidden lg:table-cell",
          cellClassName: "hidden lg:table-cell",
        },
        cell: ({ row }) => {
          const meta = ASSET_TYPE_META[row.original.type]
          return <Badge className={meta.tone}>{meta.label}</Badge>
        },
      },
      {
        accessorKey: "versionLabel",
        header: "Version",
        enableSorting: false,
        meta: {
          headerClassName: "hidden md:table-cell",
          cellClassName: "hidden md:table-cell",
        },
        cell: ({ row }) => (
          <span className="font-mono text-metadata text-content-primary">
            {row.original.versionLabel}
          </span>
        ),
      },
      {
        accessorKey: "releasedAt",
        header: "Released",
        enableSorting: false,
        meta: {
          headerClassName: "hidden xl:table-cell",
          cellClassName: "hidden xl:table-cell text-content-secondary",
        },
        cell: ({ row }) => formatDate(row.original.releasedAt),
      },
      {
        id: "action",
        header: () => <span className="sr-only">Action</span>,
        enableSorting: false,
        meta: {
          headerClassName: "w-36 px-5 text-right",
          cellClassName: "px-5 text-right",
        },
        cell: ({ row }) => (
          <AssetLink
            asset={row.original}
            className="inline-flex items-center gap-2 rounded-md font-medium text-content-primary outline-none hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            {assetActionLabel(row.original.type)}
            <ArrowUpRight className="size-4" aria-hidden="true" />
          </AssetLink>
        ),
      },
    ],
    [],
  )

  const ownedColumns = useMemo<ColumnDef<OwnedAsset, unknown>[]>(
    () => [
      {
        id: "asset",
        header: "Asset",
        enableSorting: false,
        meta: {
          headerClassName: "min-w-80 px-5",
          cellClassName: "whitespace-normal px-5 py-4",
        },
        cell: ({ row }) => {
          const asset = row.original
          const meta = ASSET_TYPE_META[asset.type]
          const Icon = meta.icon
          return (
            <div className="flex min-w-0 items-start gap-3">
              <span className={`grid size-9 shrink-0 place-items-center rounded-lg ${meta.tone}`}>
                <Icon className="size-[18px]" strokeWidth={1.9} aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <OwnedAssetLink
                  asset={asset}
                  className="font-semibold text-content-primary outline-none hover:text-action-link-hover focus-visible:rounded-sm focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  {asset.title}
                </OwnedAssetLink>
                <p className="mt-0.5 truncate font-mono text-metadata text-content-muted">
                  {asset.namespace}/{asset.slug}
                </p>
                <p className="mt-1 line-clamp-1 max-w-2xl text-sm text-content-secondary">
                  {asset.summary}
                </p>
              </div>
            </div>
          )
        },
      },
      {
        id: "type",
        header: "Type",
        enableSorting: false,
        meta: {
          headerClassName: "hidden lg:table-cell",
          cellClassName: "hidden lg:table-cell",
        },
        cell: ({ row }) => {
          const meta = ASSET_TYPE_META[row.original.type]
          return <Badge className={meta.tone}>{meta.label}</Badge>
        },
      },
      {
        accessorKey: "portfolioState",
        header: "Status",
        enableSorting: false,
        meta: {
          headerClassName: "hidden md:table-cell",
          cellClassName: "hidden md:table-cell",
        },
        cell: ({ row }) => <Badge variant="outline">{portfolioLabel(row.original.portfolioState)}</Badge>,
      },
      {
        accessorKey: "updatedAt",
        header: "Updated",
        enableSorting: false,
        meta: {
          headerClassName: "hidden xl:table-cell",
          cellClassName: "hidden xl:table-cell text-content-secondary",
        },
        cell: ({ row }) => formatDate(row.original.updatedAt),
      },
      {
        id: "action",
        header: () => <span className="sr-only">Action</span>,
        enableSorting: false,
        meta: {
          headerClassName: "w-36 px-5 text-right",
          cellClassName: "px-5 text-right",
        },
        cell: ({ row }) => (
          <OwnedAssetLink
            asset={row.original}
            className="inline-flex items-center gap-2 rounded-md font-medium text-content-primary outline-none hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            Manage asset
            <ArrowUpRight className="size-4" aria-hidden="true" />
          </OwnedAssetLink>
        ),
      },
    ],
    [],
  )

  const activeQuery = scope === "ALL" ? catalog : owned
  if (activeQuery.isPending) return <AssetPageLoading />
  if (activeQuery.isError) {
    return <AssetPageError onRetry={() => void activeQuery.refetch()} />
  }

  const recommendations = (catalog.data?.items ?? []).filter(isCatalogAsset)
  const ownedAssets = (owned.data?.items ?? []).filter(isOwnedAsset)
  const visibleAssets = scope === "ALL" ? recommendations : ownedAssets
  const total = scope === "ALL" ? (catalog.data?.total ?? 0) : (owned.data?.total ?? 0)
  const hasFilters = query.length > 0 || type !== undefined
  const hasUnrenderablePage = total > 0 && visibleAssets.length === 0
  const selectedSort =
    sort === "NAME"
      ? "NAME"
      : scope === "ALL"
        ? "RECENTLY_RELEASED"
        : "RECENTLY_UPDATED"

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        icon={<Boxes className="size-7" strokeWidth={1.7} aria-hidden="true" />}
        title="Assets"
        description="Discover reusable capabilities and manage the Assets you own."
        actions={
          <Button asChild size="lg">
            <Link to="/assets/new">
              <Plus aria-hidden="true" />
              Add asset
            </Link>
          </Button>
        }
      >
        <div className="grid gap-2 pt-2 lg:grid-cols-[minmax(0,2fr)_minmax(20rem,1fr)]">
          <InputGroup className="h-11 bg-surface-raised">
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
          <Tabs
            value={scope}
            onValueChange={(value) => onScopeChange(value as AssetCatalogScope)}
            className="w-full gap-0"
          >
            <TabsList aria-label="Asset scope" className="h-11 w-full">
              <TabsTrigger value="ALL" className="text-sm sm:text-base">
                All Assets
              </TabsTrigger>
              <TabsTrigger value="MINE" className="text-sm sm:text-base">
                My Assets
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
        <FilterBar
          filters={
            <>
              <AssetTypeFilter value={type} onValueChange={onTypeChange} />
              <Select
                value={selectedSort}
                onValueChange={(value: string) => onSortChange(value as AssetCatalogSort)}
              >
                <SelectTrigger aria-label="Sort assets" className="w-full sm:w-52">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {scope === "ALL" ? (
                    <SelectItem value="RECENTLY_RELEASED">Recently released</SelectItem>
                  ) : (
                    <SelectItem value="RECENTLY_UPDATED">Recently updated</SelectItem>
                  )}
                  <SelectItem value="NAME">Name</SelectItem>
                </SelectContent>
              </Select>
            </>
          }
          result={
            <span>
              {total} {total === 1 ? "result" : "results"}
            </span>
          }
          actions={
            <ButtonGroup aria-label="Asset layout">
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="List view"
                aria-pressed={view === "LIST"}
                className="aria-pressed:bg-action-secondary aria-pressed:text-action-secondary-foreground"
                onClick={() => onViewChange("LIST")}
              >
                <List aria-hidden="true" />
              </Button>
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="Grid view"
                aria-pressed={view === "GRID"}
                className="aria-pressed:bg-action-secondary aria-pressed:text-action-secondary-foreground"
                onClick={() => onViewChange("GRID")}
              >
                <LayoutGrid aria-hidden="true" />
              </Button>
            </ButtonGroup>
          }
        />
      </PageLayout.Header>

      <PageLayout.Body>
        {total === 0 ? (
          <Card className="border-dashed bg-surface-subtle">
            <EmptyState
              title={
                hasFilters
                  ? "No matches"
                  : scope === "MINE"
                    ? "You do not own any Assets yet"
                    : "No assets available"
              }
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
                ) : scope === "MINE" ? (
                  <Button asChild>
                    <Link to="/assets/new">Add your first asset</Link>
                  </Button>
                ) : undefined
              }
            />
          </Card>
        ) : hasUnrenderablePage ? (
          <Card className="border-dashed bg-surface-subtle">
            <EmptyState
              title="Assets could not be displayed"
              action={
                <Button variant="outline" onClick={() => void activeQuery.refetch()}>
                  Try again
                </Button>
              }
            />
          </Card>
        ) : view === "GRID" ? (
          scope === "ALL" ? (
            <AssetGrid assets={recommendations} />
          ) : (
            <OwnedAssetGrid assets={ownedAssets} />
          )
        ) : (
          <section
            className="overflow-hidden rounded-xl border border-border-default bg-surface-raised"
            aria-label={scope === "ALL" ? "Visible assets" : "Visible owned assets"}
          >
            {scope === "ALL" ? (
              <DataTable
                columns={catalogColumns}
                data={recommendations}
                getRowId={(asset) => `${asset.assetId}:${asset.releaseId}`}
                rowClassName="group"
              />
            ) : (
              <DataTable
                columns={ownedColumns}
                data={ownedAssets}
                getRowId={(asset) => asset.id}
                rowClassName="group"
              />
            )}
          </section>
        )}
        <CollectionPagination
          page={page}
          pageSize={ASSET_PAGE_SIZE}
          total={hasUnrenderablePage ? 0 : total}
          disabled={activeQuery.isPlaceholderData}
          showSummaryWhenSinglePage
          onPageChange={onPageChange}
        />
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

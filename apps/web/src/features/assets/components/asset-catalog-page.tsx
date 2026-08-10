import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import {
  ArrowUpRight,
  ChevronRight,
  LayoutGrid,
  List,
  Search,
} from "lucide-react"
import type { ReactNode } from "react"

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
import { Switch } from "@/components/ui/switch"
import { toast } from "sonner"
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
import { AssetCreateMenu } from "@/features/assets/components/asset-create-menu"
import {
  AssetCatalogMark,
  AssetTypeMark,
} from "@/features/assets/components/asset-type-mark"
import { AssetTypeFilter } from "@/features/assets/components/asset-type-filter"
import type { AssetRecommendation, AssetSummary } from "@/lib/hey-api"
import {
  listAssetCatalogOptions,
  listOwnedAssetsOptions,
  getSkillActivationOptions,
  getSkillActivationQueryKey,
  setSkillActivationMutation,
  contextOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import { avatarInitials } from "@/lib/avatar"
import { formatDate } from "@/lib/format"

const ASSET_PAGE_SIZE = 24
const OWNED_PORTFOLIO_STATES = [
  "DRAFT_ONLY",
  "ACTIVE",
  "SUNSETTING",
  "RETIRED",
] as const

type OwnedPortfolioState = (typeof OWNED_PORTFOLIO_STATES)[number]

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
  portfolioState: OwnedPortfolioState
}

function isCatalogAsset(asset: AssetRecommendation): asset is CatalogAsset {
  return Boolean(asset.assetId && asset.releaseId && asset.type)
}

function isOwnedPortfolioState(
  state: AssetSummary["portfolioState"],
): state is OwnedPortfolioState {
  return OWNED_PORTFOLIO_STATES.some((candidate) => candidate === state)
}

function isOwnedAsset(asset: AssetSummary): asset is OwnedAsset {
  return (
    Boolean(
      asset.id &&
        asset.type &&
        asset.namespace &&
        asset.slug &&
        asset.title,
    ) &&
    asset.summary != null &&
    isOwnedPortfolioState(asset.portfolioState)
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
    default:
      return "Unknown"
  }
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
      preload={false}
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
      preload={false}
      className={className}
    >
      {children}
    </Link>
  )
}

type AssetItemViewModel = {
  key: string
  type: AssetType
  title: string
  summary: string
  coordinate: string
  topBadge: string
  topBadgeClassName?: string
  secondaryBadge?: string
  detail: string
  timestamp?: string
  actionLabel: string
  sharingLabel: string
  ownerLabel?: string
  skillAssetId?: string
  link: (children: ReactNode, className: string) => ReactNode
}

function catalogItem(
  asset: CatalogAsset,
  ownerName?: string,
  ownerPending = false,
): AssetItemViewModel {
  return {
    key: `${asset.assetId}:${asset.releaseId}`,
    type: asset.type,
    title: asset.title ?? "Untitled Asset",
    summary: asset.summary ?? "",
    coordinate: formatAssetCoordinate(asset),
    topBadge: asset.versionLabel ?? "—",
    topBadgeClassName: "font-mono text-metadata",
    secondaryBadge: asset.availability === "DEPRECATED" ? "Update available" : undefined,
    detail: asset.versionLabel ?? "—",
    timestamp: asset.releasedAt,
    actionLabel: assetActionLabel(asset.type),
    sharingLabel:
      asset.sharingState === "ORGANIZATION"
        ? "Company"
        : asset.sharingState === "SHARED"
          ? "Shared"
          : "Private",
    ownerLabel: ownerPending ? "Loading owner" : (ownerName ?? "Owner unavailable"),
    skillAssetId: asset.type === "SKILL" ? asset.assetId : undefined,
    link: (children, className) => (
      <AssetLink asset={asset} className={className}>
        {children}
      </AssetLink>
    ),
  }
}

function ownedItem(asset: OwnedAsset): AssetItemViewModel {
  return {
    key: asset.id,
    type: asset.type,
    title: asset.title,
    summary: asset.summary,
    coordinate: `${asset.namespace}/${asset.slug}`,
    topBadge: portfolioLabel(asset.portfolioState),
    detail: portfolioLabel(asset.portfolioState),
    timestamp: asset.updatedAt,
    actionLabel: "Manage asset",
    sharingLabel:
      asset.sharingState === "ORGANIZATION"
        ? "Company"
        : asset.sharingState === "SHARED"
          ? "Shared"
          : "Private",
    ownerLabel: "Created by you",
    link: (children, className) => (
      <OwnedAssetLink asset={asset} className={className}>
        {children}
      </OwnedAssetLink>
    ),
  }
}

function AssetCard({ asset }: { asset: AssetItemViewModel }) {
  const meta = ASSET_TYPE_META[asset.type]
  return (
    <Card
      className="group relative min-h-68 gap-0 overflow-hidden border-border-default bg-surface-raised py-0 shadow-none transition-[border-color,transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-action-primary/55 hover:shadow-[0_14px_34px_-24px_var(--color-action-primary)]"
    >
      {asset.link(
        <span className="sr-only">{asset.title}</span>,
        "absolute inset-0 z-10 rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring",
      )}
      <CardHeader className="gap-5 px-5 pt-5 pb-3">
        <div className="flex items-start justify-between gap-3">
          <span
            className={`grid size-11 place-items-center rounded-[0.7rem] border border-current/10 ${meta.tone}`}
          >
            <AssetTypeMark type={asset.type} className="size-6" aria-hidden="true" />
          </span>
          <div className="flex items-center gap-2">
            {asset.secondaryBadge ? (
              <span className="text-metadata text-status-warning-content">
                {asset.secondaryBadge}
              </span>
            ) : null}
            <Badge variant="outline" className={asset.topBadgeClassName}>
              {asset.topBadge}
            </Badge>
          </div>
        </div>
        <div className="flex min-w-0 items-start justify-between gap-3">
          <h2 className="line-clamp-2 text-section-title tracking-[-0.015em] text-content-primary transition-colors group-hover:text-action-link-hover">
            {asset.title}
          </h2>
          <ChevronRight
            className="mt-0.5 size-4 shrink-0 text-content-muted transition-[color,transform] group-hover:translate-x-0.5 group-hover:text-action-link-hover"
            aria-hidden="true"
          />
        </div>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col px-5 pb-5">
        <p className="line-clamp-2 min-h-10 text-sm leading-5 text-content-secondary">
          {asset.summary || "No description provided."}
        </p>
        <div className="mt-auto flex min-w-0 items-center gap-2.5 pt-5">
          <span className="grid size-7 shrink-0 place-items-center rounded-md border border-border-subtle bg-surface-subtle text-metadata font-semibold text-content-secondary">
            {avatarInitials(asset.ownerLabel)}
          </span>
          <span className="min-w-0 truncate text-metadata font-medium text-content-secondary">
            {asset.ownerLabel}
          </span>
          <span className="h-3.5 w-px shrink-0 bg-border-subtle" aria-hidden="true" />
          <Badge variant="outline" className="shrink-0 bg-surface-subtle font-normal">
            {asset.sharingLabel}
          </Badge>
        </div>
      </CardContent>
      <CardFooter className="pointer-events-none mt-auto min-h-14 justify-between border-t border-border-subtle bg-surface-subtle/45 px-5 py-3">
        <span className="text-metadata font-medium text-content-muted">{meta.label}</span>
        {asset.skillAssetId ? (
          <div className="pointer-events-auto relative z-20">
            <SkillActivationToggle assetId={asset.skillAssetId} />
          </div>
        ) : null}
      </CardFooter>
    </Card>
  )
}

function SkillActivationToggle({ assetId }: { assetId: string }) {
  const queryClient = useQueryClient()
  const options = getSkillActivationOptions({ path: { assetId } })
  const activation = useQuery(options)
  const update = useMutation({
    ...setSkillActivationMutation(),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({
        queryKey: getSkillActivationQueryKey({ path: { assetId } }),
      })
      toast.success(result.enabled ? "Skill enabled" : "Skill disabled")
    },
    onError: () => toast.error("Skill activation could not be updated"),
  })
  return (
    <div className="flex items-center gap-2.5">
      <span className="text-metadata font-medium text-content-secondary">Use in Assistant</span>
      <Switch
        aria-label="Use this Skill in Assistant"
        size="sm"
        checked={Boolean(activation.data?.enabled)}
        disabled={activation.isPending || activation.isError || update.isPending}
        onCheckedChange={(enabled) =>
          update.mutate({ body: { enabled }, path: { assetId } })
        }
      />
    </div>
  )
}

function AssetGrid({
  assets,
  label,
}: {
  assets: AssetItemViewModel[]
  label: string
}) {
  return (
    <section
      className="grid gap-5 md:grid-cols-2 xl:grid-cols-3"
      aria-label={label}
    >
      {assets.map((asset) => <AssetCard key={asset.key} asset={asset} />)}
    </section>
  )
}

function createAssetColumns(
  detailHeader: string,
  timestampHeader: string,
): ColumnDef<AssetItemViewModel, unknown>[] {
  return [
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
              {asset.link(
                asset.title,
                "font-semibold text-content-primary outline-none hover:text-action-link-hover focus-visible:rounded-sm focus-visible:ring-2 focus-visible:ring-focus-ring",
              )}
              <p className="mt-0.5 truncate font-mono text-metadata text-content-muted">
                {asset.coordinate}
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
      id: "detail",
      header: detailHeader,
      enableSorting: false,
      meta: {
        headerClassName: "hidden md:table-cell",
        cellClassName: "hidden md:table-cell",
      },
      cell: ({ row }) =>
        detailHeader === "Status" ? (
          <Badge variant="outline">{row.original.detail}</Badge>
        ) : (
          <span className="font-mono text-metadata text-content-primary">
            {row.original.detail}
          </span>
        ),
    },
    {
      id: "timestamp",
      header: timestampHeader,
      enableSorting: false,
      meta: {
        headerClassName: "hidden xl:table-cell",
        cellClassName: "hidden xl:table-cell text-content-secondary",
      },
      cell: ({ row }) => formatDate(row.original.timestamp, { dateOnly: true }),
    },
    {
      id: "action",
      header: () => <span className="sr-only">Action</span>,
      enableSorting: false,
      meta: {
        headerClassName: "w-36 px-5 text-right",
        cellClassName: "px-5 text-right",
      },
      cell: ({ row }) =>
        row.original.link(
          <>
            {row.original.actionLabel}
            <ArrowUpRight className="size-4" aria-hidden="true" />
          </>,
          "inline-flex items-center gap-2 rounded-md font-medium text-content-primary outline-none hover:text-action-link-hover focus-visible:ring-2 focus-visible:ring-focus-ring",
        ),
    },
  ]
}

const CATALOG_COLUMNS = createAssetColumns("Version", "Released")
const OWNED_COLUMNS = createAssetColumns("Status", "Updated")

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
  const directoryOptions = contextOptions()
  const directory = useQuery({
    ...directoryOptions,
    queryKey: scopeAssetQueryKey(directoryOptions.queryKey, actorKey),
    enabled: scope === "ALL",
  })

  const activeQuery = scope === "ALL" ? catalog : owned
  if (activeQuery.isPending) return <AssetPageLoading />
  if (activeQuery.isError) {
    return <AssetPageError onRetry={() => void activeQuery.refetch()} />
  }

  const recommendations = (catalog.data?.items ?? []).filter(isCatalogAsset)
  const ownedAssets = (owned.data?.items ?? []).filter(isOwnedAsset)
  const visibleAssets = scope === "ALL" ? recommendations : ownedAssets
  const ownerNames = new Map(
    (directory.data?.users ?? []).flatMap((user) =>
      user.id && user.name ? [[user.id, user.name] as const] : [],
    ),
  )
  const assetItems =
    scope === "ALL"
      ? recommendations.map((asset) =>
          catalogItem(
            asset,
            asset.ownerUserId ? ownerNames.get(asset.ownerUserId) : undefined,
            directory.isPending,
          ),
        )
      : ownedAssets.map(ownedItem)
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
        icon={<AssetCatalogMark className="text-action-primary" aria-hidden="true" />}
        title="Asset catalog"
        description="Reusable capabilities shared across your company."
        actions={<AssetCreateMenu />}
      >
        <div className="grid gap-2 pt-2 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <InputGroup className="h-11 bg-surface-raised">
            <InputGroupAddon>
              <Search aria-hidden="true" />
            </InputGroupAddon>
            <InputGroupInput
              value={query}
              onChange={(event) => onQueryChange(event.currentTarget.value)}
              placeholder="Search assets"
              aria-label="Search visible assets"
            />
          </InputGroup>
          <Tabs
            value={scope}
            onValueChange={(value) => onScopeChange(value as AssetCatalogScope)}
            className="w-full gap-0"
          >
            <TabsList aria-label="Asset scope" className="h-11 w-full">
              <TabsTrigger
                value="ALL"
                className="text-sm data-[state=active]:border-border-strong data-[state=active]:bg-action-secondary-hover sm:text-base"
              >
                Available to me
              </TabsTrigger>
              <TabsTrigger
                value="MINE"
                className="text-sm data-[state=active]:border-border-strong data-[state=active]:bg-action-secondary-hover sm:text-base"
              >
                Created by me
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
                className="aria-pressed:border-border-strong aria-pressed:bg-action-secondary-hover aria-pressed:text-content-primary aria-pressed:shadow-inner"
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
                className="aria-pressed:border-border-strong aria-pressed:bg-action-secondary-hover aria-pressed:text-content-primary aria-pressed:shadow-inner"
                onClick={() => onViewChange("GRID")}
              >
                <LayoutGrid aria-hidden="true" />
              </Button>
            </ButtonGroup>
          }
        />
      </PageLayout.Header>

      <PageLayout.Body>
        {scope === "ALL" && directory.isError ? (
          <div
            role="status"
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border-subtle bg-surface-subtle px-4 py-2.5 text-metadata text-content-secondary"
          >
            <span>Owner names could not be loaded.</span>
            <Button variant="ghost" size="sm" onClick={() => void directory.refetch()}>
              Retry owner names
            </Button>
          </div>
        ) : null}
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
                    <Link to="/assets/new/skill">Add your first asset</Link>
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
          <AssetGrid
            assets={assetItems}
            label={scope === "ALL" ? "Visible assets" : "Visible owned assets"}
          />
        ) : (
          <section
            className="overflow-hidden rounded-xl border border-border-default bg-surface-raised"
            aria-label={scope === "ALL" ? "Visible assets" : "Visible owned assets"}
          >
            <DataTable
              columns={scope === "ALL" ? CATALOG_COLUMNS : OWNED_COLUMNS}
              data={assetItems}
              getRowId={(asset) => asset.key}
              rowClassName="group"
            />
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

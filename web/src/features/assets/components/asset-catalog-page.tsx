import { useQuery } from "@tanstack/react-query"
import { ArrowUpRight, Search } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  ASSET_TYPE_META,
  ASSET_TYPES,
  type AssetType,
  formatAssetCoordinate,
} from "@/features/assets/asset-format"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import {
  AssetPageError,
  AssetPageLoading,
} from "@/features/assets/components/asset-state"
import { recommendAssistantAssetsOptions } from "@/lib/hey-api/@tanstack/react-query.gen"

export function AssetCatalogPage({
  actorKey,
  query,
  type,
  onQueryChange,
  onTypeChange,
}: {
  actorKey: string
  query: string
  type?: AssetType
  onQueryChange: (query: string) => void
  onTypeChange: (type?: AssetType) => void
}) {
  const recommendationOptions = recommendAssistantAssetsOptions({
    query: { q: query || undefined, type },
  })
  const assets = useQuery({
    ...recommendationOptions,
    queryKey: scopeAssetQueryKey(recommendationOptions.queryKey, actorKey),
  })

  if (assets.isPending) return <AssetPageLoading />
  if (assets.isError) return <AssetPageError onRetry={() => void assets.refetch()} />

  const recommendations = assets.data?.recommendations ?? []
  const hasFilters = query.length > 0 || type !== undefined

  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-7xl space-y-7 p-4 md:p-8">
        <header className="flex items-end justify-between gap-4 border-b border-border-subtle pb-7">
          <h1 className="text-page-title text-content-primary">For your role</h1>
          <Badge variant="outline" className="w-fit font-mono text-metadata">
            {recommendations.length} {recommendations.length === 1 ? "asset" : "assets"}
          </Badge>
        </header>

        <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_15rem]">
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
          <Select
            value={type ?? "ALL"}
            onValueChange={(value: string) =>
              onTypeChange(value === "ALL" ? undefined : (value as AssetType))
            }
          >
            <SelectTrigger aria-label="Filter asset type" className="w-full">
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
        </div>

        {recommendations.length === 0 ? (
          <Card className="border-dashed bg-surface-subtle">
            <CardContent className="flex flex-col items-center gap-4 p-10 text-center">
              <h2 className="text-section-title text-content-primary">
                {hasFilters ? "No matches" : "No assets available"}
              </h2>
              {hasFilters ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    onQueryChange("")
                    onTypeChange(undefined)
                  }}
                >
                  Clear filters
                </Button>
              ) : null}
            </CardContent>
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
                      <h2 className="mt-2 text-section-title text-content-primary">
                        {asset.title}
                      </h2>
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
                      Use exact release
                      <ArrowUpRight className="size-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                    </Link>
                  </CardFooter>
                </Card>
              )
            })}
          </section>
        )}
      </div>
    </div>
  )
}

import { createFileRoute } from "@tanstack/react-router"

import { assetActorKey } from "@/features/assets/actor-key"
import { parseAssetCatalogSearch } from "@/features/assets/asset-catalog-state"
import { AssetCatalogPage } from "@/features/assets/components/asset-catalog-page"

export const Route = createFileRoute("/_authenticated/assets/")({
  component: AssetCatalogRoute,
  staticData: { title: "Assets" },
  validateSearch: parseAssetCatalogSearch,
})

function AssetCatalogRoute() {
  const { q, type, sort, view, page } = Route.useSearch()
  const { session } = Route.useRouteContext()
  const navigate = Route.useNavigate()
  return (
    <AssetCatalogPage
      actorKey={assetActorKey(session)}
      query={q ?? ""}
      type={type}
      sort={sort ?? "RECENTLY_RELEASED"}
      view={view ?? "GRID"}
      page={page ?? 1}
      onQueryChange={(nextQuery) =>
        void navigate({
          replace: true,
          search: (previous) => ({
            ...previous,
            q: nextQuery || undefined,
            page: undefined,
          }),
        })
      }
      onTypeChange={(nextType) =>
        void navigate({
          replace: true,
          search: (previous) => ({ ...previous, type: nextType, page: undefined }),
        })
      }
      onSortChange={(nextSort) =>
        void navigate({
          replace: true,
          search: (previous) => ({
            ...previous,
            sort: nextSort === "RECENTLY_RELEASED" ? undefined : nextSort,
            page: undefined,
          }),
        })
      }
      onViewChange={(nextView) =>
        void navigate({
          replace: true,
          search: (previous) => ({
            ...previous,
            view: nextView === "GRID" ? undefined : nextView,
          }),
        })
      }
      onPageChange={(nextPage) =>
        void navigate({
          search: (previous) => ({
            ...previous,
            page: nextPage === 1 ? undefined : nextPage,
          }),
        })
      }
    />
  )
}

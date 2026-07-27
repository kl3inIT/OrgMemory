import { createFileRoute } from "@tanstack/react-router"

import { type AssetType } from "@/features/assets/asset-format"
import { assetActorKey } from "@/features/assets/actor-key"
import {
  AssetCatalogPage,
  type AssetCatalogSort,
} from "@/features/assets/components/asset-catalog-page"

const TYPES = new Set<AssetType>([
  "PROMPT_TEMPLATE",
  "WORK_INSTRUCTION",
  "CAPABILITY_PACK",
  "SKILL",
])

export const Route = createFileRoute("/_authenticated/assets/")({
  component: AssetCatalogRoute,
  staticData: { title: "Assets" },
  validateSearch: (
    search: Record<string, unknown>,
  ): { q?: string; type?: AssetType; sort?: AssetCatalogSort; page?: number } => {
    const q = typeof search.q === "string" ? search.q.trim().slice(0, 200) : ""
    const type =
      typeof search.type === "string" && TYPES.has(search.type as AssetType)
        ? (search.type as AssetType)
        : undefined
    const sort =
      search.sort === "NAME" || search.sort === "RECENTLY_RELEASED"
        ? search.sort
        : undefined
    const parsedPage =
      typeof search.page === "number"
        ? search.page
        : typeof search.page === "string"
          ? Number.parseInt(search.page, 10)
          : 1
    const page = Number.isSafeInteger(parsedPage) && parsedPage > 1 ? parsedPage : undefined
    return { q: q || undefined, type, sort, page }
  },
})

function AssetCatalogRoute() {
  const { q, type, sort, page } = Route.useSearch()
  const { session } = Route.useRouteContext()
  const navigate = Route.useNavigate()
  return (
    <AssetCatalogPage
      actorKey={assetActorKey(session)}
      query={q ?? ""}
      type={type}
      sort={sort ?? "RECENTLY_RELEASED"}
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

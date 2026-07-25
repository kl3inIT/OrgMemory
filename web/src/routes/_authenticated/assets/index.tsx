import { createFileRoute } from "@tanstack/react-router"

import { type AssetType } from "@/features/assets/asset-format"
import { assetActorKey } from "@/features/assets/actor-key"
import { AssetCatalogPage } from "@/features/assets/components/asset-catalog-page"

const TYPES = new Set<AssetType>([
  "PROMPT_TEMPLATE",
  "WORK_INSTRUCTION",
  "CAPABILITY_PACK",
])

export const Route = createFileRoute("/_authenticated/assets/")({
  component: AssetCatalogRoute,
  staticData: { title: "Assets" },
  validateSearch: (search: Record<string, unknown>): { q?: string; type?: AssetType } => {
    const q = typeof search.q === "string" ? search.q.trim().slice(0, 200) : ""
    const type =
      typeof search.type === "string" && TYPES.has(search.type as AssetType)
        ? (search.type as AssetType)
        : undefined
    return { q: q || undefined, type }
  },
})

function AssetCatalogRoute() {
  const { q, type } = Route.useSearch()
  const { session } = Route.useRouteContext()
  const navigate = Route.useNavigate()
  return (
    <AssetCatalogPage
      actorKey={assetActorKey(session)}
      query={q ?? ""}
      type={type}
      onQueryChange={(nextQuery) =>
        void navigate({
          replace: true,
          search: (previous) => ({ ...previous, q: nextQuery || undefined }),
        })
      }
      onTypeChange={(nextType) =>
        void navigate({
          replace: true,
          search: (previous) => ({ ...previous, type: nextType }),
        })
      }
    />
  )
}

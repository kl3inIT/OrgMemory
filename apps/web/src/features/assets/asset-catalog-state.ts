import type { AssetType } from "@/features/assets/asset-format"

export type AssetCatalogSort = "RECENTLY_RELEASED" | "NAME"
export type AssetCatalogView = "LIST" | "GRID"

const TYPES = {
  PROMPT_TEMPLATE: true,
  WORK_INSTRUCTION: true,
  CAPABILITY_PACK: true,
  SKILL: true,
} satisfies Record<AssetType, true>

export function parseAssetCatalogSearch(search: Record<string, unknown>): {
  q?: string
  type?: AssetType
  sort?: AssetCatalogSort
  view?: AssetCatalogView
  page?: number
} {
  const q = typeof search.q === "string" ? search.q.trim().slice(0, 200) : ""
  const type =
    typeof search.type === "string" &&
    Object.prototype.hasOwnProperty.call(TYPES, search.type)
      ? (search.type as AssetType)
      : undefined
  const sort =
    search.sort === "NAME" || search.sort === "RECENTLY_RELEASED"
      ? search.sort
      : undefined
  const view = search.view === "GRID" ? search.view : undefined
  const parsedPage =
    typeof search.page === "number"
      ? search.page
      : typeof search.page === "string"
        ? Number.parseInt(search.page, 10)
        : 1
  const page = Number.isSafeInteger(parsedPage) && parsedPage > 1 ? parsedPage : undefined

  return { q: q || undefined, type, sort, view, page }
}

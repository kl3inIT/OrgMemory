import { describe, expect, it } from "vitest"

import { parseAssetCatalogSearch } from "@/features/assets/asset-catalog-state"

describe("parseAssetCatalogSearch", () => {
  it("keeps list and recent releases as clean URL defaults", () => {
    expect(parseAssetCatalogSearch({ view: "LIST", sort: "INVALID", page: "1" })).toEqual({
      q: undefined,
      type: undefined,
      sort: undefined,
      view: undefined,
      page: undefined,
    })
  })

  it("preserves a valid grid catalog state and rejects unsafe values", () => {
    expect(
      parseAssetCatalogSearch({
        q: `  ${"a".repeat(210)}  `,
        type: "SKILL",
        sort: "NAME",
        view: "GRID",
        page: "3",
      }),
    ).toEqual({
      q: "a".repeat(200),
      type: "SKILL",
      sort: "NAME",
      view: "GRID",
      page: 3,
    })

    expect(parseAssetCatalogSearch({ type: "UNKNOWN", view: "TABLE", page: "-2" })).toEqual({
      q: undefined,
      type: undefined,
      sort: undefined,
      view: undefined,
      page: undefined,
    })
  })
})

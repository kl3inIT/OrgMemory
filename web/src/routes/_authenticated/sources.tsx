import { createFileRoute } from "@tanstack/react-router"

import { SourcesPage } from "@/features/sources/components/sources-page"

export const Route = createFileRoute("/_authenticated/sources")({
  component: SourcesRoute,
  staticData: { title: "Documents" },
  validateSearch: (search: Record<string, unknown>) => {
    const q = typeof search.q === "string" ? search.q.trim().slice(0, 200) : ""
    return { q: q || undefined }
  },
})

function SourcesRoute() {
  const { q } = Route.useSearch()
  const navigate = Route.useNavigate()

  return (
    <SourcesPage
      search={q ?? ""}
      onSearchChange={(nextQuery) => {
        void navigate({
          replace: true,
          search: (previous) => ({
            ...previous,
            q: nextQuery || undefined,
          }),
        })
      }}
    />
  )
}

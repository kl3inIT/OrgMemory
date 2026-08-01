import { createFileRoute } from "@tanstack/react-router"

import { assetActorKey } from "@/features/assets/actor-key"
import { AssetDetailPage } from "@/features/assets/components/asset-detail-page"

export const Route = createFileRoute("/_authenticated/assets/$assetId/")({
  component: AssetDetailRoute,
  staticData: { title: "Asset" },
  validateSearch: (search: Record<string, unknown>): { release?: string } => ({
    release:
      typeof search.release === "string" && /^[0-9a-f-]{36}$/i.test(search.release)
        ? search.release
        : undefined,
  }),
})

function AssetDetailRoute() {
  const { assetId } = Route.useParams()
  const { release } = Route.useSearch()
  const { session } = Route.useRouteContext()
  const navigate = Route.useNavigate()
  return (
    <AssetDetailPage
      assetId={assetId}
      actorKey={assetActorKey(session)}
      releaseId={release}
      onReleaseChange={(nextRelease) =>
        void navigate({
          replace: true,
          search: { release: nextRelease },
        })
      }
    />
  )
}

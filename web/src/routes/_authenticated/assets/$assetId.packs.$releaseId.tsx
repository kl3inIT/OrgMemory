import { createFileRoute } from "@tanstack/react-router"

import { assetActorKey } from "@/features/assets/actor-key"
import { PackJourneyPage } from "@/features/assets/components/pack-journey-page"

export const Route = createFileRoute("/_authenticated/assets/$assetId/packs/$releaseId")({
  component: PackJourneyRoute,
  staticData: { title: "Pack journey" },
})

function PackJourneyRoute() {
  const { assetId, releaseId } = Route.useParams()
  const { session } = Route.useRouteContext()
  return (
    <PackJourneyPage
      assetId={assetId}
      releaseId={releaseId}
      actorKey={assetActorKey(session)}
    />
  )
}

import { createFileRoute } from "@tanstack/react-router"

import { assetActorKey } from "@/features/assets/actor-key"
import { GovernanceWorkspacePage } from "@/features/assets/components/governance-workspace-page"

export const Route = createFileRoute("/_authenticated/assets/$assetId/governance")({
  component: GovernanceRoute,
  staticData: { title: "Asset governance" },
})

function GovernanceRoute() {
  const { assetId } = Route.useParams()
  const { session } = Route.useRouteContext()
  return (
    <GovernanceWorkspacePage
      assetId={assetId}
      actorKey={assetActorKey(session)}
    />
  )
}

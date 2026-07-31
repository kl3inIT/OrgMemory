import { createFileRoute } from "@tanstack/react-router"

import { assetActorKey } from "@/features/assets/actor-key"
import { SkillPackageReplacePage } from "@/features/assets/components/skill-package-replace-page"

export const Route = createFileRoute("/_authenticated/assets/$assetId/skill-package")({
  component: SkillPackageRoute,
  staticData: { title: "Replace Skill package" },
})

function SkillPackageRoute() {
  const { assetId } = Route.useParams()
  const { session } = Route.useRouteContext()
  return <SkillPackageReplacePage assetId={assetId} actorKey={assetActorKey(session)} />
}

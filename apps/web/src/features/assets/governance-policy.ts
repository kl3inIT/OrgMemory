import type {
  AssetGovernanceActions,
  AssetView,
} from "@/lib/hey-api"

export type GovernanceTab = "draft" | "changes" | "review" | "releases"

export function initialGovernanceTab(asset: AssetView): GovernanceTab {
  if (asset.reviews?.some((review) => review.state === "IN_REVIEW")) {
    return "review"
  }
  if ((asset.revisions?.length ?? 0) === 0 && asset.draft) {
    return "draft"
  }
  return "changes"
}

export function canPublishSkillDirectly(
  asset: AssetView,
  actions: AssetGovernanceActions | undefined,
): boolean {
  return Boolean(
    asset.type === "SKILL" &&
      actions?.canPublishSkill &&
      !asset.reviews?.some((review) => review.state === "IN_REVIEW"),
  )
}

export function canOpenGovernance(
  actions: AssetGovernanceActions | undefined,
): boolean {
  return Boolean(actions?.canOpenGovernance)
}

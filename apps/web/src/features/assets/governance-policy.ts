import type {
  AssetGovernanceActions,
  AssetView,
} from "@/lib/hey-api"

export type GovernanceTab = "draft" | "changes" | "review" | "releases"

export function initialGovernanceTab(asset: AssetView): GovernanceTab {
  return asset.draft ? "draft" : "releases"
}

export function canPublishDirectly(
  asset: AssetView,
  actions: AssetGovernanceActions | undefined,
): boolean {
  return Boolean(
    actions?.canPublishDirect &&
      !asset.reviews?.some((review) => review.state === "IN_REVIEW"),
  )
}

export function canOpenGovernance(
  actions: AssetGovernanceActions | undefined,
): boolean {
  return Boolean(actions?.canOpenGovernance)
}

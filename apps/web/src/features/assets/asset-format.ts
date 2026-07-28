import {
  Boxes,
  FileArchive,
  ListChecks,
  Sparkles,
  type LucideIcon,
} from "lucide-react"

import type { AssetSummary, AssetView, Release } from "@/lib/hey-api"

export type AssetType = NonNullable<AssetSummary["type"]>

export const ASSET_TYPES: Array<{ value: AssetType; label: string }> = [
  { value: "PROMPT_TEMPLATE", label: "Prompt template" },
  { value: "WORK_INSTRUCTION", label: "Work instruction" },
  { value: "CAPABILITY_PACK", label: "Capability pack" },
  { value: "SKILL", label: "Skill" },
]

export const ASSET_TYPE_META: Record<
  AssetType,
  { label: string; shortLabel: string; icon: LucideIcon; tone: string }
> = {
  PROMPT_TEMPLATE: {
    label: "Prompt template",
    shortLabel: "Prompt",
    icon: Sparkles,
    tone: "bg-status-info-surface text-status-info-content",
  },
  WORK_INSTRUCTION: {
    label: "Work instruction",
    shortLabel: "Instruction",
    icon: ListChecks,
    tone: "bg-status-warning-surface text-status-warning-content",
  },
  CAPABILITY_PACK: {
    label: "Capability pack",
    shortLabel: "Pack",
    icon: Boxes,
    tone: "bg-status-success-surface text-status-success-content",
  },
  SKILL: {
    label: "Skill",
    shortLabel: "Skill",
    icon: FileArchive,
    tone: "bg-muted text-muted-foreground",
  },
}

export function latestUsableRelease(asset: AssetView): Release | undefined {
  return asset.releases?.find((release) => release.availability !== "WITHDRAWN")
}

export function formatAssetCoordinate(asset: {
  namespace?: string
  slug?: string
}) {
  return [asset.namespace, asset.slug].filter(Boolean).join("/")
}

export function formatDate(value?: string) {
  if (!value) return "—"
  const date = new Date(value)
  if (Number.isNaN(date.valueOf())) return "—"
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date)
}

export function parsePayload<T>(payload?: string): T | null {
  if (!payload) return null
  try {
    return JSON.parse(payload) as T
  } catch {
    return null
  }
}

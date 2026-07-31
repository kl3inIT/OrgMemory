import { LayoutGrid } from "lucide-react"
import type { ReactNode } from "react"

import { Button } from "@/components/ui/button"
import {
  ASSET_TYPE_META,
  ASSET_TYPES,
  type AssetType,
} from "@/features/assets/asset-format"
import { cn } from "@/lib/utils"

export function AssetTypeFilter({
  value,
  onValueChange,
}: {
  value?: AssetType
  onValueChange: (value?: AssetType) => void
}) {
  return (
    <div className="overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      <div
        role="group"
        aria-label="Filter assets by type"
        className="flex min-w-max items-center gap-1 rounded-xl border border-border-default bg-surface-subtle p-1 lg:min-w-0"
      >
        <AssetTypeButton
          label="All assets"
          selected={value === undefined}
          icon={<LayoutGrid aria-hidden="true" />}
          iconTone="bg-action-primary text-action-primary-foreground"
          onClick={() => onValueChange(undefined)}
        />
        {ASSET_TYPES.map((assetType) => {
          const meta = ASSET_TYPE_META[assetType.value]
          const Icon = meta.icon

          return (
            <AssetTypeButton
              key={assetType.value}
              label={assetType.label}
              selected={value === assetType.value}
              icon={<Icon aria-hidden="true" />}
              iconTone={meta.tone}
              onClick={() => onValueChange(assetType.value)}
            />
          )
        })}
      </div>
    </div>
  )
}

function AssetTypeButton({
  label,
  selected,
  icon,
  iconTone,
  onClick,
}: {
  label: string
  selected: boolean
  icon: ReactNode
  iconTone: string
  onClick: () => void
}) {
  return (
    <Button
      type="button"
      variant="ghost"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        "h-11 min-w-40 flex-1 justify-start gap-2.5 rounded-lg border border-transparent px-3 text-content-secondary hover:bg-control-surface-hover hover:text-content-primary lg:min-w-0",
        selected &&
          "border-border-subtle bg-surface-raised text-content-primary shadow-sm hover:bg-surface-raised",
      )}
    >
      <span
        className={cn(
          "grid size-7 shrink-0 place-items-center rounded-md [&_svg]:size-4",
          iconTone,
        )}
      >
        {icon}
      </span>
      <span>{label}</span>
    </Button>
  )
}

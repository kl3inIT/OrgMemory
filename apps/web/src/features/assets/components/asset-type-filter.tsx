import { Boxes } from "lucide-react"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  ASSET_TYPES,
  type AssetType,
} from "@/features/assets/asset-format"

export function AssetTypeFilter({
  value,
  onValueChange,
}: {
  value?: AssetType
  onValueChange: (value?: AssetType) => void
}) {
  return (
    <Select
      value={value ?? "ALL"}
      onValueChange={(nextValue) =>
        onValueChange(nextValue === "ALL" ? undefined : (nextValue as AssetType))
      }
    >
      <SelectTrigger aria-label="Filter assets by type" className="w-full sm:w-52">
        <Boxes aria-hidden="true" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="ALL">All types</SelectItem>
        {ASSET_TYPES.map((assetType) => (
          <SelectItem key={assetType.value} value={assetType.value}>
            {assetType.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

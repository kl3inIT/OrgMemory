import { Copy } from "lucide-react"
import type { ComponentProps } from "react"

import { Button } from "@/components/ui/button"
import { copyWithToast } from "@/lib/copy"

export function CopyButton({
  value,
  label,
  toastLabel,
  size = "icon-sm",
  variant = "ghost",
  ...props
}: Omit<ComponentProps<typeof Button>, "children" | "onClick"> & {
  value: string
  label: string
  toastLabel: string
}) {
  return (
    <Button
      type="button"
      size={size}
      variant={variant}
      aria-label={label}
      onClick={() => void copyWithToast(value, toastLabel)}
      {...props}
    >
      <Copy aria-hidden="true" />
    </Button>
  )
}

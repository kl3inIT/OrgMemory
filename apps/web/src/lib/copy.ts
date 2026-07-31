import { toast } from "sonner"

export async function copyWithToast(value: string, label: string) {
  if (!navigator.clipboard?.writeText) {
    toast.error(`${label} could not be copied`)
    return false
  }
  try {
    await navigator.clipboard.writeText(value)
    toast.success(`${label} copied`)
    return true
  } catch {
    toast.error(`${label} could not be copied`)
    return false
  }
}

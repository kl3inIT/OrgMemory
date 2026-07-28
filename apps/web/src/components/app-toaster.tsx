import { useTheme } from "next-themes"
import { Toaster } from "sonner"

export function AppToaster() {
  const { resolvedTheme } = useTheme()

  return (
    <Toaster
      theme={resolvedTheme === "dark" ? "dark" : "light"}
      position="bottom-right"
      visibleToasts={3}
      closeButton
      gap={8}
      offset={16}
      toastOptions={{
        duration: 4000,
        closeButtonAriaLabel: "Dismiss notification",
        classNames: {
          toast: "border-border-default bg-surface-overlay text-content-primary shadow-md",
          description: "text-content-secondary",
          actionButton: "bg-action-primary text-action-primary-foreground",
          cancelButton: "bg-action-secondary text-action-secondary-foreground",
          closeButton: "border-control-border bg-control-surface text-content-secondary",
          success: "border-status-success-border bg-status-success-surface text-status-success-content",
          error: "border-status-danger-border bg-status-danger-surface text-status-danger-content",
          warning: "border-status-warning-border bg-status-warning-surface text-status-warning-content",
          info: "border-status-info-border bg-status-info-surface text-status-info-content",
        },
      }}
    />
  )
}

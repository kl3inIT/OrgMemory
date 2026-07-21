import { AppShell } from "@/components/app-shell"
import { AuthGate } from "@/components/auth-gate"

export function WorkspacePage() {
  return (
    <AuthGate>
      <AppShell />
    </AuthGate>
  )
}

import { LogOut } from "lucide-react"
import { toast } from "sonner"

import { ModeToggle } from "@/components/mode-toggle"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { submitBrowserLogout } from "@/features/session/logout"
import { useBrowserSession } from "@/features/session/use-browser-session"

function initials(name?: string, email?: string) {
  const source = name?.trim() || email?.trim() || "OrgMemory"
  return source
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase()
}

export function AppShell() {
  const session = useBrowserSession()
  const identity = session.data

  async function signOut() {
    try {
      await submitBrowserLogout()
    } catch {
      toast.error("Could not sign out. Try again.")
    }
  }

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-background px-3 py-2 text-sm font-medium focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to content
      </a>
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-screen-2xl items-center justify-between px-4 md:px-6">
          <span className="font-semibold tracking-tight">OrgMemory</span>
          <div className="flex items-center gap-1">
            <ModeToggle />
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" aria-label="Open account menu">
                  <Avatar size="sm">
                    <AvatarFallback>{initials(identity?.name, identity?.email)}</AvatarFallback>
                  </Avatar>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64">
                <DropdownMenuLabel className="space-y-1">
                  <p className="truncate text-sm font-medium">{identity?.name || "Company account"}</p>
                  {identity?.email ? (
                    <p className="truncate text-xs font-normal text-muted-foreground">{identity.email}</p>
                  ) : null}
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => void signOut()}>
                  <LogOut aria-hidden="true" />
                  Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>
      <main
        id="main-content"
        className="mx-auto min-h-[calc(100dvh-3.5rem)] max-w-screen-2xl px-4 py-6 md:px-6"
        tabIndex={-1}
      ></main>
    </div>
  )
}

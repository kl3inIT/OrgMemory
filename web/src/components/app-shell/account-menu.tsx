import { LogOut, UserRoundCog } from "lucide-react"
import { useState } from "react"
import { Link } from "@tanstack/react-router"
import { toast } from "sonner"

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
import { isAdministrator } from "@/features/session/require-session"
import type { SessionResponse } from "@/lib/hey-api"

function initials(name?: string, email?: string) {
  const source = name?.trim() || email?.trim()
  if (!source) return "OM"
  return source
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase()
}

export function AccountMenu({ identity }: { identity: SessionResponse }) {
  const [isSigningOut, setIsSigningOut] = useState(false)

  async function signOut() {
    if (isSigningOut) return
    setIsSigningOut(true)
    try {
      await submitBrowserLogout()
    } catch {
      setIsSigningOut(false)
      toast.error("Could not sign out. Try again.")
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Open account menu">
          <Avatar size="sm">
            <AvatarFallback>{initials(identity.name, identity.email)}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuLabel className="space-y-1">
          <p className="truncate text-sm font-medium">{identity.name || "Company account"}</p>
          {identity.email ? (
            <p className="truncate text-xs font-normal text-muted-foreground">{identity.email}</p>
          ) : null}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {isAdministrator(identity) ? (
          <DropdownMenuItem asChild>
            <Link to="/admin/users">
              <UserRoundCog aria-hidden="true" />
              Administration
            </Link>
          </DropdownMenuItem>
        ) : null}
        <DropdownMenuItem disabled={isSigningOut} onSelect={() => void signOut()}>
          <LogOut aria-hidden="true" />
          {isSigningOut ? "Signing out…" : "Sign out"}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

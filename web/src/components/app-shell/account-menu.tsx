import { ChevronsUpDown, LogOut, Moon, Sun, UserRoundCog } from "lucide-react"
import { useState } from "react"
import { Link } from "@tanstack/react-router"
import { useTheme } from "next-themes"
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
import { SidebarMenuButton } from "@/components/ui/sidebar"
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

export function AccountMenu({
  identity,
  variant = "icon",
  showAdministration = true,
}: {
  identity: SessionResponse
  variant?: "icon" | "sidebar"
  showAdministration?: boolean
}) {
  const [isSigningOut, setIsSigningOut] = useState(false)
  const { resolvedTheme, setTheme } = useTheme()
  const isDark = resolvedTheme === "dark"
  const displayName = identity.name || "Company account"

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
        {variant === "sidebar" ? (
          <SidebarMenuButton
            size="lg"
            tooltip={displayName}
            aria-label={`Open account menu for ${displayName}`}
            className="h-12 rounded-lg px-2 data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
          >
            <Avatar size="sm" className="shrink-0">
              <AvatarFallback>{initials(identity.name, identity.email)}</AvatarFallback>
            </Avatar>
            <span className="grid min-w-0 flex-1 text-left leading-tight group-data-[collapsible=icon]:hidden">
              <span className="truncate text-sm font-medium">{displayName}</span>
              {identity.email ? (
                <span className="truncate text-xs text-muted-foreground">{identity.email}</span>
              ) : null}
            </span>
            <ChevronsUpDown
              className="ml-auto size-4 text-content-tertiary group-data-[collapsible=icon]:hidden"
              aria-hidden="true"
            />
          </SidebarMenuButton>
        ) : (
          <Button variant="ghost" size="icon" aria-label="Open account menu">
            <Avatar size="sm">
              <AvatarFallback>{initials(identity.name, identity.email)}</AvatarFallback>
            </Avatar>
          </Button>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent
        side={variant === "sidebar" ? "right" : "bottom"}
        align="end"
        sideOffset={6}
        className="w-64"
      >
        <DropdownMenuLabel className="space-y-1">
          <p className="truncate text-sm font-medium">{displayName}</p>
          {identity.email ? (
            <p className="truncate text-xs font-normal text-muted-foreground">{identity.email}</p>
          ) : null}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {showAdministration && isAdministrator(identity) ? (
          <DropdownMenuItem asChild>
            <Link to="/admin/users">
              <UserRoundCog aria-hidden="true" />
              Administration
            </Link>
          </DropdownMenuItem>
        ) : null}
        <DropdownMenuItem onSelect={() => setTheme(isDark ? "light" : "dark")}>
          {isDark ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
          {isDark ? "Use light theme" : "Use dark theme"}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled={isSigningOut} onSelect={() => void signOut()}>
          <LogOut aria-hidden="true" />
          {isSigningOut ? "Signing out…" : "Sign out"}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

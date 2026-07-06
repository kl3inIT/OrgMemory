import { useNavigate } from "@tanstack/react-router"
import { Bell, ChevronDown, HelpCircle, LogOut, Search } from "lucide-react"
import { useAuth } from "react-oidc-context"
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
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { useOrganizationLookups, userInitials } from "@/features/organization/use-organization-context"
import { AUTH_ENABLED } from "@/lib/auth"

export function Topbar({ query, onQueryChange }: { query: string; onQueryChange: (value: string) => void }) {
  const navigate = useNavigate()
  const auth = useAuth()
  const { users } = useOrganizationLookups()
  const fallbackUser = users.find((user) => user.role === "ADMIN") ?? users[0]
  const profile = AUTH_ENABLED ? auth.user?.profile : undefined
  const displayName = profile?.name ?? fallbackUser?.name ?? "OrgMemory Admin"
  const displayDetail = profile?.email ?? fallbackUser?.role.replace("_", " ") ?? "Platform Admin"

  function submitSearch() {
    const trimmed = query.trim()
    if (!trimmed) return
    sessionStorage.setItem("orgmemory:registry-query", trimmed)
    navigate({ to: "/registry" })
  }

  return (
    <header className="flex min-h-16 items-center justify-between gap-4 border-b bg-background px-4 md:px-6">
      <div className="hidden text-sm font-medium text-muted-foreground md:block">Organizational AI Memory</div>
      <div className="flex min-w-0 flex-1 items-center justify-end gap-2 md:gap-3">
        <div className="relative w-full max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                submitSearch()
              }
            }}
            placeholder="Search assets, owners, teams..."
          />
        </div>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          aria-label="Notifications"
          className="relative"
          onClick={() => toast.info("No unread workflow alerts.")}
        >
          <Bell className="size-4" />
          <span className="absolute right-2 top-2 size-2 rounded-full bg-primary" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          aria-label="Help"
          onClick={() => toast.info("Demo guide: docs/DEMO_GUIDE.md")}
        >
          <HelpCircle className="size-4" />
        </Button>
        <Separator orientation="vertical" className="hidden h-8 md:block" />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" className="flex items-center gap-2 rounded-md px-1 py-0.5 hover:bg-accent">
              <Avatar>
                <AvatarFallback>
                  {profile?.name
                    ? profile.name
                        .split(" ")
                        .map((part) => part[0])
                        .slice(0, 2)
                        .join("")
                        .toUpperCase()
                    : userInitials(fallbackUser)}
                </AvatarFallback>
              </Avatar>
              <div className="hidden text-left leading-tight md:block">
                <div className="font-semibold">{displayName}</div>
                <div className="text-xs text-muted-foreground">{displayDetail}</div>
              </div>
              <ChevronDown className="size-4 text-muted-foreground" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel className="max-w-56 truncate">{displayDetail}</DropdownMenuLabel>
            {AUTH_ENABLED ? (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => void auth.signoutRedirect()}>
                  <LogOut className="size-4" />
                  Sign out
                </DropdownMenuItem>
              </>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}

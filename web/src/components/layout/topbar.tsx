import { useNavigate } from "@tanstack/react-router"
import { Bell, ChevronDown, HelpCircle, Search } from "lucide-react"
import { toast } from "sonner"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { useOrganizationLookups, userInitials } from "@/features/organization/use-organization-context"

export function Topbar({ query, onQueryChange }: { query: string; onQueryChange: (value: string) => void }) {
  const navigate = useNavigate()
  const { users } = useOrganizationLookups()
  const currentUser = users.find((user) => user.role === "ADMIN") ?? users[0]

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
        <div className="flex items-center gap-2">
          <Avatar>
            <AvatarFallback>{userInitials(currentUser)}</AvatarFallback>
          </Avatar>
          <div className="hidden leading-tight md:block">
            <div className="font-semibold">{currentUser?.name ?? "OrgMemory Admin"}</div>
            <div className="text-xs text-muted-foreground">{currentUser?.role.replace("_", " ") ?? "Platform Admin"}</div>
          </div>
          <ChevronDown className="size-4 text-muted-foreground" />
        </div>
      </div>
    </header>
  )
}

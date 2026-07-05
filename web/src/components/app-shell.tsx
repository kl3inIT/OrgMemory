import { Link, Outlet, useLocation } from "@tanstack/react-router"
import {
  BarChart3,
  BookOpen,
  Home,
  Inbox,
  Layers,
  Network,
  Settings,
  Sparkles,
  UserPlus,
  type LucideIcon,
} from "lucide-react"
import { useState } from "react"
import { ModeToggle } from "@/components/mode-toggle"
import { Topbar } from "@/components/layout/topbar"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar"
import { useAssets } from "@/features/assets/use-assets"

type RoutePath = "/" | "/registry" | "/create" | "/review" | "/transfer" | "/ask" | "/graph" | "/analytics" | "/settings"

const NAV: Array<{ label: string; icon: LucideIcon; to: RoutePath; exact?: boolean }> = [
  { label: "Dashboard", icon: Home, to: "/", exact: true },
  { label: "Capability Registry", icon: BookOpen, to: "/registry" },
  { label: "Review Queue", icon: Inbox, to: "/review" },
  { label: "Onboarding", icon: UserPlus, to: "/transfer" },
  { label: "Analytics", icon: BarChart3, to: "/analytics" },
  { label: "Ask Memory", icon: Sparkles, to: "/ask" },
  { label: "Knowledge Graph", icon: Network, to: "/graph" },
  { label: "Settings", icon: Settings, to: "/settings" },
]

export function AppShell() {
  const pathname = useLocation({ select: (location) => location.pathname })
  const [query, setQuery] = useState("")
  const { data } = useAssets()
  const reviewCount = (data ?? []).filter((asset) => asset.status === "IN_REVIEW" || asset.status === "DRAFT").length

  return (
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <Link
            to="/"
            className="flex items-center gap-2 px-1 py-1 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0"
          >
            <span className="grid size-8 shrink-0 place-items-center rounded-md bg-primary text-primary-foreground">
              <Layers className="size-4" />
            </span>
            <span className="truncate group-data-[collapsible=icon]:hidden">OrgMemory</span>
          </Link>
        </SidebarHeader>

        <SidebarContent>
          <SidebarMenu className="px-2">
            {NAV.map((item) => {
              const isActive = item.exact ? pathname === item.to : pathname.startsWith(item.to)
              return (
                <SidebarMenuItem key={item.to}>
                  <SidebarMenuButton asChild tooltip={item.label} isActive={isActive}>
                    <Link to={item.to}>
                      <item.icon />
                      <span>{item.label}</span>
                    </Link>
                  </SidebarMenuButton>
                  {item.to === "/review" && reviewCount ? (
                    <SidebarMenuBadge className="rounded-full bg-primary text-primary-foreground">
                      {reviewCount}
                    </SidebarMenuBadge>
                  ) : null}
                </SidebarMenuItem>
              )
            })}
          </SidebarMenu>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton asChild tooltip="New Asset">
                <Link to="/create">
                  <Sparkles />
                  <span>New Asset</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <ModeToggle />
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>

      <SidebarInset className="h-screen overflow-hidden">
        <SidebarTrigger className="absolute left-2 top-2 z-20 md:hidden" />
        <Topbar query={query} onQueryChange={setQuery} />
        <main
          data-testid="app-content"
          className="h-[calc(100vh-4rem)] overflow-x-hidden overflow-y-auto p-4 md:p-6 [&>*]:min-w-0"
        >
          <Outlet />
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}

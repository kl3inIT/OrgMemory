import { Files, MessageSquareText, Network } from "lucide-react"
import { Link, useLocation } from "@tanstack/react-router"

import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar"

const NAVIGATION = [
  { label: "Assistant", to: "/" as const, icon: MessageSquareText },
  { label: "Documents", to: "/sources" as const, icon: Files },
]

export function AppSidebar() {
  const pathname = useLocation({ select: (location) => location.pathname })

  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader className="pb-1 pt-2">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              size="lg"
              tooltip="OrgMemory"
              className="h-11 rounded-lg px-1.5 hover:bg-transparent active:bg-transparent"
            >
              <Link to="/">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-action-primary text-action-primary-foreground shadow-xs">
                  <Network className="size-4" aria-hidden="true" />
                </span>
                <span className="truncate text-label text-content-primary">OrgMemory</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup className="pt-2">
          <SidebarGroupContent>
            <SidebarMenu className="gap-1.5">
              {NAVIGATION.map((item) => (
                <SidebarMenuItem key={item.to}>
                  <SidebarMenuButton
                    asChild
                    isActive={pathname === item.to}
                    tooltip={item.label}
                    className="h-9 rounded-lg px-2.5 text-content-secondary data-[active=true]:bg-surface-raised data-[active=true]:text-content-primary data-[active=true]:shadow-xs"
                  >
                    <Link to={item.to} aria-current={pathname === item.to ? "page" : undefined}>
                      <item.icon aria-hidden="true" />
                      <span>{item.label}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}

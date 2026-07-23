import { ArrowLeft, KeyRound, Link2, ShieldCheck, UserRoundCog, Users } from "lucide-react"
import { Link, useLocation } from "@tanstack/react-router"

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar"

const PERMISSIONS = [
  { label: "Users", to: "/admin/users" as const, icon: Users },
  { label: "Source mappings", to: "/admin/mappings" as const, icon: Link2 },
  { label: "Source groups", to: "/admin/groups" as const, icon: ShieldCheck },
  { label: "SCIM", to: "/admin/scim" as const, icon: KeyRound },
]

const ITEM_CLASSES =
  "h-9 rounded-lg px-2.5 text-content-secondary data-[active=true]:bg-surface-raised data-[active=true]:text-content-primary data-[active=true]:shadow-xs"

export function AdminSidebar() {
  const pathname = useLocation({ select: (location) => location.pathname })

  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader className="pb-1 pt-2">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              size="lg"
              tooltip="Administration"
              className="h-11 rounded-lg px-1.5 hover:bg-transparent active:bg-transparent"
            >
              <Link to="/admin/users">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-action-primary text-action-primary-foreground shadow-xs">
                  <UserRoundCog className="size-4" aria-hidden="true" />
                </span>
                <span className="truncate text-label text-content-primary">Administration</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup className="pt-2">
          <SidebarGroupLabel>Permissions</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-1.5">
              {PERMISSIONS.map((item) => (
                <SidebarMenuItem key={item.to}>
                  <SidebarMenuButton
                    asChild
                    isActive={pathname === item.to}
                    tooltip={item.label}
                    className={ITEM_CLASSES}
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
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild tooltip="Back to workspace" className={ITEM_CLASSES}>
              <Link to="/">
                <ArrowLeft aria-hidden="true" />
                <span>Back to workspace</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}

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

export function AdminSidebar() {
  const pathname = useLocation({ select: (location) => location.pathname })

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild size="lg" tooltip="Administration">
              <Link to="/admin/users">
                <span className="grid size-8 shrink-0 place-items-center rounded-md border bg-background">
                  <UserRoundCog className="size-4" aria-hidden="true" />
                </span>
                <span className="truncate font-semibold">Administration</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Permissions</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {PERMISSIONS.map((item) => (
                <SidebarMenuItem key={item.to}>
                  <SidebarMenuButton asChild isActive={pathname === item.to} tooltip={item.label}>
                    <Link to={item.to}>
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
            <SidebarMenuButton asChild tooltip="Back to workspace">
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

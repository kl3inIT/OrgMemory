import { ArrowLeft, KeyRound, Link2, Plug, ShieldCheck, UserRoundCog, Users } from "lucide-react"
import { Link, useLocation } from "@tanstack/react-router"

import { AccountMenu } from "@/components/app-shell/account-menu"
import type { SessionResponse } from "@/lib/hey-api"
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
  SidebarTrigger,
} from "@/components/ui/sidebar"

const GROUPS = [
  {
    label: "Sources",
    items: [{ label: "Connectors", to: "/admin/connectors" as const, icon: Plug }],
  },
  {
    label: "Permissions",
    items: [
      { label: "Users", to: "/admin/users" as const, icon: Users },
      { label: "Source mappings", to: "/admin/mappings" as const, icon: Link2 },
      { label: "Source groups", to: "/admin/groups" as const, icon: ShieldCheck },
      { label: "SCIM", to: "/admin/scim" as const, icon: KeyRound },
    ],
  },
]

const ITEM_CLASSES =
  "h-9 rounded-lg px-2.5 text-content-secondary data-[active=true]:bg-surface-raised data-[active=true]:text-content-primary data-[active=true]:shadow-xs"

export function AdminSidebar({ identity }: { identity: SessionResponse }) {
  const pathname = useLocation({ select: (location) => location.pathname })

  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader className="pb-1 pt-2">
        <div className="flex h-11 min-w-0 items-center gap-2 px-1.5">
          <Link
            to="/admin/users"
            aria-label="Administration home"
            className="flex min-w-0 flex-1 items-center gap-2 group-data-[collapsible=icon]:hidden"
          >
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-action-primary text-action-primary-foreground shadow-xs">
              <UserRoundCog className="size-4" aria-hidden="true" />
            </span>
            <span className="truncate text-label text-content-primary">Administration</span>
          </Link>
          <SidebarTrigger className="ml-auto shrink-0 text-content-secondary group-data-[collapsible=icon]:mx-auto" />
        </div>
      </SidebarHeader>
      <SidebarContent>
        {GROUPS.map((group) => (
          <SidebarGroup key={group.label} className="pt-2">
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu className="gap-1.5">
                {group.items.map((item) => (
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
        ))}
      </SidebarContent>
      <SidebarFooter className="border-t border-sidebar-border">
        <SidebarMenu className="gap-1">
          <SidebarMenuItem>
            <SidebarMenuButton asChild tooltip="Back to workspace" className={ITEM_CLASSES}>
              <Link to="/">
                <ArrowLeft aria-hidden="true" />
                <span>Back to workspace</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <AccountMenu identity={identity} variant="sidebar" showAdministration={false} />
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}

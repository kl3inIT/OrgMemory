import { Boxes, Files, MessageSquareText, Plug, UserRoundCog } from "lucide-react"
import { Link, useLocation } from "@tanstack/react-router"

import { AccountMenu } from "@/components/app-shell/account-menu"
import { AssistantConversationList } from "@/features/assistant/components/assistant-conversation-list"
import { sessionActorKey } from "@/features/session/actor-cache-key"
import { isAdministrator } from "@/features/session/require-session"
import type { SessionResponse } from "@/lib/hey-api"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar"

const NAVIGATION = [
  { label: "Assistant", to: "/" as const, icon: MessageSquareText },
  { label: "Assets", to: "/assets" as const, icon: Boxes },
  { label: "Documents", to: "/sources" as const, icon: Files },
  { label: "Connect", to: "/connect" as const, icon: Plug },
]

const ITEM_CLASSES =
  "h-9 rounded-lg px-2.5 text-content-secondary data-[active=true]:bg-surface-raised data-[active=true]:text-content-primary data-[active=true]:shadow-xs"

export function AppSidebar({ identity }: { identity: SessionResponse }) {
  const location = useLocation()
  const pathname = location.pathname
  const activeConversationId =
    typeof (location.search as { chat?: unknown }).chat === "string"
      ? (location.search as { chat: string }).chat
      : undefined

  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader className="pb-1 pt-2">
        <div className="flex h-11 min-w-0 items-center gap-2 px-1.5">
          <Link
            to="/"
            aria-label="OrgMemory home"
            className="flex min-w-0 flex-1 items-center gap-2 group-data-[collapsible=icon]:hidden"
          >
            <img
              src="/orgmemory-mark.png"
              alt=""
              aria-hidden="true"
              className="size-8 shrink-0 rounded-lg shadow-xs"
            />
            <span className="truncate text-label text-content-primary">OrgMemory</span>
          </Link>
          <SidebarTrigger className="ml-auto shrink-0 text-content-secondary group-data-[collapsible=icon]:mx-auto" />
        </div>
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
        {pathname === "/" ? (
          <AssistantConversationList
            activeConversationId={activeConversationId}
            actorKey={sessionActorKey(identity)}
          />
        ) : null}
      </SidebarContent>
      <SidebarFooter className="border-t border-sidebar-border">
        <SidebarMenu className="gap-1">
          {isAdministrator(identity) ? (
            <SidebarMenuItem>
              <SidebarMenuButton
                asChild
                tooltip="Administration"
                className={ITEM_CLASSES}
              >
                <Link to="/admin/users">
                  <UserRoundCog aria-hidden="true" />
                  <span>Administration</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          ) : null}
          <SidebarMenuItem>
            <AccountMenu identity={identity} variant="sidebar" showAdministration={false} />
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}

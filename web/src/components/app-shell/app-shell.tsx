import { type ReactNode, useEffect } from "react"
import { useMatches } from "@tanstack/react-router"

import { AccountMenu } from "@/components/app-shell/account-menu"
import { AppSidebar } from "@/components/app-shell/app-sidebar"
import { ModeToggle } from "@/components/mode-toggle"
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar"
import type { SessionResponse } from "@/lib/hey-api"

export function AppShell({ identity, children }: { identity: SessionResponse; children: ReactNode }) {
  const pageTitle = useMatches({
    select: (matches) =>
      [...matches].reverse().find((match) => match.staticData.title)?.staticData.title ?? "OrgMemory",
  })

  useEffect(() => {
    document.title = pageTitle === "OrgMemory" ? "OrgMemory" : `${pageTitle} · OrgMemory`
  }, [pageTitle])

  return (
    <SidebarProvider className="h-dvh min-h-0 overflow-hidden">
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-background px-3 py-2 text-sm font-medium focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to content
      </a>
      <AppSidebar />
      <SidebarInset
        id="main-content"
        className="h-dvh min-w-0 overflow-hidden bg-surface-base md:h-[calc(100dvh-1rem)] md:border md:border-border-subtle"
        tabIndex={-1}
      >
        <header className="flex h-12 shrink-0 items-center justify-between border-b border-border-subtle bg-surface-base/90 px-3 backdrop-blur-sm md:px-4">
          <div className="flex min-w-0 items-center gap-2.5">
            <SidebarTrigger className="text-content-secondary" />
            <span className="truncate text-label text-content-primary">{pageTitle}</span>
          </div>
          <div className="flex items-center gap-1">
            <ModeToggle />
            <AccountMenu identity={identity} />
          </div>
        </header>
        <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden">{children}</div>
      </SidebarInset>
    </SidebarProvider>
  )
}

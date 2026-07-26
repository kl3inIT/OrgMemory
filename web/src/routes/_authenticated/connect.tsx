import { createFileRoute } from "@tanstack/react-router"

import { McpConnectPage } from "@/features/mcp/components/mcp-connect-page"

export const Route = createFileRoute("/_authenticated/connect")({
  component: McpConnectPage,
  staticData: { title: "Connect" },
})

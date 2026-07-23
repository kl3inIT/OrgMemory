import { createFileRoute } from "@tanstack/react-router"

import { ConnectionDetailPage } from "@/features/admin/components/connection-detail-page"

/**
 * One connection. The path is the source system and the connection key because that pair is
 * what identifies a connection everywhere else — in the ledger, in the API, and in the audit
 * trail — so the address of the screen is the identity of the thing.
 */
export const Route = createFileRoute("/admin/connectors/$sourceSystem/$connectionKey")({
  component: ConnectionDetailRoute,
  staticData: { title: "Connection" },
})

function ConnectionDetailRoute() {
  const { sourceSystem, connectionKey } = Route.useParams()
  return <ConnectionDetailPage sourceSystem={sourceSystem} connectionKey={connectionKey} />
}

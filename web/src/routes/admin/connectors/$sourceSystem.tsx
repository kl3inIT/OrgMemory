import { createFileRoute } from "@tanstack/react-router"

import { ConnectionWizard } from "@/features/admin/components/connection-wizard"

/**
 * Connecting one source. Adding a connection and reconfiguring one are the same steps from a
 * different starting position, so they are the same route: without a connection the flow begins
 * at the credential, because until one has been checked there is no key to configure against.
 *
 * <p>The source is a path parameter rather than a route per source. `/new` is a static segment
 * and wins over this one, so the catalogue keeps its own address.
 */
export const Route = createFileRoute("/admin/connectors/$sourceSystem")({
  validateSearch: (search: Record<string, unknown>): { connection?: string } =>
    typeof search.connection === "string" ? { connection: search.connection } : {},
  component: ConnectionWizardRoute,
  staticData: { title: "Connect a source" },
})

function ConnectionWizardRoute() {
  const { sourceSystem } = Route.useParams()
  const { connection } = Route.useSearch()
  return <ConnectionWizard sourceSystem={sourceSystem} connectionKey={connection} />
}

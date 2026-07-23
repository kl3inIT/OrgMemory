import { createFileRoute } from "@tanstack/react-router"

import { SlackConnectionWizard } from "@/features/admin/components/slack-connection-wizard"

/**
 * Adding a workspace and reconfiguring one are the same steps in a different starting
 * position, so they are the same route. Without a connection the flow begins at the token,
 * because until a token has been checked there is no workspace id to configure against.
 */
export const Route = createFileRoute("/admin/connectors/slack")({
  validateSearch: (search: Record<string, unknown>): { connection?: string } =>
    typeof search.connection === "string" ? { connection: search.connection } : {},
  component: SlackConnectorRoute,
  staticData: { title: "Slack" },
})

function SlackConnectorRoute() {
  const { connection } = Route.useSearch()
  return <SlackConnectionWizard connectionKey={connection} />
}

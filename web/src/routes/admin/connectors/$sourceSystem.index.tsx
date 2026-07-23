import { createFileRoute, useNavigate } from "@tanstack/react-router"

import { isWizardStep, type WizardStep } from "@/features/admin/connection-steps"
import { ConnectionWizard } from "@/features/admin/components/connection-wizard"

/**
 * Connecting one source. Adding a connection and reconfiguring one are the same steps from a
 * different starting position, so they are the same route: without a connection the flow begins
 * at the credential, because until one has been checked there is no key to configure against.
 *
 * <p>The source is a path parameter rather than a route per source. `/new` is a static segment
 * and wins over this one, so the catalogue keeps its own address.
 *
 * <p>A leaf (`.index`) rather than a bare `$sourceSystem.tsx`, because the connection page
 * addresses `$sourceSystem/$connectionKey`. A dot in a route filename means nesting, so as a
 * bare file this became that page's parent layout — and since it renders the wizard rather than
 * an `Outlet`, it stood in for its own child: opening a connection showed this form instead.
 *
 * <p>Which connection and which step are both in the address, so the wizard holds no position
 * of its own. That makes Back mean the previous step rather than leaving the form, a reload
 * keep the reader where they were, and a step something another screen can link straight to.
 */
export const Route = createFileRoute("/admin/connectors/$sourceSystem/")({
  validateSearch: (search: Record<string, unknown>): { connection?: string; step?: WizardStep } => ({
    ...(typeof search.connection === "string" ? { connection: search.connection } : {}),
    // An unknown step is dropped rather than rejected: a stale or edited address should open
    // the wizard at its default, not fail to open it.
    ...(isWizardStep(search.step) ? { step: search.step } : {}),
  }),
  component: ConnectionWizardRoute,
  staticData: { title: "Connect a source" },
})

function ConnectionWizardRoute() {
  const { sourceSystem } = Route.useParams()
  const { connection, step } = Route.useSearch()
  const navigate = useNavigate({ from: Route.fullPath })
  return (
    <ConnectionWizard
      sourceSystem={sourceSystem}
      connectionKey={connection}
      step={step}
      onNavigate={(next) => void navigate({ search: (current) => ({ ...current, ...next }) })}
    />
  )
}

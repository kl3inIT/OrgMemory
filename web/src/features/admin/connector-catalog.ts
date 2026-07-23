import { MessagesSquare, MonitorSmartphone, Upload } from "lucide-react"
import type { LucideIcon } from "lucide-react"

/**
 * The evidence sources OrgMemory governs, as a catalogue an administrator picks from.
 *
 * <p>This is deliberately not a marketplace. The product boundary names a connector
 * marketplace as a non-goal and says OrgMemory differentiates through governed knowledge
 * rather than a broad connector catalogue, so this lists what the system can actually
 * ingest today plus the one source the vision names and has not built. Nothing here is
 * aspirational filler: a tile that cannot be clicked says why.
 *
 * <p>Adding a source is adding an entry. That is the whole point of the shape — the page
 * has no knowledge of Slack in it.
 */
export type ConnectorCatalogEntry = {
  id: string
  name: string
  kind: "Connector" | "Direct" | "Edge"
  description: string
  icon: LucideIcon
  /** Where picking it goes. Absent means it cannot be picked yet. */
  to?: "/admin/connectors/slack" | "/sources"
  unavailable?: string
}

export const CONNECTOR_CATALOG: ConnectorCatalogEntry[] = [
  {
    id: "slack",
    name: "Slack",
    kind: "Connector",
    description:
      "Crawls channels a bot can see. Channel membership at crawl time becomes the access rule, so a thread is retrievable only by the people who could read it in Slack.",
    icon: MessagesSquare,
    to: "/admin/connectors/slack",
  },
  {
    id: "upload",
    name: "File upload",
    kind: "Direct",
    description:
      "A person uploads a document into a Knowledge Space. Access follows the Space rather than a source system, because there is no source system to ask.",
    icon: Upload,
    to: "/sources",
  },
  {
    id: "edge",
    name: "Edge capture",
    kind: "Edge",
    description:
      "Work captured on a device, kept private until its author publishes it. Passive discovery, active publishing.",
    icon: MonitorSmartphone,
    unavailable: "Not built yet.",
  },
]

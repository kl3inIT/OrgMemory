import type { SourceIconName } from "@/features/admin/components/source-icon"

/**
 * The evidence sources OrgMemory governs, as a catalogue an administrator picks from.
 *
 * <p>This is deliberately not a marketplace. The product boundary names a connector
 * marketplace as a non-goal, so this lists what the system can actually ingest today plus the
 * one source the vision names and has not built. Nothing here is aspirational filler: a tile
 * that cannot be picked says why, and the reason distinguishes "OrgMemory has not built this"
 * from "this deployment did not install it".
 *
 * <p>Adding a source is adding an entry. The page has no knowledge of Slack in it.
 */
export type ConnectorCatalogEntry = {
  id: string
  name: string
  description: string
  icon: SourceIconName
  /**
   * Who keeps the access rule for what arrives this way. This is the same distinction the
   * ledger records per object in `source_objects.acl_authority`, not a browser-side taxonomy —
   * which is why it is worth grouping the catalogue by. It is the one difference between two
   * sources that an administrator has to understand before choosing either.
   */
  aclAuthority: "SOURCE" | "ORGMEMORY"
  /**
   * The name an installed adapter reports from `GET /api/admin/connectors/sources`. Absent
   * means nothing is crawled for this source, so there is no adapter to be missing.
   */
  sourceSystem?: string
  /**
   * How this source's credential is asked for. Present when the source has one — a direct
   * upload does not — and it is what stops the wizard's first step from being written per
   * source, the way its configuration step already stopped being.
   */
  credential?: ConnectorCredentialDescriptor
  /** Where picking it goes, when that is not the wizard for this source. */
  to?: "/sources"
  /** Why it cannot be picked, when the reason is the product rather than the deployment. */
  unavailable?: string
}

export type ConnectorCredentialDescriptor = {
  label: string
  placeholder?: string
  /** A service account key is a JSON document; a bot token is one line. */
  multiline?: boolean
  /** What the credential has to have been granted, in the source's own words. */
  requirements?: string[]
  /** What else has to be true at the source before a crawl can read anything. */
  note?: string
  /** What the connection will be keyed on, so the probe's answer is recognisable. */
  keyName: string
  /**
   * Where the credential is made. Onyx's source map carries a docs link per connector for the
   * same reason: the step asks for something that does not exist yet, and a screen that only
   * says what to paste leaves the reader to find out where from.
   */
  issuer?: { label: string; href: string }
  /**
   * A document the source accepts verbatim to produce a correctly scoped credential, offered
   * to copy. It is the difference between listing six scopes and granting them.
   */
  recipe?: { label: string; body: string }
}

export const CONNECTOR_CATALOG: ConnectorCatalogEntry[] = [
  {
    id: "slack",
    name: "Slack",
    description:
      "Crawls channels a bot can see. Channel membership at crawl time becomes the access rule, so a thread is retrievable only by the people who could read it in Slack.",
    icon: "slack",
    aclAuthority: "SOURCE",
    sourceSystem: "slack",
    credential: {
      label: "Bot token",
      placeholder: "xoxb-…",
      keyName: "workspace",
      requirements: [
        "channels:read",
        "channels:history",
        "groups:read",
        "groups:history",
        "users:read",
        "users:read.email",
        "channels:join",
      ],
      note: "channels:join lets the bot add itself to the public channels you choose. Leave it out and it reads only what it has been invited to; a private channel needs an invite either way.",
      issuer: { label: "Create a Slack app", href: "https://api.slack.com/apps" },
      recipe: {
        label: "App manifest",
        body: `display_information:
  name: OrgMemory
  description: Crawls channels into the OrgMemory governed ledger
features:
  bot_user:
    display_name: OrgMemory
    always_online: false
oauth_config:
  scopes:
    bot:
      - channels:read
      - channels:history
      - groups:read
      - groups:history
      - users:read
      - users:read.email
      - channels:join
settings:
  org_deploy_enabled: false
  socket_mode_enabled: false
  is_hosted: false
  token_rotation_enabled: false`,
      },
    },
  },
  {
    id: "google_drive",
    name: "Google Drive",
    description:
      "Crawls documents a service account can see. Each file's own sharing becomes the access rule, so a document is retrievable only by the people it was shared with in Drive.",
    icon: "google-drive",
    aclAuthority: "SOURCE",
    sourceSystem: "google_drive",
    credential: {
      label: "Service account key",
      placeholder: '{ "type": "service_account", … }',
      multiline: true,
      keyName: "domain",
      requirements: ["https://www.googleapis.com/auth/drive.readonly"],
      note: "Either share the folders to crawl with the service account, or grant it domain-wide delegation and name a user to read as.",
      issuer: {
        label: "Create a service account",
        href: "https://console.cloud.google.com/iam-admin/serviceaccounts",
      },
    },
  },
  {
    id: "upload",
    name: "File upload",
    description:
      "A person uploads a document into a Knowledge Space. Access follows the Space rather than a source system, because there is no source system to ask.",
    icon: "upload",
    aclAuthority: "ORGMEMORY",
    to: "/sources",
  },
  {
    id: "edge",
    name: "Edge capture",
    description:
      "Work captured on a device, kept private until its author publishes it. Passive discovery, active publishing.",
    icon: "edge",
    aclAuthority: "ORGMEMORY",
    unavailable: "Not built yet.",
  },
]

/**
 * How the catalogue is grouped.
 *
 * <p>Onyx groups by what kind of tool a source is — wiki, storage, ticketing, messaging. That
 * is the right axis when you are scanning fifty-five tiles for the one product your company
 * uses. It is the wrong axis here, where it would produce one heading per tile and tell an
 * administrator nothing they did not already know from the logo.
 *
 * <p>Custody is the axis that carries a fact instead: it says whether the access rule comes
 * from somewhere else or from OrgMemory, which is the difference that decides how retrieval
 * behaves afterwards. When the catalogue is large enough that these groups get crowded, a
 * tool-kind axis can be added underneath.
 */
export const CATALOG_GROUPS = [
  {
    aclAuthority: "SOURCE",
    title: "Crawled from a connected system",
    description:
      "The system that already holds the work keeps the access rule. Every crawl records who could read each object at that moment, and retrieval enforces the newest sealed generation of it — so losing access there stops retrieval here.",
  },
  {
    aclAuthority: "ORGMEMORY",
    title: "Given to OrgMemory directly",
    description:
      "There is no system to ask, so the Knowledge Space it was published into is the access rule.",
  },
] as const

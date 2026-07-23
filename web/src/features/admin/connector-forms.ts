/**
 * What a source's own settings look like on a form.
 *
 * <p>The backend stores those settings as one opaque JSON document, because only the adapter
 * that defined the shape can read it. That leaves the browser needing to know the shape in
 * exactly one place, which is here: a descriptor per source, not a component per source. Onyx
 * arrives at the same answer from fifty-five connectors; the reason holds at one, because the
 * alternative is a hand-written form that drifts from what the adapter parses.
 *
 * <p>Only the settings the source owns live here. A Knowledge Space, an actor, and a crawl
 * interval are true of every source and are real columns with real constraints, so the wizard
 * renders those itself rather than pretending each source invented them.
 */

type FieldBase = {
  name: string
  label: string
  description?: string
}

export type ConnectorField =
  | (FieldBase & { type: "text"; default?: string; placeholder?: string })
  | (FieldBase & {
      type: "list"
      default?: string[]
      placeholder?: string
      /** Applied to each entry on the way to storage, for sources that constrain their names. */
      normalize?: (value: string) => string
    })
  /**
   * A list whose entries the source can enumerate, so it is picked rather than typed. Stored
   * exactly like a `list` — the difference is only that the options are known.
   */
  | (FieldBase & { type: "scopes"; default?: string[]; emptyMeans?: string })
  | (FieldBase & { type: "number"; default: number; min?: number })
  | (FieldBase & { type: "checkbox"; default?: boolean })
  | (FieldBase & { type: "select"; default: string; options: { value: string; label: string }[] })

export type ConnectorFormDescriptor = {
  /** Decisions an administrator has to make. */
  fields: ConnectorField[]
  /**
   * Decisions that already have a working answer. Onyx splits the same way, and the split is
   * the useful part: a field down here is one nobody has to read to finish.
   */
  advanced: ConnectorField[]
}

export const CONNECTOR_FORMS: Record<string, ConnectorFormDescriptor> = {
  slack: {
    fields: [
      {
        type: "scopes",
        name: "channels",
        label: "Channels",
        description:
          "Choosing a channel is the instruction to read it: the bot adds itself to a public channel it is not in yet. A private one it cannot, so that says what to run in Slack instead.",
        emptyMeans:
          "Nothing chosen means the bot reads only the channels it has been invited to, and adds itself to none.",
      },
    ],
    advanced: [
      {
        type: "number",
        name: "maxThreadsPerChannel",
        label: "Threads per channel",
        default: 500,
        min: 1,
        description: "A bound on one crawl. Hitting it withdraws the completeness claim.",
      },
    ],
  },
  google_drive: {
    fields: [
      {
        type: "list",
        name: "folderIds",
        label: "Folders",
        placeholder: "1AbCdEf…, 1XyZ…",
        description:
          "Folder ids, from the end of a folder's URL. Leave empty to read everything the service account can see. A filter also stops the crawl claiming it enumerated the connection, so nothing is retired on its word.",
      },
      {
        type: "text",
        name: "impersonatedUser",
        label: "Read as",
        placeholder: "someone@example.com",
        description:
          "Only with domain-wide delegation. Left empty, the service account reads what has been shared with it directly, which is the safer arrangement and usually the one you want first.",
      },
    ],
    advanced: [
      {
        type: "checkbox",
        name: "includeSharedDrives",
        label: "Include shared drives",
        default: true,
        description: "Shared drives the account is a member of, as well as files shared with it.",
      },
      {
        type: "number",
        name: "maxFiles",
        label: "Files per crawl",
        default: 500,
        min: 1,
        description: "A bound on one crawl. Hitting it withdraws the completeness claim.",
      },
    ],
  },
}

/** Every field on a descriptor, in the order they are rendered. */
export function allFields(descriptor: ConnectorFormDescriptor): ConnectorField[] {
  return [...descriptor.fields, ...descriptor.advanced]
}

/**
 * What the boxes hold while somebody is typing.
 *
 * <p>Text, lists and numbers are edited as strings because that is what a half-typed value is:
 * "12" on the way to "120", "general, " on the way to a second channel. Converting on every
 * keystroke would delete the characters that make an entry incomplete, which is most of them.
 */
export type ConnectorFieldDraft = Record<string, string | boolean>

export function draftFrom(
  descriptor: ConnectorFormDescriptor,
  config: Record<string, unknown>,
): ConnectorFieldDraft {
  const draft: ConnectorFieldDraft = {}
  for (const field of allFields(descriptor)) {
    const stored = config[field.name]
    switch (field.type) {
      case "text":
        draft[field.name] = typeof stored === "string" ? stored : (field.default ?? "")
        break
      case "list":
      case "scopes": {
        const entries = Array.isArray(stored)
          ? stored.filter((entry): entry is string => typeof entry === "string")
          : (field.default ?? [])
        draft[field.name] = entries.join(", ")
        break
      }
      case "number":
        draft[field.name] = String(typeof stored === "number" ? stored : field.default)
        break
      case "checkbox":
        draft[field.name] = typeof stored === "boolean" ? stored : (field.default ?? false)
        break
      case "select":
        draft[field.name] =
          typeof stored === "string" && field.options.some((option) => option.value === stored)
            ? stored
            : field.default
        break
    }
  }
  return draft
}

/** The document the adapter will read. Only fields the descriptor declares reach it. */
export function configFrom(
  descriptor: ConnectorFormDescriptor,
  draft: ConnectorFieldDraft,
): Record<string, unknown> {
  const config: Record<string, unknown> = {}
  for (const field of allFields(descriptor)) {
    const value = draft[field.name]
    switch (field.type) {
      case "text":
        config[field.name] = String(value ?? "")
        break
      case "list":
      case "scopes":
        config[field.name] = String(value ?? "")
          .split(",")
          .map((entry) => entry.trim())
          .filter(Boolean)
          .map((entry) => ("normalize" in field && field.normalize ? field.normalize(entry) : entry))
        break
      case "number":
        config[field.name] = Number.parseInt(String(value), 10)
        break
      case "checkbox":
        config[field.name] = value === true
        break
      case "select":
        config[field.name] = String(value ?? field.default)
        break
    }
  }
  return config
}

/**
 * The fields that cannot be saved, by name.
 *
 * <p>Only numbers can be wrong here — everything else accepts whatever was typed. A number
 * that is blank or below its floor would be stored as a bound the crawl cannot honour, so it
 * is caught before the request rather than by a constraint afterwards.
 */
export function invalidFields(
  descriptor: ConnectorFormDescriptor,
  draft: ConnectorFieldDraft,
): string[] {
  return allFields(descriptor)
    .filter((field) => {
      if (field.type !== "number") return false
      const parsed = Number.parseInt(String(draft[field.name]), 10)
      return Number.isNaN(parsed) || parsed < (field.min ?? 1)
    })
    .map((field) => field.name)
}

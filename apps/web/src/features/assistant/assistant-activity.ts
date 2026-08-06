import type { UIMessage } from "ai"

export interface AssistantActivity {
  phase:
    | "RETRIEVAL"
    | "GENERATION"
    | "SKILL_DISCOVERY"
    | "SKILL_ACTIVATION"
    | "SKILL_RESOURCE"
  state: "ACTIVE" | "COMPLETE" | "FAILED"
  evidenceCount?: number | null
  skillOrdinal?: number | null
  skillTitle?: string | null
}

export function activityLabel(activity: AssistantActivity | null) {
  if (!activity) return "Connecting to the Assistant…"
  if (activity.phase === "RETRIEVAL" && activity.state === "ACTIVE") {
    return "Searching permitted knowledge…"
  }
  if (activity.phase === "RETRIEVAL") {
    const count = activity.evidenceCount ?? 0
    return count === 1 ? "Found 1 permitted source" : `Found ${count} permitted sources`
  }
  if (activity.phase === "SKILL_DISCOVERY") {
    if (activity.state === "ACTIVE") return "Looking for a relevant skill…"
    if (activity.state === "FAILED") return "Skill search unavailable — continuing…"
    const count = activity.evidenceCount ?? 0
    return count === 1 ? "Found 1 available skill" : `Found ${count} available skills`
  }
  if (activity.phase === "SKILL_ACTIVATION") {
    if (activity.state === "ACTIVE") return "Loading skill instructions…"
    if (activity.state === "FAILED") return "Skill unavailable — continuing…"
    return "Preparing the grounded answer…"
  }
  if (activity.phase === "SKILL_RESOURCE") {
    if (activity.state === "ACTIVE") return "Reading a skill reference…"
    if (activity.state === "FAILED") return "Skill reference unavailable — continuing…"
    return "Preparing the grounded answer…"
  }
  return "Preparing the grounded answer…"
}

export interface AssistantSkillReceipt {
  ordinal: number
  title: string | null
  activation: "ACTIVE" | "COMPLETE"
  resource: "ACTIVE" | "COMPLETE" | "FAILED" | null
}

const INVISIBLE_TEXT = new Set(["\u200b", "\u200c", "\u200d", "\u2060", "\ufeff"])
const MARKDOWN_FRAMING = new Set([
  "*",
  "_",
  "~",
  "`",
  "#",
  ">",
  "|",
  "\\",
  "[",
  "]",
  "(",
  ")",
  "{",
  "}",
  "-",
])

export function hasRenderableAssistantText(text: string) {
  return Array.from(text).some(
    (character) =>
      !/\s/u.test(character) &&
      !INVISIBLE_TEXT.has(character) &&
      !MARKDOWN_FRAMING.has(character),
  )
}

export function hasVisibleAssistantOutput(message: Pick<UIMessage, "parts">) {
  return message.parts.some(
    (part) => part.type === "text" && hasRenderableAssistantText(part.text),
  )
}

export function reduceSkillReceipts(
  current: AssistantSkillReceipt[],
  activity: AssistantActivity,
): AssistantSkillReceipt[] {
  const ordinal = activity.skillOrdinal
  if (
    (activity.phase !== "SKILL_ACTIVATION" && activity.phase !== "SKILL_RESOURCE") ||
    ordinal == null
  ) {
    return current
  }

  if (activity.phase === "SKILL_ACTIVATION") {
    if (activity.state === "FAILED") {
      return current.filter((receipt) => receipt.ordinal !== ordinal)
    }
    const existing = current.find((receipt) => receipt.ordinal === ordinal)
    const title = activity.state === "COMPLETE" ? activity.skillTitle ?? null : null
    const next: AssistantSkillReceipt = {
      ordinal,
      title: title ?? existing?.title ?? null,
      activation: activity.state,
      resource: existing?.resource ?? null,
    }
    return [...current.filter((receipt) => receipt.ordinal !== ordinal), next].sort(
      (left, right) => left.ordinal - right.ordinal,
    )
  }

  const existing = current.find((receipt) => receipt.ordinal === ordinal)
  if (!existing?.title || existing.activation !== "COMPLETE") return current
  return current.map((receipt) =>
    receipt.ordinal === ordinal
      ? { ...receipt, resource: activity.state }
      : receipt,
  )
}

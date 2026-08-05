export interface AssistantActivity {
  phase:
    | "RETRIEVAL"
    | "GENERATION"
    | "SKILL_DISCOVERY"
    | "SKILL_ACTIVATION"
    | "SKILL_RESOURCE"
  state: "ACTIVE" | "COMPLETE" | "FAILED"
  evidenceCount?: number | null
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
    return "Skill instructions ready"
  }
  if (activity.phase === "SKILL_RESOURCE") {
    if (activity.state === "ACTIVE") return "Reading a skill reference…"
    if (activity.state === "FAILED") return "Skill reference unavailable — continuing…"
    return "Skill reference ready"
  }
  return "Preparing the grounded answer…"
}

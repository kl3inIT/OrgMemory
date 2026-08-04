const DRAFT_PREFIX = "orgmemory:assistant-draft:v1:"
const MAX_DRAFT_LENGTH = 4_000

function draftKey(actorKey: string, conversationId?: string) {
  return `${DRAFT_PREFIX}${encodeURIComponent(actorKey)}:${conversationId ?? "new"}`
}

export function readAssistantDraft(actorKey: string, conversationId?: string) {
  try {
    return sessionStorage.getItem(draftKey(actorKey, conversationId)) ?? ""
  } catch {
    return ""
  }
}

export function writeAssistantDraft(
  actorKey: string,
  conversationId: string | undefined,
  value: string,
) {
  const bounded = value.slice(0, MAX_DRAFT_LENGTH)
  const key = draftKey(actorKey, conversationId)
  try {
    if (bounded.length === 0) {
      sessionStorage.removeItem(key)
    } else {
      sessionStorage.setItem(key, bounded)
    }
  } catch {
    // The composer remains usable when browser storage is disabled.
  }
  return bounded
}

export function clearAssistantDraft(actorKey: string, conversationId?: string) {
  try {
    sessionStorage.removeItem(draftKey(actorKey, conversationId))
  } catch {
    // There is no persisted draft to clear when storage is unavailable.
  }
}

export function clearAssistantActorDrafts(actorKey: string) {
  clearDraftsWithPrefix(`${DRAFT_PREFIX}${encodeURIComponent(actorKey)}:`)
}

export function clearAllAssistantDrafts() {
  clearDraftsWithPrefix(DRAFT_PREFIX)
}

function clearDraftsWithPrefix(prefix: string) {
  try {
    const matchingKeys: string[] = []
    for (let index = 0; index < sessionStorage.length; index += 1) {
      const key = sessionStorage.key(index)
      if (key?.startsWith(prefix)) matchingKeys.push(key)
    }
    for (const key of matchingKeys) sessionStorage.removeItem(key)
  } catch {
    // There is no persisted draft to clear when storage is unavailable.
  }
}

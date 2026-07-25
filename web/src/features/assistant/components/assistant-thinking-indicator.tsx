import { Network } from "lucide-react"

export function AssistantThinkingIndicator() {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-label="OrgMemory is searching permitted knowledge"
      className="flex h-8 items-center gap-2.5"
    >
      <span className="grid size-6 shrink-0 place-items-center text-content-primary">
        <Network className="size-[18px] motion-safe:animate-pulse" aria-hidden="true" />
      </span>
      <span className="assistant-thinking-text text-label">
        Searching permitted knowledge…
      </span>
    </div>
  )
}

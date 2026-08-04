export function AssistantThinkingIndicator({
  label = "Searching permitted knowledge…",
}: {
  label?: string
}) {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={label}
      className="flex h-8 items-center"
    >
      <span className="assistant-thinking-text text-label">
        {label}
      </span>
    </div>
  )
}

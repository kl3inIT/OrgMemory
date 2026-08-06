import type { AssistantSkillReceipt } from "@/features/assistant/assistant-activity"
import { AssistantSkillActivity } from "@/features/assistant/components/assistant-skill-activity"
import { AssistantThinkingIndicator } from "@/features/assistant/components/assistant-thinking-indicator"

export function AssistantTurnActivity({
  receipts,
  settled,
  waitingLabel,
}: {
  receipts: AssistantSkillReceipt[]
  settled: boolean
  waitingLabel: string | null
}) {
  const hasReceipt = receipts.some((receipt) => receipt.title)
  if (!hasReceipt && !waitingLabel) return null

  return (
    <div
      className="w-full max-w-[95%] space-y-2"
      aria-label="Current turn activity"
    >
      <AssistantSkillActivity receipts={receipts} settled={settled} />
      {waitingLabel ? <AssistantThinkingIndicator label={waitingLabel} /> : null}
    </div>
  )
}

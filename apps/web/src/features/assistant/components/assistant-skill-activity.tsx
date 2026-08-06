import { Check, ChevronDown, LoaderCircle, Sparkles, TriangleAlert } from "lucide-react"
import { useEffect, useState } from "react"

import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import type { AssistantSkillReceipt } from "@/features/assistant/assistant-activity"
import { cn } from "@/lib/utils"

function StepState({ state }: { state: "ACTIVE" | "COMPLETE" | "FAILED" }) {
  if (state === "ACTIVE") {
    return <LoaderCircle className="size-3.5 animate-spin" aria-hidden="true" />
  }
  if (state === "FAILED") {
    return <TriangleAlert className="size-3.5 text-destructive" aria-hidden="true" />
  }
  return <Check className="size-3.5" aria-hidden="true" />
}

function SkillReceipt({
  receipt,
  settled,
}: {
  receipt: AssistantSkillReceipt
  settled: boolean
}) {
  const [open, setOpen] = useState(!settled)
  const active = receipt.activation === "ACTIVE" || receipt.resource === "ACTIVE"

  useEffect(() => {
    if (settled) setOpen(false)
  }, [settled])

  if (!receipt.title) return null

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <div className="rounded-xl border border-border-subtle bg-surface-subtle/40 text-content-secondary">
        <CollapsibleTrigger className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left text-sm hover:text-content-primary">
          {active ? (
            <LoaderCircle className="size-4 shrink-0 animate-spin" aria-hidden="true" />
          ) : (
            <Sparkles className="size-4 shrink-0" aria-hidden="true" />
          )}
          <span className="min-w-0 flex-1 truncate">
            Using <span className="font-medium text-content-primary">{receipt.title}</span> skill
          </span>
          <ChevronDown
            className={cn("size-4 shrink-0 transition-transform", open && "rotate-180")}
            aria-hidden="true"
          />
        </CollapsibleTrigger>
        <CollapsibleContent>
          <div className="space-y-2 border-t border-border-subtle px-3 py-2.5 text-xs">
            <div className="flex items-center gap-2">
              <StepState state={receipt.activation} />
              <span>
                {receipt.activation === "ACTIVE"
                  ? "Loading skill instructions"
                  : "Skill instructions loaded"}
              </span>
            </div>
            {receipt.resource ? (
              <div className="flex items-center gap-2">
                <StepState state={receipt.resource} />
                <span>
                  {receipt.resource === "ACTIVE"
                    ? "Reading a skill reference"
                    : receipt.resource === "FAILED"
                      ? "Skill reference unavailable"
                      : "Skill reference read"}
                </span>
              </div>
            ) : null}
          </div>
        </CollapsibleContent>
      </div>
    </Collapsible>
  )
}

export function AssistantSkillActivity({
  receipts,
  settled,
}: {
  receipts: AssistantSkillReceipt[]
  settled: boolean
}) {
  const visible = receipts.filter((receipt) => receipt.title)
  if (visible.length === 0) return null

  return (
    <div className="space-y-2" aria-label="Skills used in this turn">
      {visible.map((receipt) => (
        <SkillReceipt key={receipt.ordinal} receipt={receipt} settled={settled} />
      ))}
    </div>
  )
}

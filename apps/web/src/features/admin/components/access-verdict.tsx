import { CircleCheck, CircleHelp, CircleSlash, Lock } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"

import type { AclProvenanceResponse } from "@/lib/hey-api/types.gen"

export type AccessState = "ALLOWED" | "DENIED" | "UNKNOWN"

const VERDICTS = {
  ALLOWED: { label: "Allowed", icon: CircleCheck, className: "text-emerald-600 dark:text-emerald-400" },
  DENIED: { label: "Denied", icon: CircleSlash, className: "text-destructive" },
  UNKNOWN: { label: "Unknown", icon: CircleHelp, className: "text-amber-600 dark:text-amber-400" },
} as const

function relativeAge(from: string) {
  const elapsed = Date.now() - new Date(from).getTime()
  const hours = Math.floor(elapsed / 3_600_000)
  if (hours < 1) return "under an hour ago"
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

/**
 * Every verdict in the admin surface renders through this, so none of them can quietly
 * drop the part that makes it safe to act on: where the ACL came from and how current it
 * is. A mirrored verdict is a copy of a decision Slack or Drive owns, and saying so is not
 * decoration — an administrator reading a stale `Allowed` as live access is the failure
 * this component exists to prevent.
 */
export function AccessVerdict({
  state,
  provenance,
  className,
}: {
  state: AccessState
  provenance?: AclProvenanceResponse
  className?: string
}) {
  const verdict = VERDICTS[state]
  const Icon = verdict.icon
  const mirrored = provenance?.authority === "SOURCE"

  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <span className={cn("inline-flex items-center gap-1.5 font-medium", verdict.className)}>
        <Icon className="size-4 shrink-0" aria-hidden />
        {verdict.label}
      </span>

      {mirrored ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <Badge variant="outline" className="gap-1 font-normal">
              <Lock className="size-3 shrink-0" aria-hidden />
              {provenance?.origin || "Source system"}
            </Badge>
          </TooltipTrigger>
          <TooltipContent className="max-w-xs">
            A copy of a permission the source system owns, not its current state.
            {provenance?.generation == null ? null : ` Generation ${provenance.generation}.`}
            {provenance?.capturedAt ? ` Synced ${relativeAge(provenance.capturedAt)}.` : null}
            {provenance?.expired ? " This copy is past its validity." : null}
          </TooltipContent>
        </Tooltip>
      ) : null}

      {provenance?.expired ? (
        <span className="text-xs text-muted-foreground">needs a re-sync</span>
      ) : null}
    </span>
  )
}

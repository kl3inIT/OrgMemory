import {
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  LoaderCircle,
  ShieldCheck,
} from "lucide-react"

import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"

type ReceiptStatus = "running" | "complete" | "failed"

/**
 * A compact, sanitized Assistant tool receipt. It is intentionally
 * closed-world: this renders only server-issued action metadata, never raw
 * variables, payloads, provider requests, or tool arguments.
 */
export function AssistantActionReceipt({
  action,
  status,
  summary,
  traceId,
  releaseDigest,
}: {
  action: string
  status: ReceiptStatus
  summary: string
  traceId?: string
  releaseDigest?: string
}) {
  const Icon =
    status === "running" ? LoaderCircle : status === "complete" ? CheckCircle2 : CircleAlert
  const tone =
    status === "complete"
      ? "text-status-success-content"
      : status === "failed"
        ? "text-status-danger-content"
        : "text-content-secondary"

  return (
    <Collapsible className="rounded-xl border border-border-default bg-surface-subtle">
      <CollapsibleTrigger className="group flex w-full items-center gap-3 px-4 py-3 text-left outline-none focus-visible:ring-2 focus-visible:ring-focus-ring">
        <span className={`grid size-8 shrink-0 place-items-center rounded-full bg-surface-raised ${tone}`}>
          <Icon
            className={`size-4 ${status === "running" ? "animate-spin" : ""}`}
            aria-hidden="true"
          />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-label text-content-primary">{action}</span>
          <span className="block truncate text-metadata text-content-secondary">{summary}</span>
        </span>
        <ChevronDown
          className="size-4 text-content-muted transition-transform group-data-[state=open]:rotate-180"
          aria-hidden="true"
        />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="grid gap-2 border-t border-border-subtle px-4 py-3 text-metadata">
          <div className="flex items-center gap-2 text-content-secondary">
            <ShieldCheck className="size-3.5" aria-hidden="true" />
            Server-authorized, sanitized receipt
          </div>
          <dl className="grid gap-1 font-mono text-content-muted">
            <div className="flex justify-between gap-4">
              <dt>trace</dt>
              <dd>{traceId?.slice(0, 12) ?? "pending"}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>release</dt>
              <dd>{releaseDigest?.slice(0, 16) ?? "not returned"}</dd>
            </div>
          </dl>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

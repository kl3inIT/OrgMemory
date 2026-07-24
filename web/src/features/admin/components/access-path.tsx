import { Building2, FileText, FolderClosed, User, Users } from "lucide-react"

import type { AccessBlockResponse, AccessStepResponse } from "@/lib/hey-api/types.gen"

const STEP_ICONS = {
  user: User,
  organization: Building2,
  organizational_unit: Users,
  group: Users,
  role: Users,
  knowledge_space: FolderClosed,
  knowledge_asset: FileText,
} as const

const KIND_LABELS = {
  DIRECT: "assigned directly",
  COMPUTED: "rewritten to",
  INHERITED: "inherited from",
} as const

function objectType(reference: string) {
  const colon = reference.indexOf(":")
  return colon < 0 ? reference : reference.slice(0, colon)
}

function objectLabel(reference: string) {
  const colon = reference.indexOf(":")
  return colon < 0 ? reference : reference.slice(colon + 1)
}

/**
 * A granted decision is a chain, not a tree: the engine short-circuits a union, so exactly
 * one derivation is decisive. Drawing branches would suggest the answer depended on all of
 * them.
 */
export function AccessPath({ path }: { path: AccessStepResponse[] }) {
  if (path.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Access is granted, but the derivation could not be read from the authorization engine.
      </p>
    )
  }

  return (
    <ol className="relative">
      {path.map((step, index) => {
        const type = objectType(step.object ?? "")
        const Icon = STEP_ICONS[type as keyof typeof STEP_ICONS] ?? FolderClosed
        const last = index === path.length - 1
        return (
          <li key={`${step.object}#${step.relation}`} className="relative flex gap-3 pb-6 last:pb-0">
            {last ? null : (
              <span
                aria-hidden
                className="absolute left-[11px] top-6 h-[calc(100%-1.5rem)] w-px bg-border"
              />
            )}
            <span className="relative z-10 mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full border bg-background">
              <Icon className="size-3.5 text-muted-foreground" aria-hidden />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{objectLabel(step.object ?? "")}</p>
              <p className="text-xs text-muted-foreground">
                {type.replaceAll("_", " ")} · {step.relation}{" "}
                <span className="opacity-70">
                  ({KIND_LABELS[step.kind as keyof typeof KIND_LABELS] ?? step.kind})
                </span>
              </p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}

/**
 * A refusal is a flat list of what was tried. An explicit deny is separated from a missing
 * relationship because they call for different fixes, and where the source system owns the
 * ACL only one of them can be fixed in OrgMemory at all.
 */
export function AccessDenied({ blockedBy }: { blockedBy: AccessBlockResponse[] }) {
  if (blockedBy.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Nothing grants this permission, and the authorization engine offered no branches to
        report.
      </p>
    )
  }

  return (
    <ul className="space-y-2">
      {blockedBy.map((block) => {
        const denied = block.kind === "EXPLICIT_DENY"
        return (
          <li key={block.branch} className="flex gap-3 text-sm">
            <span
              aria-hidden
              className={
                denied ? "font-mono text-destructive" : "font-mono text-muted-foreground"
              }
            >
              {denied ? "⛔" : "✗"}
            </span>
            <div className="min-w-0">
              <p className={denied ? "font-medium text-destructive" : "font-medium"}>
                {block.branch}
              </p>
              {block.detail ? (
                <p className="text-xs text-muted-foreground">{block.detail}</p>
              ) : null}
              {denied ? (
                <p className="text-xs text-destructive">
                  Refused deliberately — granting a relationship will not override it.
                </p>
              ) : null}
            </div>
          </li>
        )
      })}
    </ul>
  )
}

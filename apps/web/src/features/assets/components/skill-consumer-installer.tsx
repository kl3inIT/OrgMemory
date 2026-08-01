import { Bot, ChevronDown, CircleAlert, FolderOutput } from "lucide-react"
import { useState } from "react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { buildSkillInstallHandoff } from "@/features/assets/agent-handoff/skill-agent-handoffs"
import {
  getSkillConsumer,
  SKILL_CONSUMERS,
  skillConsumerTarget,
  type SkillConsumerId,
} from "@/features/assets/agent-handoff/skill-consumers"
import { AgentHandoffPanel } from "@/features/assets/components/agent-handoff-panel"

export function SkillConsumerInstaller({ reference }: { reference: string }) {
  const [selectedId, setSelectedId] = useState<SkillConsumerId | null>(null)
  const selected = selectedId ? getSkillConsumer(selectedId) : null
  const handoff = selected ? buildSkillInstallHandoff(reference, selected) : null

  return (
    <>
      <div className="flex shrink-0 flex-col items-start gap-1 sm:items-end">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button aria-label="Install with">
              Install with
              <ChevronDown aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-72">
            <DropdownMenuLabel>Choose a coding agent</DropdownMenuLabel>
            {SKILL_CONSUMERS.map((consumer) => (
              <DropdownMenuItem
                key={consumer.id}
                className="items-start py-2"
                onSelect={() => setSelectedId(consumer.id)}
              >
                <Bot className="mt-0.5" aria-hidden="true" />
                <span className="min-w-0">
                  <span className="block font-medium text-content-primary">{consumer.label}</span>
                  <span className="block font-mono text-metadata text-content-muted">
                    {consumer.projectDirectory}/&lt;skill&gt;
                  </span>
                </span>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
        <span className="text-metadata text-content-muted">Runtime behavior not certified</span>
      </div>

      <Dialog
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null)
        }}
      >
        {selected && handoff ? (
          <DialogContent className="max-h-[min(90vh,52rem)] overflow-y-auto sm:max-w-3xl">
            <DialogHeader className="pr-8">
              <div className="flex flex-wrap items-center gap-2">
                <DialogTitle>Install with {selected.label}</DialogTitle>
                <Badge variant="outline" className="border-status-success-border">
                  Install supported
                </Badge>
              </div>
              <DialogDescription>
                Install the exact release <span className="font-mono">{reference}</span> into the
                current project.
              </DialogDescription>
            </DialogHeader>

            <div className="flex items-start gap-3 rounded-lg border border-border-subtle bg-surface-subtle px-4 py-3">
              <FolderOutput className="mt-0.5 size-4 shrink-0 text-content-muted" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-label text-content-primary">Project-local target</p>
                <code className="mt-1 block overflow-x-auto font-mono text-supporting text-content-secondary">
                  {skillConsumerTarget(selected, reference)}
                </code>
              </div>
            </div>

            <Alert className="border-status-warning-border bg-status-warning-surface text-status-warning-content">
              <CircleAlert aria-hidden="true" />
              <AlertTitle>Runtime behavior not certified</AlertTitle>
              <AlertDescription className="text-status-warning-content/90">
                Installation support does not mean OrgMemory has certified how {selected.label}{" "}
                interprets or executes this Skill.
              </AlertDescription>
            </Alert>

            <AgentHandoffPanel handoff={handoff} variant="embedded" />
          </DialogContent>
        ) : null}
      </Dialog>
    </>
  )
}

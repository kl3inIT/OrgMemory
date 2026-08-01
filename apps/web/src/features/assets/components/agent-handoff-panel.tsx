import { Bot, CheckCircle2, LockKeyhole, Terminal } from "lucide-react"

import { CopyButton } from "@/components/patterns/copy-button"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { AgentHandoff } from "@/features/assets/agent-handoff/agent-handoff"

export function AgentHandoffPanel({
  handoff,
  variant = "card",
}: {
  handoff: AgentHandoff
  variant?: "card" | "embedded"
}) {
  const defaultTab = handoff.promptTemplate ? "agent" : "cli"
  const content = (
    <>
      <Tabs defaultValue={defaultTab} className="gap-4">
        <TabsList aria-label="Skill handoff method" className="h-10">
          {handoff.promptTemplate ? (
            <TabsTrigger value="agent" className="px-4">
              Use your agent
            </TabsTrigger>
          ) : null}
          <TabsTrigger value="cli" className="px-4">
            Use CLI
          </TabsTrigger>
        </TabsList>

        {handoff.promptTemplate ? (
          <TabsContent value="agent">
            <CopyBlock
              label="Agent prompt"
              value={handoff.promptTemplate}
              copyLabel="Copy agent prompt"
            />
          </TabsContent>
        ) : null}

        <TabsContent value="cli">
          <CopyBlock
            label="OrgMemory CLI"
            value={handoff.cliCommand}
            copyLabel="Copy OrgMemory CLI command"
            singleLine
          />
        </TabsContent>
      </Tabs>

      <Alert className="border-status-warning-border bg-status-warning-surface text-status-warning-content">
        <LockKeyhole aria-hidden="true" />
        <AlertTitle>Confirmation boundary</AlertTitle>
        <AlertDescription className="text-status-warning-content/90">
          {handoff.confirmationBoundary}
        </AlertDescription>
      </Alert>

      <div className="grid gap-4 border-t border-border-subtle pt-4 md:grid-cols-2">
        <div>
          <h3 className="text-label text-content-primary">Before you start</h3>
          <ul className="mt-2 space-y-1 text-supporting text-content-secondary">
            {handoff.prerequisites.map((item) => (
              <li key={item} className="flex gap-2">
                <CheckCircle2
                  className="mt-0.5 size-3.5 shrink-0 text-status-success-content"
                  aria-hidden="true"
                />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="text-label text-content-primary">After completion</h3>
          <p className="mt-2 text-supporting leading-5 text-content-secondary">
            {handoff.completionNote}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2" aria-label="Requested scopes">
            <span className="text-metadata text-content-muted">CLI requests</span>
            {handoff.requiredScopes.map((scope) => (
              <Badge key={scope} variant="outline" className="font-mono font-normal">
                {scope}
              </Badge>
            ))}
          </div>
        </div>
      </div>
    </>
  )

  if (variant === "embedded") {
    return <div className="min-w-0 space-y-4">{content}</div>
  }

  return (
    <Card className="w-full min-w-0 max-w-full gap-4 overflow-hidden bg-surface-raised shadow-none">
      <CardHeader className="min-w-0 gap-1 px-5 sm:px-6">
        <div className="flex items-center gap-2">
          <Bot className="size-4 text-content-muted" aria-hidden="true" />
          <CardTitle>{handoff.title}</CardTitle>
        </div>
        <p className="min-w-0 text-supporting leading-5 break-words text-content-secondary">
          Copy the instructions into a local agent, or run the official CLI command yourself.
        </p>
      </CardHeader>

      <CardContent className="min-w-0 space-y-4 px-5 sm:px-6">{content}</CardContent>
    </Card>
  )
}

function CopyBlock({
  label,
  value,
  copyLabel,
  singleLine = false,
}: {
  label: string
  value: string
  copyLabel: string
  singleLine?: boolean
}) {
  return (
    <div className="min-w-0 rounded-lg border border-border-subtle bg-surface-subtle p-3">
      <div className="mb-2 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-label text-content-primary">
          <Terminal className="size-4" aria-hidden="true" />
          {label}
        </div>
        <CopyButton value={value} label={copyLabel} toastLabel={label} variant="outline" />
      </div>
      <code
        className={
          singleLine
            ? "block overflow-x-auto whitespace-nowrap font-mono text-supporting text-content-secondary"
            : "block max-h-48 overflow-auto whitespace-pre-wrap font-mono text-supporting leading-5 text-content-secondary"
        }
      >
        {value}
      </code>
    </div>
  )
}

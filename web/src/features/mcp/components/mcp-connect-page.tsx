import { Copy, ExternalLink, LockKeyhole, Plug } from "lucide-react"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

const MCP_ENDPOINT = "https://om.kl3in.tech/mcp"
const CLAUDE_CODE_COMMAND = "claude mcp add --transport http orgmemory https://om.kl3in.tech/mcp"
const CODEX_ADD_COMMAND =
  "codex mcp add orgmemory --url https://om.kl3in.tech/mcp --oauth-resource https://om.kl3in.tech/mcp"
const CODEX_LOGIN_COMMAND = "codex mcp login orgmemory --scopes assets:read"

export function McpConnectPage() {
  return (
    <PageLayout.Root variant="standard">
      <PageLayout.Header
        title="Connect AI clients"
        description="Use released OrgMemory Assets from your preferred assistant without copying content or credentials between products."
        icon={<Plug className="size-5" aria-hidden="true" />}
      />

      <PageLayout.Body>
        <Card className="gap-4">
          <CardHeader>
            <CardTitle>OrgMemory MCP</CardTitle>
            <CardDescription>
              Read-only Streamable HTTP with OAuth 2.1 for Claude, Codex, and compatible MCP
              clients.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <CopyField label="Server URL" value={MCP_ENDPOINT} />
          </CardContent>
        </Card>

        <Tabs defaultValue="claude" className="gap-5">
          <TabsList aria-label="MCP clients" className="h-10">
            <TabsTrigger value="claude" className="px-4">
              Claude
            </TabsTrigger>
            <TabsTrigger value="codex" className="px-4">
              Codex
            </TabsTrigger>
            <TabsTrigger value="other" className="px-4">
              Other clients
            </TabsTrigger>
          </TabsList>

        <TabsContent value="claude">
          <ClientCard
            title="Connect Claude"
            description="Claude handles client registration and opens OrgMemory sign-in when required."
          >
            <SetupStep number={1} title="Claude Web or Desktop">
              Open <strong>Settings → Connectors</strong>, add a custom connector, and paste the
              server URL above. Choose <strong>Connect</strong> and approve the read-only access
              request.
            </SetupStep>
            <SetupStep number={2} title="Claude Code">
              Run this once, then use <code>/mcp</code> inside Claude Code to sign in and inspect
              the connection.
              <CommandBlock command={CLAUDE_CODE_COMMAND} />
            </SetupStep>
            <Button variant="outline" asChild>
              <a
                href="https://support.anthropic.com/en/articles/11503834-building-custom-connectors-via-remote-mcp-servers"
                target="_blank"
                rel="noreferrer"
              >
                Claude connector guide
                <ExternalLink aria-hidden="true" />
              </a>
            </Button>
          </ClientCard>
          </TabsContent>

        <TabsContent value="codex">
          <ClientCard
            title="Connect Codex"
            description="The same MCP configuration is shared by Codex CLI, IDE, and desktop."
          >
            <SetupStep number={1} title="Add the server">
              <CommandBlock command={CODEX_ADD_COMMAND} />
            </SetupStep>
            <SetupStep number={2} title="Sign in">
              Codex opens your browser for OrgMemory consent. The token remains in the client
              credential store.
              <CommandBlock command={CODEX_LOGIN_COMMAND} />
            </SetupStep>
          </ClientCard>
          </TabsContent>

        <TabsContent value="other">
          <ClientCard
            title="Connect another MCP client"
            description="Use the canonical URL; no shared client secret is required for public clients."
          >
            <SetupStep number={1} title="Transport">
              Select <strong>Streamable HTTP</strong> and enter the server URL.
            </SetupStep>
            <SetupStep number={2} title="Authorization">
              Use OAuth 2.1 Authorization Code with PKCE. OrgMemory uses restricted Dynamic Client
              Registration, so public clients do not need a shared client secret.
            </SetupStep>
            <SetupStep number={3} title="Scope">
              Request <code>assets:read</code>. OrgMemory still checks your live access to every
              Asset, Pack item, and release.
            </SetupStep>
            <p className="rounded-lg border border-border-subtle bg-surface-subtle p-3 text-sm text-content-secondary">
              Loopback callbacks are supported for local clients. A hosted client with another
              callback domain needs an administrator to approve that domain first.
            </p>
          </ClientCard>
          </TabsContent>
        </Tabs>

        <div className="flex items-start gap-2 text-sm leading-6 text-content-muted">
          <LockKeyhole className="mt-1 size-4 shrink-0" aria-hidden="true" />
          <p>
            OrgMemory checks your current permissions on every request. The connector can search
            and render released Assets, but cannot publish or modify them.
          </p>
        </div>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

function ClientCard({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children: React.ReactNode
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">{children}</CardContent>
    </Card>
  )
}

function SetupStep({
  number,
  title,
  children,
}: {
  number: number
  title: string
  children: React.ReactNode
}) {
  return (
    <div className="grid grid-cols-[2rem_1fr] gap-3">
      <span className="grid size-8 place-items-center rounded-full border border-border-subtle bg-surface-subtle text-sm font-semibold text-content-primary">
        {number}
      </span>
      <div className="min-w-0 space-y-2">
        <h2 className="text-sm font-semibold text-content-primary">{title}</h2>
        <div className="space-y-2 text-sm leading-6 text-content-secondary">{children}</div>
      </div>
    </div>
  )
}

function CopyField({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-2">
      <p className="text-xs font-medium uppercase tracking-wide text-content-tertiary">{label}</p>
      <div className="flex min-w-0 items-center gap-2 rounded-lg border border-border-subtle bg-surface-subtle p-2 pl-3">
        <code className="min-w-0 flex-1 truncate text-sm text-content-primary">{value}</code>
        <CopyButton value={value} label={`Copy ${label}`} />
      </div>
    </div>
  )
}

function CommandBlock({ command }: { command: string }) {
  return (
    <div className="flex min-w-0 items-start gap-2 rounded-lg border border-border-subtle bg-surface-subtle p-2 pl-3">
      <code className="min-w-0 flex-1 overflow-x-auto py-1 font-mono text-xs text-content-primary">
        {command}
      </code>
      <CopyButton value={command} label="Copy command" />
    </div>
  )
}

function CopyButton({ value, label }: { value: string; label: string }) {
  return (
    <Button
      type="button"
      size="icon-sm"
      variant="ghost"
      aria-label={label}
      onClick={() =>
        void navigator.clipboard
          .writeText(value)
          .then(() => toast.success("Copied"))
          .catch(() => toast.error("Could not copy"))
      }
    >
      <Copy aria-hidden="true" />
    </Button>
  )
}

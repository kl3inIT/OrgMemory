import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Copy, KeyRound, Plus, RefreshCw, ShieldAlert, Trash2 } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  invalidateAdminData,
  provisioningConnectionsQueryOptions,
  provisioningCredentialsQueryOptions,
} from "@/features/admin/admin-queries"
import { AdminEmpty, AdminPage } from "@/features/admin/components/admin-page"
import {
  createProvisioningConnectionMutation,
  issueProvisioningCredentialMutation,
  revokeProvisioningCredentialMutation,
  rotateProvisioningCredentialMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type {
  ConnectionResponse,
  CredentialResponse,
  IssuedCredentialResponse,
} from "@/lib/hey-api"

const PROVIDERS = [
  { value: "MICROSOFT_ENTRA", label: "Microsoft Entra ID" },
  { value: "OKTA", label: "Okta" },
  { value: "GENERIC_SCIM", label: "Generic SCIM 2.0" },
] as const

type ProviderProfile = (typeof PROVIDERS)[number]["value"]

function providerLabel(profile?: string) {
  return PROVIDERS.find((candidate) => candidate.value === profile)?.label ?? profile ?? "SCIM"
}

function formatTimestamp(value?: string) {
  if (!value) return "Never"
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

function CredentialRow({
  connection,
  credential,
  onIssued,
}: {
  connection: ConnectionResponse
  credential: CredentialResponse
  onIssued: (issued: IssuedCredentialResponse) => void
}) {
  const queryClient = useQueryClient()
  const rotate = useMutation(rotateProvisioningCredentialMutation())
  const revoke = useMutation(revokeProvisioningCredentialMutation())
  const revoked = Boolean(credential.revokedAt)

  async function rotateCredential() {
    if (!window.confirm("Rotate this token? The old token will remain valid only during the overlap window.")) {
      return
    }
    try {
      const issued = await rotate.mutateAsync({
        path: { connectionId: connection.id!, credentialId: credential.id! },
        body: { usersScope: true, groupsScope: false },
      })
      onIssued(issued)
      await invalidateAdminData(queryClient)
      toast.success("SCIM token rotated")
    } catch {
      toast.error("The SCIM token could not be rotated")
    }
  }

  async function revokeCredential() {
    if (!window.confirm("Revoke this token immediately? Requests using it will stop working.")) {
      return
    }
    try {
      await revoke.mutateAsync({
        path: { connectionId: connection.id!, credentialId: credential.id! },
      })
      await invalidateAdminData(queryClient)
      toast.success("SCIM token revoked")
    } catch {
      toast.error("The SCIM token could not be revoked")
    }
  }

  return (
    <li className="grid gap-3 px-3 py-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <code className="truncate text-xs font-medium">{credential.publicTokenId}</code>
          <Badge variant={revoked ? "outline" : "secondary"}>{revoked ? "Revoked" : "Active"}</Badge>
        </div>
        <p className="text-xs text-muted-foreground">
          Last used {formatTimestamp(credential.lastUsedAt)} · Created{" "}
          {formatTimestamp(credential.createdAt)}
        </p>
      </div>
      {!revoked ? (
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={rotate.isPending}
            onClick={() => void rotateCredential()}
          >
            <RefreshCw className="size-4" aria-hidden />
            Rotate
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Revoke token ${credential.publicTokenId}`}
            disabled={revoke.isPending}
            onClick={() => void revokeCredential()}
          >
            <Trash2 className="size-4" aria-hidden />
          </Button>
        </div>
      ) : null}
    </li>
  )
}

function ConnectionCard({ connection }: { connection: ConnectionResponse }) {
  const queryClient = useQueryClient()
  const credentials = useQuery(provisioningCredentialsQueryOptions(connection.id!))
  const issue = useMutation(issueProvisioningCredentialMutation())
  const [issued, setIssued] = useState<IssuedCredentialResponse | null>(null)
  const tenantUrl = `${window.location.origin}/scim/v2`

  async function issueCredential() {
    try {
      const result = await issue.mutateAsync({
        path: { connectionId: connection.id! },
        body: { usersScope: true, groupsScope: false },
      })
      setIssued(result)
      await invalidateAdminData(queryClient)
      toast.success("SCIM token created")
    } catch {
      toast.error("The SCIM token could not be created")
    }
  }

  async function copy(value: string, label: string) {
    try {
      await navigator.clipboard.writeText(value)
      toast.success(`${label} copied`)
    } catch {
      toast.error(`${label} could not be copied`)
    }
  }

  return (
    <Card>
      <CardHeader className="gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <CardTitle>{connection.alias}</CardTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            {providerLabel(connection.providerProfile)}
          </p>
        </div>
        <Badge variant="outline">{connection.operationalState ?? "DISABLED"}</Badge>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`tenant-url-${connection.id}`}>Tenant URL</Label>
            <div className="flex gap-2">
              <Input id={`tenant-url-${connection.id}`} value={tenantUrl} readOnly />
              <Button
                variant="outline"
                size="icon"
                aria-label="Copy Tenant URL"
                onClick={() => void copy(tenantUrl, "Tenant URL")}
              >
                <Copy className="size-4" aria-hidden />
              </Button>
            </div>
          </div>
          <dl className="grid grid-cols-2 gap-3 rounded-lg border bg-surface-subtle p-3 text-sm">
            <div>
              <dt className="text-muted-foreground">Users</dt>
              <dd className="font-medium">{connection.usersEnabled ? "Credential scope ready" : "Off"}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Groups</dt>
              <dd className="font-medium">{connection.groupsEnabled ? "Credential scope ready" : "Not in this increment"}</dd>
            </div>
          </dl>
        </div>

        <Alert>
          <ShieldAlert aria-hidden />
          <AlertTitle>Provisioning remains disabled</AlertTitle>
          <AlertDescription>
            Configure and rotate credentials now. User and group mutation stays unavailable until
            the private-beta capability gate is enabled in a later increment.
          </AlertDescription>
        </Alert>

        {issued?.token ? (
          <Alert>
            <KeyRound aria-hidden />
            <AlertTitle>Copy this secret token now</AlertTitle>
            <AlertDescription>
              <p>It is shown once and cannot be recovered after this panel is dismissed.</p>
              <div className="flex w-full gap-2">
                <Input value={issued.token} readOnly aria-label="New SCIM secret token" />
                <Button
                  variant="outline"
                  size="icon"
                  aria-label="Copy secret token"
                  onClick={() => void copy(issued.token!, "Secret token")}
                >
                  <Copy className="size-4" aria-hidden />
                </Button>
              </div>
              <Button size="sm" onClick={() => setIssued(null)}>
                I have copied it
              </Button>
            </AlertDescription>
          </Alert>
        ) : null}

        <Separator />

        <section className="space-y-3" aria-labelledby={`credentials-${connection.id}`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 id={`credentials-${connection.id}`} className="font-medium">
                Bearer tokens
              </h3>
              <p className="text-xs text-muted-foreground">
                Secrets are hashed at rest. Metadata and last use remain auditable.
              </p>
            </div>
            <Button
              size="sm"
              disabled={issue.isPending}
              onClick={() => void issueCredential()}
            >
              <KeyRound className="size-4" aria-hidden />
              Create token
            </Button>
          </div>

          {credentials.isPending ? (
            <Skeleton className="h-20 w-full" />
          ) : credentials.isError ? (
            <p className="text-sm text-destructive">Credential metadata could not be loaded.</p>
          ) : credentials.data?.length ? (
            <ul className="divide-y rounded-lg border">
              {credentials.data.map((credential) => (
                <CredentialRow
                  key={credential.id}
                  connection={connection}
                  credential={credential}
                  onIssued={setIssued}
                />
              ))}
            </ul>
          ) : (
            <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
              No token has been issued for this connection.
            </p>
          )}
        </section>
      </CardContent>
    </Card>
  )
}

export function AdminScimPage() {
  const queryClient = useQueryClient()
  const connections = useQuery(provisioningConnectionsQueryOptions())
  const create = useMutation(createProvisioningConnectionMutation())
  const [alias, setAlias] = useState("")
  const [provider, setProvider] = useState<ProviderProfile>("MICROSOFT_ENTRA")

  async function createConnection() {
    try {
      await create.mutateAsync({ body: { alias: alias.trim(), providerProfile: provider } })
      setAlias("")
      await invalidateAdminData(queryClient)
      toast.success("Disabled SCIM connection created")
    } catch {
      toast.error("The SCIM connection could not be created")
    }
  }

  return (
    <AdminPage
      title="SCIM provisioning"
      description="Connect an external workforce directory without making Keycloak the account authority."
    >
      <Card>
        <CardHeader>
          <CardTitle>New connection</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-3 md:grid-cols-[minmax(240px,1fr)_240px_auto] md:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              void createConnection()
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="scim-alias">Connection name</Label>
              <Input
                id="scim-alias"
                value={alias}
                onChange={(event) => setAlias(event.target.value)}
                placeholder="Workforce directory"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="scim-provider">Provider profile</Label>
              <Select
                value={provider}
                onValueChange={(value: string) => setProvider(value as ProviderProfile)}
              >
                <SelectTrigger id="scim-provider">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PROVIDERS.map((candidate) => (
                    <SelectItem key={candidate.value} value={candidate.value}>
                      {candidate.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button type="submit" disabled={!alias.trim() || create.isPending}>
              <Plus className="size-4" aria-hidden />
              Create disabled connection
            </Button>
          </form>
        </CardContent>
      </Card>

      {connections.isPending ? (
        <div className="grid gap-4">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : connections.isError ? (
        <Alert variant="destructive">
          <AlertTitle>Provisioning connections could not be loaded</AlertTitle>
        </Alert>
      ) : connections.data?.length ? (
        <div className="grid gap-4">
          {connections.data.map((connection) => (
            <ConnectionCard key={connection.id} connection={connection} />
          ))}
        </div>
      ) : (
        <AdminEmpty
          title="No SCIM connection"
          description="Create a disabled connection to prepare Tenant URL and bearer credentials."
        />
      )}
    </AdminPage>
  )
}

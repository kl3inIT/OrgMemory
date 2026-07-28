import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  Bot,
  Cable,
  CheckCircle2,
  CircleAlert,
  Cloud,
  Cpu,
  KeyRound,
  Loader2,
  Network,
  RefreshCw,
  Server,
  Settings2,
  ShieldCheck,
  Sparkles,
} from "lucide-react"
import { type FormEvent, type ReactNode, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { AdminPage } from "@/features/admin/components/admin-page"
import type {
  GatewayResponse,
  ModelRef,
  ProviderPresetResponse,
  RouteResponse,
} from "@/lib/hey-api"
import {
  clearAdminAiRouteMutation,
  createAdminAiGatewayMutation,
  disableAdminAiGatewayMutation,
  listAdminAiGatewaysOptions,
  listAdminAiGatewaysQueryKey,
  listAdminAiProviderPresetsOptions,
  listAdminAiRoutesOptions,
  listAdminAiRoutesQueryKey,
  setAdminAiRouteMutation,
  testAdminAiGatewayMutation,
  testStoredAdminAiGatewayMutation,
  updateAdminAiGatewayMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

const CATEGORY_COPY = {
  DIRECT_PROVIDER: {
    title: "Direct providers",
    description: "Native or first-party model APIs.",
    icon: Cloud,
  },
  GATEWAY_ROUTER: {
    title: "Gateways & routers",
    description: "One endpoint that routes to one or many model vendors.",
    icon: Network,
  },
  SELF_HOSTED_CUSTOM: {
    title: "Self-hosted & custom",
    description: "Operator-allowlisted endpoints inside your own network.",
    icon: Server,
  },
} as const

const WORKLOADS = [
  {
    value: "ASSISTANT_CHAT" as const,
    title: "Default chat model",
    description: "Used by organization conversations unless a governed asset pins another route.",
  },
  {
    value: "PROMPT_EXECUTION" as const,
    title: "Prompt execution",
    description: "Used when released prompt assets run against retrieved organization context.",
  },
]

function providerMark(preset?: ProviderPresetResponse["preset"]): ReactNode {
  switch (preset) {
    case "ANTHROPIC":
      return <Sparkles className="size-4" aria-hidden="true" />
    case "NINE_ROUTER":
    case "OPENROUTER":
    case "LITELLM":
      return <Network className="size-4" aria-hidden="true" />
    case "OLLAMA":
    case "OPENAI_COMPATIBLE":
      return <Server className="size-4" aria-hidden="true" />
    default:
      return <Cpu className="size-4" aria-hidden="true" />
  }
}

function safeKey(preset: ProviderPresetResponse) {
  const base = (preset.preset ?? "gateway").toLocaleLowerCase().replaceAll("_", "-")
  return `${base}-${crypto.randomUUID().slice(0, 8)}`
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  return fallback
}

export function AdminLanguageModelsPage() {
  const providers = useQuery(listAdminAiProviderPresetsOptions())
  const gateways = useQuery(listAdminAiGatewaysOptions())
  const routes = useQuery(listAdminAiRoutesOptions())
  const [connecting, setConnecting] = useState<ProviderPresetResponse>()
  const [editing, setEditing] = useState<GatewayResponse>()

  if (providers.isPending || gateways.isPending || routes.isPending) {
    return <LoadingState label="Loading language models" className="min-h-full flex-1" />
  }

  const failed = providers.error ?? gateways.error ?? routes.error
  if (failed) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Language model settings could not be loaded"
          description="Organization AI administration permission is required."
          error={failed}
          onRetry={() => Promise.all([providers.refetch(), gateways.refetch(), routes.refetch()])}
        />
      </div>
    )
  }

  const profiles = gateways.data ?? []
  const presets = providers.data ?? []
  const effectiveRoutes = routes.data ?? []

  return (
    <AdminPage
      title="Language Models"
      description="Connect model providers and choose explicit organization routes. A failed route never silently falls back."
      icon={<Bot className="size-5" aria-hidden="true" />}
    >
      <RouteSettings gateways={profiles} routes={effectiveRoutes} />

      {profiles.length > 0 ? (
        <section className="space-y-3" aria-labelledby="connected-gateways">
          <div>
            <h2 id="connected-gateways" className="text-lg font-semibold">Connected gateways</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Credentials remain encrypted server-side and are never returned to this browser.
            </p>
          </div>
          <div className="grid gap-3 lg:grid-cols-2">
            {profiles.map((profile) => (
              <button
                key={profile.id}
                type="button"
                className="group flex min-h-24 items-center gap-4 rounded-xl border border-border-subtle bg-card p-5 text-left shadow-xs outline-none transition-[border-color,transform,box-shadow] hover:-translate-y-0.5 hover:border-control-border hover:shadow-sm focus-visible:ring-2 focus-visible:ring-focus-ring"
                onClick={() => setEditing(profile)}
              >
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-surface-subtle text-content-secondary">
                  {providerMark(profile.preset)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="truncate font-semibold">{profile.displayName}</span>
                    {profile.credentialSet ? <Badge variant="success">Ready</Badge> : <Badge variant="warning">Key required</Badge>}
                  </span>
                  <span className="mt-1 block truncate text-sm text-muted-foreground">{profile.baseUrl}</span>
                </span>
                <Settings2 className="size-4 text-muted-foreground transition-transform group-hover:rotate-45" aria-hidden="true" />
              </button>
            ))}
          </div>
        </section>
      ) : (
        <Alert>
          <Cable aria-hidden="true" />
          <AlertTitle>No organization gateway connected</AlertTitle>
          <AlertDescription>
            Deployment defaults remain visible above. Connect a provider below before creating an organization override.
          </AlertDescription>
        </Alert>
      )}

      <div className="h-px bg-border-subtle" />

      <section className="space-y-7" aria-labelledby="available-providers">
        <div>
          <h2 id="available-providers" className="text-lg font-semibold">Add provider</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Only protocols implemented and verified by this deployment are offered.
          </p>
        </div>
        {Object.entries(CATEGORY_COPY).map(([category, copy]) => {
          const categoryProviders = presets.filter((provider) => provider.category === category)
          if (categoryProviders.length === 0) return null
          const CategoryIcon = copy.icon
          return (
            <div key={category} className="space-y-3">
              <div className="flex items-start gap-3">
                <CategoryIcon className="mt-0.5 size-4 text-muted-foreground" aria-hidden="true" />
                <div>
                  <h3 className="text-sm font-semibold">{copy.title}</h3>
                  <p className="text-xs text-muted-foreground">{copy.description}</p>
                </div>
              </div>
              <div className="grid gap-3 lg:grid-cols-2">
                {categoryProviders.map((provider) => (
                  <button
                    key={provider.preset}
                    type="button"
                    className="group flex min-h-24 items-center gap-4 rounded-xl border border-border-subtle bg-surface-raised p-5 text-left outline-none transition-colors hover:border-control-border hover:bg-surface-subtle focus-visible:ring-2 focus-visible:ring-focus-ring"
                    onClick={() => setConnecting(provider)}
                  >
                    <span className="grid size-10 shrink-0 place-items-center rounded-lg border border-border-subtle bg-card">
                      {providerMark(provider.preset)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold">{provider.displayName}</span>
                      <span className="mt-1 block text-sm text-muted-foreground">{provider.vendorName}</span>
                    </span>
                    <span className="flex items-center gap-2 text-sm font-medium text-muted-foreground group-hover:text-foreground">
                      Connect <Cable className="size-4" aria-hidden="true" />
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )
        })}
      </section>

      <ConnectGatewayDialog provider={connecting} onOpenChange={(open) => !open && setConnecting(undefined)} />
      <GatewaySettingsDialog gateway={editing} onOpenChange={(open) => !open && setEditing(undefined)} />
    </AdminPage>
  )
}

function RouteSettings({ gateways, routes }: { gateways: GatewayResponse[]; routes: RouteResponse[] }) {
  const queryClient = useQueryClient()
  const setRoute = useMutation({
    ...setAdminAiRouteMutation(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: listAdminAiRoutesQueryKey() })
      toast.success("Model route updated.")
    },
    onError: (error) => toast.error(apiErrorMessage(error, "The route could not be updated.")),
  })
  const clearRoute = useMutation({
    ...clearAdminAiRouteMutation(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: listAdminAiRoutesQueryKey() })
      toast.success("Deployment default restored.")
    },
    onError: (error) => toast.error(apiErrorMessage(error, "The deployment default could not be restored.")),
  })
  const probe = useMutation(testStoredAdminAiGatewayMutation())
  const [selectedGateway, setSelectedGateway] = useState<Record<string, string>>({})
  const [models, setModels] = useState<Record<string, ModelRef[]>>({})
  const [modelIds, setModelIds] = useState<Record<string, string>>({})

  useEffect(() => {
    const nextGateways: Record<string, string> = {}
    const nextModels: Record<string, string> = {}
    for (const workload of WORKLOADS) {
      const route = routes.find((candidate) => candidate.workload === workload.value)
      if (route?.gatewayProfileId) nextGateways[workload.value] = route.gatewayProfileId
      if (route?.modelId) nextModels[workload.value] = route.modelId
    }
    setSelectedGateway(nextGateways)
    setModelIds(nextModels)
  }, [routes])

  const defaultRoute = routes.find((route) => route.workload === "ASSISTANT_CHAT")

  async function discover(workload: (typeof WORKLOADS)[number]["value"]) {
    const profileId = selectedGateway[workload]
    if (!profileId) return
    try {
      const result = await probe.mutateAsync({ path: { profileId } })
      if (!result.authenticated) {
        toast.error(`Connection test failed: ${result.errorCode ?? "provider_unavailable"}`)
        return
      }
      setModels((current) => ({ ...current, [workload]: result.models ?? [] }))
      const first = result.models?.[0]?.id
      if (first && !modelIds[workload]) {
        setModelIds((current) => ({ ...current, [workload]: first }))
      }
      toast.success(result.models?.length ? `${result.models.length} models loaded.` : "Connected. Enter a model ID manually.")
    } catch (error) {
      toast.error(apiErrorMessage(error, "Connection test failed."))
    }
  }

  return (
    <Card className="overflow-hidden border-border-subtle bg-card py-0">
      <CardHeader className="border-b border-border-subtle bg-surface-raised py-5">
        <div className="flex items-center gap-3">
          <span className="grid size-9 place-items-center rounded-lg bg-action-primary text-action-primary-foreground">
            <Sparkles className="size-4" aria-hidden="true" />
          </span>
          <div>
            <CardTitle>Organization model routes</CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">
              Current default: {defaultRoute?.modelId ?? "Not configured"} · {defaultRoute?.source === "ORGANIZATION_OVERRIDE" ? "organization override" : "deployment default"}
            </p>
          </div>
        </div>
      </CardHeader>
      <CardContent className="divide-y divide-border-subtle p-0">
        {WORKLOADS.map((workload) => {
          const route = routes.find((candidate) => candidate.workload === workload.value)
          const availableModels = models[workload.value] ?? []
          const profileId = selectedGateway[workload.value] ?? ""
          const canSave = Boolean(profileId && modelIds[workload.value]?.trim())
          return (
            <div key={workload.value} className="grid gap-4 p-5 xl:grid-cols-[minmax(15rem,1fr)_minmax(16rem,0.9fr)_minmax(16rem,1fr)_auto] xl:items-end">
              <div className="self-center">
                <p className="font-medium">{workload.title}</p>
                <p className="mt-1 max-w-lg text-sm leading-5 text-muted-foreground">{workload.description}</p>
                {route?.source === "DEPLOYMENT_DEFAULT" ? <Badge variant="muted" className="mt-2">Deployment default</Badge> : <Badge variant="success" className="mt-2">Organization override</Badge>}
              </div>
              <div className="space-y-2">
                <Label htmlFor={`${workload.value}-gateway`}>Gateway</Label>
                <Select
                  value={profileId}
                  onValueChange={(value) => setSelectedGateway((current) => ({ ...current, [workload.value]: value }))}
                >
                  <SelectTrigger id={`${workload.value}-gateway`}>
                    <SelectValue placeholder="Choose a connected gateway" />
                  </SelectTrigger>
                  <SelectContent>
                    {gateways.filter((gateway) => gateway.enabled && gateway.credentialSet).map((gateway) => (
                      <SelectItem key={gateway.id} value={gateway.id!}>{gateway.displayName}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <Label htmlFor={`${workload.value}-model`}>Model ID</Label>
                  <Button type="button" variant="ghost" size="xs" disabled={!profileId || probe.isPending} onClick={() => discover(workload.value)}>
                    {probe.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <RefreshCw aria-hidden="true" />}
                    Test & load
                  </Button>
                </div>
                {availableModels.length > 0 ? (
                  <Select
                    value={modelIds[workload.value] ?? ""}
                    onValueChange={(value) => setModelIds((current) => ({ ...current, [workload.value]: value }))}
                  >
                    <SelectTrigger id={`${workload.value}-model`}><SelectValue placeholder="Choose a model" /></SelectTrigger>
                    <SelectContent>
                      {availableModels.map((model) => <SelectItem key={model.id} value={model.id!}>{model.displayName || model.id}</SelectItem>)}
                    </SelectContent>
                  </Select>
                ) : (
                  <Input
                    id={`${workload.value}-model`}
                    value={modelIds[workload.value] ?? ""}
                    placeholder={route?.modelId ?? "provider/model-id"}
                    onChange={(event) => setModelIds((current) => ({ ...current, [workload.value]: event.target.value }))}
                  />
                )}
              </div>
              <div className="flex gap-2 xl:flex-col">
                <Button
                  type="button"
                  disabled={!canSave || setRoute.isPending}
                  onClick={() => setRoute.mutate({
                    path: { workload: workload.value },
                    body: { gatewayProfileId: profileId, modelId: modelIds[workload.value].trim() },
                  })}
                >
                  Save route
                </Button>
                {route?.source === "ORGANIZATION_OVERRIDE" ? (
                  <Button
                    type="button"
                    variant="ghost"
                    disabled={clearRoute.isPending}
                    onClick={() => clearRoute.mutate({ path: { workload: workload.value } })}
                  >
                    Use deployment default
                  </Button>
                ) : null}
              </div>
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}

function ConnectGatewayDialog({
  provider,
  onOpenChange,
}: {
  provider?: ProviderPresetResponse
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState("")
  const [baseUrl, setBaseUrl] = useState("")
  const [credential, setCredential] = useState("")
  const [timeout, setTimeoutValue] = useState("60")
  const [result, setResult] = useState<{ authenticated: boolean; models: ModelRef[]; errorCode?: string }>()
  const test = useMutation(testAdminAiGatewayMutation())
  const create = useMutation(createAdminAiGatewayMutation())

  useEffect(() => {
    if (!provider) return
    setDisplayName(provider.displayName ?? "")
    setBaseUrl(provider.defaultBaseUrl ?? "")
    setCredential("")
    setTimeoutValue("60")
    setResult(undefined)
  }, [provider])

  async function testConnection() {
    if (!provider) return
    try {
      const response = await test.mutateAsync({
        body: {
          preset: provider.preset,
          protocol: provider.protocol,
          baseUrl,
          requestTimeoutSeconds: Number(timeout),
          credential,
        },
      })
      setResult({ authenticated: response.authenticated ?? false, models: response.models ?? [], errorCode: response.errorCode })
    } catch (error) {
      toast.error(apiErrorMessage(error, "Connection test failed."))
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!provider) return
    try {
      await create.mutateAsync({
        body: {
          gatewayKey: safeKey(provider),
          displayName,
          preset: provider.preset,
          category: provider.category,
          protocol: provider.protocol,
          baseUrl,
          requestTimeoutSeconds: Number(timeout),
          credential,
        },
      })
      await queryClient.invalidateQueries({ queryKey: listAdminAiGatewaysQueryKey() })
      toast.success(`${displayName} connected.`)
      onOpenChange(false)
    } catch (error) {
      toast.error(apiErrorMessage(error, "Provider could not be connected."))
    }
  }

  return (
    <Dialog open={Boolean(provider)} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <form onSubmit={submit} className="contents">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">{providerMark(provider?.preset)} Connect {provider?.displayName}</DialogTitle>
            <DialogDescription>
              The endpoint is checked against the deployment allowlist before any credential is stored.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <Field label="Connection name" value={displayName} onChange={setDisplayName} required />
            <Field label="Base URL" value={baseUrl} onChange={setBaseUrl} disabled={!provider?.baseUrlEditable} required />
            <Field label="API key" type="password" value={credential} onChange={setCredential} autoComplete="new-password" required />
            <Field label="Request timeout (seconds)" type="number" value={timeout} onChange={setTimeoutValue} min="1" max="300" required />
            {result ? (
              <Alert variant={result.authenticated ? "default" : "destructive"}>
                {result.authenticated ? <CheckCircle2 aria-hidden="true" /> : <CircleAlert aria-hidden="true" />}
                <AlertTitle>{result.authenticated ? "Connection verified" : "Connection rejected"}</AlertTitle>
                <AlertDescription>
                  {result.authenticated
                    ? result.models.length
                      ? `${result.models.length} models were discovered.`
                      : "Authentication succeeded. This endpoint requires a manual model ID."
                    : `Reason: ${result.errorCode ?? "provider_unavailable"}`}
                </AlertDescription>
              </Alert>
            ) : null}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={test.isPending || !credential || !baseUrl} onClick={testConnection}>
              {test.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <ShieldCheck aria-hidden="true" />}
              Test connection
            </Button>
            <Button type="submit" disabled={create.isPending || !displayName || !credential || !baseUrl}>
              {create.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <KeyRound aria-hidden="true" />}
              Store securely
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function GatewaySettingsDialog({
  gateway,
  onOpenChange,
}: {
  gateway?: GatewayResponse
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState("")
  const [baseUrl, setBaseUrl] = useState("")
  const [timeout, setTimeoutValue] = useState("60")
  const [credential, setCredential] = useState("")
  const update = useMutation(updateAdminAiGatewayMutation())
  const test = useMutation(testStoredAdminAiGatewayMutation())
  const disable = useMutation(disableAdminAiGatewayMutation())

  useEffect(() => {
    if (!gateway) return
    setDisplayName(gateway.displayName ?? "")
    setBaseUrl(gateway.baseUrl ?? "")
    setTimeoutValue(String(gateway.requestTimeoutSeconds ?? 60))
    setCredential("")
  }, [gateway])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!gateway?.id) return
    try {
      await update.mutateAsync({
        path: { profileId: gateway.id },
        body: {
          displayName,
          baseUrl,
          requestTimeoutSeconds: Number(timeout),
          credential: credential || undefined,
        },
      })
      await queryClient.invalidateQueries({ queryKey: listAdminAiGatewaysQueryKey() })
      toast.success("Gateway settings updated.")
      onOpenChange(false)
    } catch (error) {
      toast.error(apiErrorMessage(error, "Gateway settings could not be updated."))
    }
  }

  async function testStored() {
    if (!gateway?.id) return
    try {
      const response = await test.mutateAsync({ path: { profileId: gateway.id } })
      if (response.authenticated) {
        toast.success(response.models?.length ? `${response.models.length} models discovered.` : "Connection verified.")
      } else {
        toast.error(`Connection failed: ${response.errorCode ?? "provider_unavailable"}`)
      }
    } catch (error) {
      toast.error(apiErrorMessage(error, "Connection test failed."))
    }
  }

  async function disableGateway() {
    if (!gateway?.id) return
    try {
      await disable.mutateAsync({ path: { profileId: gateway.id } })
      await queryClient.invalidateQueries({ queryKey: listAdminAiGatewaysQueryKey() })
      toast.success("Gateway disabled.")
      onOpenChange(false)
    } catch (error) {
      toast.error(apiErrorMessage(error, "Change active routes before disabling this gateway."))
    }
  }

  return (
    <Dialog open={Boolean(gateway)} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <form onSubmit={submit} className="contents">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2"><Settings2 className="size-4" aria-hidden="true" /> {gateway?.displayName}</DialogTitle>
            <DialogDescription>Update metadata or rotate the credential. The existing secret is never revealed.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <Field label="Connection name" value={displayName} onChange={setDisplayName} required />
            <Field label="Base URL" value={baseUrl} onChange={setBaseUrl} required />
            <Field label="Request timeout (seconds)" type="number" value={timeout} onChange={setTimeoutValue} min="1" max="300" required />
            <Field label="New API key" type="password" value={credential} onChange={setCredential} autoComplete="new-password" placeholder="Leave blank to keep the current key" />
          </div>
          <DialogFooter>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button type="button" variant="destructive" className="sm:mr-auto" disabled={disable.isPending}>
                  Disable gateway
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Disable {gateway?.displayName}?</AlertDialogTitle>
                  <AlertDialogDescription>
                    The server refuses this action while an organization route still uses the gateway.
                    Stored credentials remain encrypted for audit and recovery.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-status-danger-content text-white hover:bg-status-danger-content/90"
                    onClick={disableGateway}
                  >
                    Disable gateway
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
            <Button type="button" variant="outline" disabled={test.isPending || !gateway?.credentialSet} onClick={testStored}>
              {test.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <ShieldCheck aria-hidden="true" />} Test stored key
            </Button>
            <Button type="submit" disabled={update.isPending || !displayName || !baseUrl}>
              Save changes
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function Field({
  label,
  value,
  onChange,
  ...input
}: {
  label: string
  value: string
  onChange: (value: string) => void
} & Omit<React.ComponentProps<typeof Input>, "value" | "onChange">) {
  const id = useMemo(() => `ai-${label.toLocaleLowerCase().replaceAll(/[^a-z0-9]+/g, "-")}`, [label])
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} value={value} onChange={(event) => onChange(event.target.value)} {...input} />
    </div>
  )
}

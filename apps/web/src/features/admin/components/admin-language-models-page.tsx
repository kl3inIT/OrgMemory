import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  Bot,
  Cable,
  CheckCircle2,
  CircleAlert,
  Cloud,
  KeyRound,
  Loader2,
  Network,
  RefreshCw,
  Server,
  Settings2,
  ShieldCheck,
  Sparkles,
} from "lucide-react"
import { type FormEvent, useEffect, useMemo, useState } from "react"
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
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { AdminPage } from "@/features/admin/components/admin-page"
import { ProviderLogo } from "@/features/admin/components/provider-logo"
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
  replaceAdminAssistantModelsMutation,
  setAdminAiRouteMutation,
  testAdminAiGatewayMutation,
  testStoredAdminAiGatewayMutation,
  updateAdminAiGatewayMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import { apiErrorMessage } from "@/lib/api-error"

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
    title: "Answer generation",
    description: "Produces the final governed answer after retrieval and citation assembly.",
  },
  {
    value: "KEYWORD_PLANNING" as const,
    title: "Keyword planning",
    description: "Creates bounded high- and low-level search keywords before retrieval.",
  },
  {
    value: "GRAPH_EXTRACTION" as const,
    title: "Graph extraction",
    description: "Extracts entities and relations for newly enqueued indexing jobs.",
  },
  {
    value: "PROMPT_EXECUTION" as const,
    title: "Prompt execution",
    description: "Used when released prompt assets run against retrieved organization context.",
  },
]

type OpenAiReasoningEffort = NonNullable<RouteResponse["openAiReasoningEffort"]>
type ReasoningSelection = OpenAiReasoningEffort | "PROVIDER_DEFAULT"

const REASONING_OPTIONS: Array<{ value: ReasoningSelection; label: string }> = [
  { value: "PROVIDER_DEFAULT", label: "Provider default" },
  { value: "NONE", label: "None" },
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
  { value: "XHIGH", label: "Extra high" },
  { value: "MAX", label: "Maximum" },
]

function safeKey(preset: ProviderPresetResponse) {
  const base = (preset.preset ?? "gateway").toLowerCase().replaceAll("_", "-")
  return `${base}-${crypto.randomUUID().slice(0, 8)}`
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
                  <ProviderLogo preset={profile.preset} className="size-5" />
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
                      <ProviderLogo preset={provider.preset} className="size-5" />
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
      <GatewaySettingsDialog
        gateway={editing}
        assistantRoute={effectiveRoutes.find((route) => route.workload === "ASSISTANT_CHAT")}
        onOpenChange={(open) => !open && setEditing(undefined)}
      />
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
  const [reasoning, setReasoning] = useState<Record<string, ReasoningSelection>>({})

  useEffect(() => {
    const nextGateways: Record<string, string> = {}
    const nextModels: Record<string, string> = {}
    const nextReasoning: Record<string, ReasoningSelection> = {}
    for (const workload of WORKLOADS) {
      const route = routes.find((candidate) => candidate.workload === workload.value)
      if (route?.gatewayProfileId) nextGateways[workload.value] = route.gatewayProfileId
      if (route?.modelId) nextModels[workload.value] = route.modelId
      nextReasoning[workload.value] = route?.openAiReasoningEffort ?? "PROVIDER_DEFAULT"
    }
    setSelectedGateway(nextGateways)
    setModelIds(nextModels)
    setReasoning(nextReasoning)
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
            <CardTitle>RAG pipeline & prompt routes</CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">
              Answer: {defaultRoute?.modelId ?? "Not configured"} · Keyword and answer changes apply to later requests; Graph changes apply only to later jobs.
            </p>
          </div>
        </div>
      </CardHeader>
      <CardContent className="divide-y divide-border-subtle p-0">
        {WORKLOADS.map((workload) => {
          const route = routes.find((candidate) => candidate.workload === workload.value)
          const availableModels = models[workload.value] ?? []
          const profileId = selectedGateway[workload.value] ?? ""
          const selectedProfile = gateways.find((gateway) => gateway.id === profileId)
          const reasoningSupported = selectedProfile?.supportsOpenAiReasoningEffort === true
          const canSave = Boolean(profileId && modelIds[workload.value]?.trim())
          if (route?.editable === false) {
            return (
              <div key={workload.value} className="grid gap-4 p-5 lg:grid-cols-[minmax(15rem,1fr)_repeat(3,minmax(10rem,0.7fr))] lg:items-center">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium">{workload.title}</p>
                    <Badge variant="muted">Deployment managed</Badge>
                  </div>
                  <p className="mt-1 max-w-lg text-sm leading-5 text-muted-foreground">{workload.description}</p>
                  <p className="mt-2 text-xs leading-5 text-muted-foreground">{route.lifecycleNote}</p>
                </div>
                <ReadOnlyRouteValue label="Gateway" value={route.gatewayKey} />
                <ReadOnlyRouteValue label="Model" value={route.modelId} />
                <ReadOnlyRouteValue label="OpenAI reasoning" value={route.openAiReasoningEffort?.toLowerCase() ?? "provider default"} />
              </div>
            )
          }
          return (
            <div key={workload.value} className="grid gap-4 p-5 2xl:grid-cols-[minmax(14rem,1fr)_minmax(14rem,0.8fr)_minmax(14rem,0.9fr)_minmax(11rem,0.65fr)_auto] 2xl:items-end">
              <div className="self-center">
                <p className="font-medium">{workload.title}</p>
                <p className="mt-1 max-w-lg text-sm leading-5 text-muted-foreground">{workload.description}</p>
                {route?.source === "DEPLOYMENT_DEFAULT" ? <Badge variant="muted" className="mt-2">Deployment default</Badge> : <Badge variant="success" className="mt-2">Organization override</Badge>}
                <p className="mt-2 text-xs leading-5 text-muted-foreground">{route?.lifecycleNote}</p>
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
              <div className="space-y-2">
                <Label htmlFor={`${workload.value}-reasoning`}>OpenAI reasoning</Label>
                <Select
                  value={reasoning[workload.value] ?? "PROVIDER_DEFAULT"}
                  disabled={!reasoningSupported}
                  onValueChange={(value) => setReasoning((current) => ({ ...current, [workload.value]: value as ReasoningSelection }))}
                >
                  <SelectTrigger id={`${workload.value}-reasoning`}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {REASONING_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}
                  </SelectContent>
                </Select>
                {!reasoningSupported ? <p className="text-xs text-muted-foreground">Select a gateway that declares this option; otherwise the provider default is used.</p> : null}
              </div>
              <div className="flex gap-2 2xl:flex-col">
                <Button
                  type="button"
                  disabled={!canSave || setRoute.isPending}
                  onClick={() => setRoute.mutate({
                    path: { workload: workload.value },
                    body: {
                      gatewayProfileId: profileId,
                      modelId: modelIds[workload.value].trim(),
                      openAiReasoningEffort: reasoningSupported && reasoning[workload.value] !== "PROVIDER_DEFAULT"
                        ? reasoning[workload.value] as OpenAiReasoningEffort
                        : undefined,
                    },
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

function ReadOnlyRouteValue({ label, value }: { label: string; value?: string }) {
  return (
    <div className="rounded-lg border border-border-subtle bg-surface-subtle px-3 py-2.5">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 truncate font-mono text-sm">{value || "Not configured"}</p>
    </div>
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
  const [supportsReasoning, setSupportsReasoning] = useState(false)
  const [result, setResult] = useState<{ authenticated: boolean; models: ModelRef[]; errorCode?: string }>()
  const test = useMutation(testAdminAiGatewayMutation())
  const create = useMutation(createAdminAiGatewayMutation())

  useEffect(() => {
    if (!provider) return
    setDisplayName(provider.displayName ?? "")
    setBaseUrl(provider.defaultBaseUrl ?? "")
    setCredential("")
    setTimeoutValue("60")
    setSupportsReasoning(false)
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
    const connectionName = displayName.trim() || provider.displayName || provider.vendorName
    try {
      await create.mutateAsync({
        body: {
          gatewayKey: safeKey(provider),
          displayName: connectionName,
          preset: provider.preset,
          category: provider.category,
          protocol: provider.protocol,
          baseUrl,
          requestTimeoutSeconds: Number(timeout),
          supportsOpenAiReasoningEffort: supportsReasoning,
          credential,
        },
      })
      await queryClient.invalidateQueries({ queryKey: listAdminAiGatewaysQueryKey() })
      toast.success(`${connectionName} connected.`)
      onOpenChange(false)
    } catch (error) {
      toast.error(apiErrorMessage(error, "Provider could not be connected."))
    }
  }

  return (
    <Dialog open={Boolean(provider)} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[min(92vh,58rem)] grid-rows-[auto_minmax(0,1fr)_auto] gap-0 overflow-hidden p-0 sm:max-w-3xl">
        <form onSubmit={submit} className="contents">
          <DialogHeader className="border-b border-border-subtle bg-surface-raised px-6 py-5 pr-14">
            <div className="flex items-start gap-4">
              <span className="grid size-11 shrink-0 place-items-center rounded-xl border border-border-subtle bg-card shadow-xs">
                <ProviderLogo preset={provider?.preset} className="size-6" />
              </span>
              <div className="min-w-0">
                <DialogTitle className="text-xl leading-7">Set up {provider?.displayName}</DialogTitle>
                <DialogDescription className="mt-1 leading-5">
                  Connect to {provider?.vendorName} and verify the models exposed to OrgMemory.
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>
          <div className="min-h-0 overflow-y-auto px-6 py-5">
            <div className="space-y-6">
              <section className="space-y-4" aria-labelledby="provider-credentials-heading">
                <div>
                  <h3 id="provider-credentials-heading" className="font-semibold">Credentials</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Secrets are encrypted server-side and are never returned to this browser.
                  </p>
                </div>
                <Field
                  label="API key"
                  description={`Paste the API key issued by ${provider?.vendorName ?? "the provider"}.`}
                  type="password"
                  value={credential}
                  onChange={setCredential}
                  autoComplete="new-password"
                  required
                />
                {provider?.protocol === "OPENAI_COMPATIBLE" ? (
                  <div className="flex items-start justify-between gap-4 rounded-lg border border-border-subtle bg-surface-subtle px-4 py-3">
                    <div>
                      <Label htmlFor="connect-openai-reasoning">OpenAI reasoning effort</Label>
                      <p className="mt-1 text-xs leading-5 text-muted-foreground">
                        Declare this only when the endpoint accepts the OpenAI reasoning_effort field. Undeclared endpoints fail closed.
                      </p>
                    </div>
                    <Switch id="connect-openai-reasoning" checked={supportsReasoning} onCheckedChange={setSupportsReasoning} />
                  </div>
                ) : null}
                <Field
                  label="Display name"
                  labelSuffix="Optional"
                  description="Use a recognizable name when your organization connects more than one endpoint."
                  value={displayName}
                  onChange={setDisplayName}
                  placeholder={provider?.displayName}
                />
              </section>

              <div className="h-px bg-border-subtle" />

              <section className="space-y-4" aria-labelledby="provider-connection-heading">
                <div>
                  <h3 id="provider-connection-heading" className="font-semibold">Connection</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    The endpoint is checked against the deployment allowlist before the credential is stored.
                  </p>
                </div>
                {provider?.baseUrlEditable ? (
                  <Field
                    label="Base URL"
                    description="Use the OpenAI-compatible API root exposed by this gateway."
                    value={baseUrl}
                    onChange={setBaseUrl}
                    required
                  />
                ) : (
                  <div className="rounded-lg border border-border-subtle bg-surface-subtle px-4 py-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Provider endpoint</p>
                    <p className="mt-1 break-all font-mono text-sm">{baseUrl}</p>
                  </div>
                )}
                <Field
                  label="Request timeout"
                  labelSuffix="Seconds"
                  type="number"
                  value={timeout}
                  onChange={setTimeoutValue}
                  min="1"
                  max="300"
                  required
                />
              </section>

              <section
                className="overflow-hidden rounded-xl border border-border-subtle bg-surface-sunken"
                aria-labelledby="provider-models-heading"
              >
                <div className="flex items-start justify-between gap-4 border-b border-border-subtle px-4 py-3">
                  <div>
                    <h3 id="provider-models-heading" className="font-semibold">Models</h3>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Test the connection to discover models available for explicit organization routes.
                    </p>
                  </div>
                  {result?.authenticated ? <Badge variant="success">Verified</Badge> : <Badge variant="muted">Not tested</Badge>}
                </div>
                <div className="p-3">
                  {!result ? (
                    <div className="grid min-h-20 place-items-center rounded-lg border border-dashed border-border-default px-4 text-center text-sm text-muted-foreground">
                      Models will appear here after a successful connection test.
                    </div>
                  ) : result.authenticated ? (
                    result.models.length > 0 ? (
                      <div className="space-y-2">
                        {result.models.slice(0, 8).map((model) => (
                          <div
                            key={model.id}
                            className="flex items-center gap-3 rounded-lg border border-status-info-border bg-status-info-surface px-3 py-2 text-sm text-status-info-content"
                          >
                            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
                            <span className="min-w-0 truncate font-medium">{model.displayName || model.id}</span>
                          </div>
                        ))}
                        {result.models.length > 8 ? (
                          <p className="px-1 pt-1 text-xs text-muted-foreground">
                            +{result.models.length - 8} more models discovered
                          </p>
                        ) : null}
                      </div>
                    ) : (
                      <Alert>
                        <CheckCircle2 aria-hidden="true" />
                        <AlertTitle>Connection verified</AlertTitle>
                        <AlertDescription>
                          This endpoint authenticates successfully but requires a model ID to be entered on the route.
                        </AlertDescription>
                      </Alert>
                    )
                  ) : (
                    <Alert variant="destructive">
                      <CircleAlert aria-hidden="true" />
                      <AlertTitle>Connection rejected</AlertTitle>
                      <AlertDescription>Reason: {result.errorCode ?? "provider_unavailable"}</AlertDescription>
                    </Alert>
                  )}
                </div>
              </section>

              <section className="flex items-start gap-3 rounded-xl border border-border-subtle bg-surface-subtle p-4">
                <ShieldCheck className="mt-0.5 size-5 shrink-0 text-content-secondary" aria-hidden="true" />
                <div>
                  <h3 className="font-semibold">Organization-governed access</h3>
                  <p className="mt-1 text-sm leading-5 text-muted-foreground">
                    Administrators choose explicit workload routes. Users cannot bypass asset permissions by selecting a provider directly.
                  </p>
                </div>
              </section>
            </div>
          </div>
          <DialogFooter className="border-t border-border-subtle bg-surface-raised px-6 py-4">
            <Button
              type="button"
              variant="outline"
              disabled={test.isPending || !credential || !baseUrl}
              onClick={testConnection}
            >
              {test.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <ShieldCheck aria-hidden="true" />}
              Test connection
            </Button>
            <Button type="submit" disabled={create.isPending || !credential || !baseUrl}>
              {create.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <KeyRound aria-hidden="true" />}
              Connect provider
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function GatewaySettingsDialog({
  gateway,
  assistantRoute,
  onOpenChange,
}: {
  gateway?: GatewayResponse
  assistantRoute?: RouteResponse
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState("")
  const [baseUrl, setBaseUrl] = useState("")
  const [timeout, setTimeoutValue] = useState("60")
  const [credential, setCredential] = useState("")
  const [supportsReasoning, setSupportsReasoning] = useState(false)
  const [assistantModels, setAssistantModels] = useState("")
  const [discoveredModels, setDiscoveredModels] = useState<ModelRef[]>([])
  const update = useMutation(updateAdminAiGatewayMutation())
  const replaceAssistantModels = useMutation(replaceAdminAssistantModelsMutation())
  const test = useMutation(testStoredAdminAiGatewayMutation())
  const disable = useMutation(disableAdminAiGatewayMutation())

  useEffect(() => {
    if (!gateway) return
    setDisplayName(gateway.displayName ?? "")
    setBaseUrl(gateway.baseUrl ?? "")
    setTimeoutValue(String(gateway.requestTimeoutSeconds ?? 60))
    setCredential("")
    setSupportsReasoning(gateway.supportsOpenAiReasoningEffort ?? false)
    setAssistantModels(
      (gateway.assistantModels ?? [])
        .map((model) => `${model.modelId ?? ""}${model.displayName && model.displayName !== model.modelId ? ` | ${model.displayName}` : ""}`)
        .join("\n"),
    )
    setDiscoveredModels([])
  }, [gateway])

  const isAssistantGateway =
    Boolean(gateway?.id) && assistantRoute?.gatewayProfileId === gateway?.id
  const supportsCatalogReasoning =
    !assistantRoute?.openAiReasoningEffort || assistantRoute.openAiReasoningEffort === "NONE"
  const supportsAssistantChoices =
    isAssistantGateway && supportsCatalogReasoning

  function parsedAssistantModels() {
    return assistantModels
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const [modelId, ...displayNameParts] = line.split("|")
        const displayName = displayNameParts.join("|").trim()
        return {
          modelId: modelId.trim(),
          displayName: displayName || modelId.trim(),
        }
      })
  }

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
          supportsOpenAiReasoningEffort: supportsReasoning,
          credential: credential || undefined,
        },
      })
      if (supportsAssistantChoices) {
        await replaceAssistantModels.mutateAsync({
          path: { profileId: gateway.id },
          body: { models: parsedAssistantModels() },
        })
      }
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
        setDiscoveredModels(response.models ?? [])
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
      <DialogContent className="max-h-[min(92vh,54rem)] overflow-y-auto sm:max-w-xl">
        <form onSubmit={submit} className="contents">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ProviderLogo preset={gateway?.preset} className="size-5" />
              {gateway?.displayName}
            </DialogTitle>
            <DialogDescription>Update metadata or rotate the credential. The existing secret is never revealed.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <Field label="Connection name" value={displayName} onChange={setDisplayName} required />
            <Field label="Base URL" value={baseUrl} onChange={setBaseUrl} required />
            <Field label="Request timeout (seconds)" type="number" value={timeout} onChange={setTimeoutValue} min="1" max="300" required />
            <Field label="New API key" type="password" value={credential} onChange={setCredential} autoComplete="new-password" placeholder="Leave blank to keep the current key" />
            {gateway?.protocol === "OPENAI_COMPATIBLE" ? (
              <div className="flex items-start justify-between gap-4 rounded-lg border border-border-subtle bg-surface-subtle px-4 py-3">
                <div>
                  <Label htmlFor="edit-openai-reasoning">OpenAI reasoning effort</Label>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    Enable only if this endpoint accepts reasoning_effort. Routes with an explicit value fail closed otherwise.
                  </p>
                </div>
                <Switch id="edit-openai-reasoning" checked={supportsReasoning} onCheckedChange={setSupportsReasoning} />
              </div>
            ) : null}
            <div className="space-y-2 rounded-xl border border-border-subtle bg-surface-subtle p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <Label htmlFor="assistant-model-catalog">Assistant model choices</Label>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    One model per line. Use <span className="font-mono">model-id | Display name</span>. The active answer model remains the organization default.
                  </p>
                </div>
                <Badge variant={supportsAssistantChoices ? "success" : "muted"}>
                  {supportsAssistantChoices ? "Available" : "Route required"}
                </Badge>
              </div>
              <Textarea
                id="assistant-model-catalog"
                value={assistantModels}
                disabled={!supportsAssistantChoices}
                onChange={(event) => setAssistantModels(event.target.value)}
                placeholder={"gpt-5-mini | Fast\ngpt-5 | Deep reasoning"}
                className="min-h-28 bg-background font-mono text-xs"
              />
              {!isAssistantGateway ? (
                <p className="text-xs text-muted-foreground">
                  Set this gateway as the Answer generation route before publishing choices to users.
                </p>
              ) : !supportsCatalogReasoning ? (
                <p className="text-xs text-muted-foreground">
                  Alternate choices require provider-default or None reasoning on the answer route.
                </p>
              ) : discoveredModels.length > 0 ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setAssistantModels(
                      discoveredModels
                        .map((model) => `${model.id ?? ""}${model.displayName && model.displayName !== model.id ? ` | ${model.displayName}` : ""}`)
                        .join("\n"),
                    )
                  }
                >
                  Use {discoveredModels.length} discovered models
                </Button>
              ) : null}
            </div>
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
            <Button
              type="submit"
              disabled={update.isPending || replaceAssistantModels.isPending || !displayName || !baseUrl}
            >
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
  labelSuffix,
  description,
  value,
  onChange,
  ...input
}: {
  label: string
  labelSuffix?: string
  description?: string
  value: string
  onChange: (value: string) => void
} & Omit<React.ComponentProps<typeof Input>, "value" | "onChange">) {
  const id = useMemo(() => `ai-${label.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-")}`, [label])
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <Label htmlFor={id}>{label}</Label>
        {labelSuffix ? <span className="text-xs text-muted-foreground">{labelSuffix}</span> : null}
      </div>
      <Input id={id} value={value} onChange={(event) => onChange(event.target.value)} {...input} />
      {description ? <p className="text-xs leading-5 text-muted-foreground">{description}</p> : null}
    </div>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import {
  ArrowLeft,
  Check,
  Circle,
  ExternalLink,
  LockKeyhole,
  Play,
  RefreshCw,
  ShieldAlert,
} from "lucide-react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import {
  getCapabilityPackJourneyOptions,
  getCapabilityPackJourneyQueryKey,
  setCapabilityPackProgressMutation,
  startCapabilityPackMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

export function PackJourneyPage({
  assetId,
  releaseId,
  actorKey,
}: {
  assetId: string
  releaseId: string
  actorKey: string
}) {
  const queryClient = useQueryClient()
  const journeyOptions = getCapabilityPackJourneyOptions({ path: { assetId, releaseId } })
  const journeyQueryKey = scopeAssetQueryKey(journeyOptions.queryKey, actorKey)
  const journey = useQuery({
    ...journeyOptions,
    queryKey: journeyQueryKey,
  })
  const start = useMutation({
    ...startCapabilityPackMutation(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: getCapabilityPackJourneyQueryKey({ path: { assetId, releaseId } }),
      })
      toast.success("Capability journey started")
    },
  })
  const update = useMutation({
    ...setCapabilityPackProgressMutation(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: getCapabilityPackJourneyQueryKey({ path: { assetId, releaseId } }),
      })
    },
  })

  if (journey.isPending) {
    return (
      <div className="grid min-h-0 flex-1 place-items-center p-8">
        <p className="text-body text-content-secondary">Loading your exact Pack journey…</p>
      </div>
    )
  }

  if (journey.isError || !journey.data?.assignmentId) {
    return (
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-3xl p-4 md:p-8">
          <Link
            to="/assets/$assetId"
            params={{ assetId }}
            search={{ release: releaseId }}
            className="mb-6 inline-flex items-center gap-2 text-supporting text-content-secondary"
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to Asset
          </Link>
          <Card className="overflow-hidden">
            <CardContent className="grid gap-7 p-7 md:grid-cols-[1fr_auto] md:items-center">
              <div>
                <Badge className="bg-status-success-surface text-status-success-content">
                  Exact release
                </Badge>
                <h1 className="mt-4 text-page-title">Start this capability journey</h1>
                <p className="mt-2 text-body text-content-secondary">
                  Starting creates a private, actor-scoped assignment. The Pack version and every
                  item pin remain unchanged as you progress.
                </p>
              </div>
              <Button
                size="lg"
                disabled={start.isPending}
                onClick={() => start.mutate({ path: { assetId, releaseId } })}
              >
                <Play aria-hidden="true" />
                Start journey
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    )
  }

  const value = journey.data
  const total = value.items?.length ?? 0
  const complete = value.items?.filter((item) => item.completed).length ?? 0
  const percentage = total === 0 ? 0 : Math.round((complete / total) * 100)

  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-5xl space-y-6 p-4 md:p-8">
        <header className="rounded-2xl border border-border-default bg-surface-raised p-6 shadow-sm md:p-8">
          <Link
            to="/assets/$assetId"
            params={{ assetId }}
            search={{ release: releaseId }}
            className="inline-flex items-center gap-2 text-supporting text-content-secondary"
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to Asset
          </Link>
          <div className="mt-6 grid gap-6 md:grid-cols-[1fr_15rem] md:items-end">
            <div>
              <div className="flex flex-wrap gap-2">
                <Badge className="bg-status-success-surface text-status-success-content">
                  {value.purpose?.replaceAll("_", " ")}
                </Badge>
                <Badge variant="outline" className="font-mono">
                  {value.versionLabel}
                </Badge>
                <Badge variant="outline">{value.status}</Badge>
              </div>
              <h1 className="mt-4 text-page-title">{value.title}</h1>
              <p className="mt-2 max-w-2xl text-body text-content-secondary">
                {value.expectedOutcome}
              </p>
            </div>
            <div>
              <div className="flex items-center justify-between text-supporting">
                <span>Progress</span>
                <span className="font-mono">{percentage}%</span>
              </div>
              <Progress value={percentage} className="mt-2" />
              <p className="mt-2 text-right text-metadata text-content-muted">
                {complete} of {total} accessible items
              </p>
            </div>
          </div>
          <p className="mt-5 font-mono text-metadata text-content-muted">
            release {value.packReleaseId?.slice(0, 8)} · digest{" "}
            {value.releaseDigest?.slice(0, 16)}
          </p>
        </header>

        {value.accessGap ? (
          <Alert className="border-status-warning-border bg-status-warning-surface">
            <ShieldAlert aria-hidden="true" />
            <AlertTitle>Some Pack content is not currently available to you</AlertTitle>
            <AlertDescription>
              The hidden component count and metadata are intentionally withheld. Ask the Pack
              owner to review your access.
            </AlertDescription>
          </Alert>
        ) : null}

        <section className="space-y-3" aria-label="Pack items">
          {value.items?.map((item) => {
            const target =
              item.kind === "REGISTRY_RELEASE" && item.resourceId
                ? {
                    to: "/assets/$assetId" as const,
                    params: { assetId: item.resourceId },
                    search: { release: item.pinnedVersionId },
                  }
                : null
            return (
              <Card
                key={item.key}
                className={item.completed ? "border-status-success-border" : "border-border-default"}
              >
                <CardContent className="grid gap-4 p-5 sm:grid-cols-[2.5rem_1fr_auto] sm:items-center">
                  <Button
                    size="icon"
                    variant={item.completed ? "default" : "outline"}
                    aria-label={`${item.completed ? "Mark incomplete" : "Mark complete"}: ${item.title}`}
                    disabled={update.isPending}
                    onClick={() =>
                      update.mutate({
                        path: { assetId, releaseId, itemKey: item.key! },
                        body: { completed: !item.completed },
                      })
                    }
                  >
                    {item.completed ? <Check aria-hidden="true" /> : <Circle aria-hidden="true" />}
                  </Button>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-label text-content-primary">{item.title}</h2>
                      <Badge variant={item.required ? "default" : "outline"}>
                        {item.required ? "Required" : "Optional"}
                      </Badge>
                      {item.availability === "DEPRECATED" ? (
                        <Badge className="bg-status-warning-surface text-status-warning-content">
                          Newer release may exist
                        </Badge>
                      ) : null}
                    </div>
                    <p className="mt-1 font-mono text-metadata text-content-muted">
                      {item.kind} · pin {item.pinnedVersionId?.slice(0, 12)}
                    </p>
                  </div>
                  {target ? (
                    <Button asChild variant="ghost">
                      <Link {...target}>
                        Open
                        <ExternalLink aria-hidden="true" />
                      </Link>
                    </Button>
                  ) : (
                    <Badge variant="outline">
                      <LockKeyhole className="mr-1 size-3" aria-hidden="true" />
                      Knowledge
                    </Badge>
                  )}
                </CardContent>
              </Card>
            )
          })}
        </section>

        <div className="flex justify-end">
          <Button variant="outline" onClick={() => void journey.refetch()}>
            <RefreshCw aria-hidden="true" />
            Recheck access and updates
          </Button>
        </div>
      </div>
    </div>
  )
}

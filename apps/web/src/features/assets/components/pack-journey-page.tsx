import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import {
  Check,
  Circle,
  ExternalLink,
  LockKeyhole,
  Play,
  RefreshCw,
  ShieldAlert,
} from "lucide-react"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
import { LoadingState } from "@/components/states/page-loading"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import { AssetBreadcrumb } from "@/features/assets/components/asset-breadcrumb"
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
    return <LoadingState className="min-h-full flex-1" label="Loading your exact Pack journey" />
  }

  if (journey.isError || !journey.data?.assignmentId) {
    return (
      <PageLayout.Root variant="narrow">
        <PageLayout.Header
          title="Start this capability journey"
          description="Starting creates a private, actor-scoped assignment. The Pack version and every item pin remain unchanged as you progress."
          breadcrumb={<AssetBreadcrumb assetId={assetId} current="Journey" releaseId={releaseId} />}
          metadata={
            <Badge className="bg-status-success-surface text-status-success-content">
              Exact release
            </Badge>
          }
          actions={
            <Button
              size="lg"
              disabled={start.isPending}
              onClick={() => start.mutate({ path: { assetId, releaseId } })}
            >
              <Play aria-hidden="true" />
              Start journey
            </Button>
          }
        />
      </PageLayout.Root>
    )
  }

  const value = journey.data
  const total = value.items?.length ?? 0
  const complete = value.items?.filter((item) => item.completed).length ?? 0
  const percentage = total === 0 ? 0 : Math.round((complete / total) * 100)

  return (
    <PageLayout.Root variant="standard">
      <PageLayout.Header
        title={value.title}
        description={value.expectedOutcome}
        breadcrumb={
          <AssetBreadcrumb
            assetId={assetId}
            assetTitle={value.title}
            current="Journey"
            releaseId={releaseId}
          />
        }
        metadata={
          <div className="flex flex-wrap gap-2">
            <Badge className="bg-status-success-surface text-status-success-content">
              {value.purpose?.replaceAll("_", " ")}
            </Badge>
            <Badge variant="outline" className="font-mono">
              {value.versionLabel}
            </Badge>
            <Badge variant="outline">{value.status}</Badge>
          </div>
        }
        actions={
          <div className="w-60">
            <div className="flex items-center justify-between text-supporting">
              <span>Progress</span>
              <span className="font-mono">{percentage}%</span>
            </div>
            <Progress value={percentage} className="mt-2" />
            <p className="mt-2 text-right text-metadata text-content-muted">
              {complete} of {total} accessible items
            </p>
          </div>
        }
      >
        <p className="mt-5 font-mono text-metadata text-content-muted">
          release {value.packReleaseId?.slice(0, 8)} · digest {value.releaseDigest?.slice(0, 16)}
        </p>
      </PageLayout.Header>

      <PageLayout.Body>
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
                className={
                  item.completed ? "border-status-success-border" : "border-border-default"
                }
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
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

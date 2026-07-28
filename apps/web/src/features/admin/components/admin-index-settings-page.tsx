import { useQuery } from "@tanstack/react-query"
import { Boxes, DatabaseZap, LockKeyhole, Ruler } from "lucide-react"

import { ErrorState } from "@/components/states/application-error"
import { LoadingState } from "@/components/states/page-loading"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { AdminPage } from "@/features/admin/components/admin-page"
import { getAdminAiIndexSettingsOptions } from "@/lib/hey-api/@tanstack/react-query.gen"

export function AdminIndexSettingsPage() {
  const settings = useQuery(getAdminAiIndexSettingsOptions())

  if (settings.isPending) {
    return <LoadingState label="Loading index settings" className="min-h-full flex-1" />
  }

  if (settings.isError) {
    return (
      <div className="grid min-h-full flex-1 place-items-center p-6">
        <ErrorState
          title="Index settings could not be loaded"
          description="Administration permission is required to inspect embedding geometry."
          error={settings.error}
          onRetry={() => settings.refetch()}
        />
      </div>
    )
  }

  const value = settings.data
  const facts = [
    { label: "Provider", value: value.embeddingProvider, icon: Boxes },
    { label: "Model", value: value.embeddingModel, icon: DatabaseZap },
    { label: "Dimensions", value: value.dimensions?.toLocaleString(), icon: Ruler },
    { label: "Distance metric", value: value.distanceMetric, icon: LockKeyhole },
  ]

  return (
    <AdminPage
      title="Index Settings"
      description="The embedding geometry that makes documents searchable across the organization."
      icon={<DatabaseZap className="size-5" aria-hidden="true" />}
    >
      <Card className="overflow-hidden border-border-subtle bg-surface-raised py-0">
        <CardHeader className="border-b border-border-subtle py-5">
          <div className="flex flex-wrap items-center gap-3">
            <CardTitle>Embedding profile</CardTitle>
            <Badge variant="muted">Deployment managed</Badge>
          </div>
          <p className="max-w-3xl text-sm leading-6 text-muted-foreground">
            Chat models can change independently. This profile stays locked because changing vector
            dimensions or distance semantics requires a new index generation.
          </p>
        </CardHeader>
        <CardContent className="grid gap-px bg-border-subtle p-0 sm:grid-cols-2 xl:grid-cols-4">
          {facts.map((fact) => (
            <div key={fact.label} className="min-h-32 bg-card p-5">
              <fact.icon className="mb-5 size-4 text-muted-foreground" aria-hidden="true" />
              <p className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">
                {fact.label}
              </p>
              <p className="mt-1.5 break-words text-base font-semibold">{fact.value ?? "Not configured"}</p>
            </div>
          ))}
        </CardContent>
      </Card>

      <div className="flex gap-3 rounded-xl border border-border-subtle bg-surface-subtle p-5">
        <LockKeyhole className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div>
          <p className="text-sm font-medium">Why this is separate from Language Models</p>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-muted-foreground">
            {value.lifecycleNote ??
              "Embedding changes require a versioned profile and a controlled reindex before activation."}
          </p>
        </div>
      </div>
    </AdminPage>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { ChevronLeft, LoaderCircle, RefreshCw } from "lucide-react"
import { useState, type FormEvent } from "react"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
import { Alert, AlertDescription } from "@/components/ui/alert"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { scopeAssetQueryKey } from "@/features/assets/actor-key"
import { AssetPageError, AssetPageLoading } from "@/features/assets/components/asset-state"
import { SkillPackageInput } from "@/features/assets/components/skill-package-input"
import { SkillPackageInspectionCard } from "@/features/assets/components/skill-package-inspection-card"
import { apiErrorMessage } from "@/lib/api-error"
import type { SkillPackageInspection } from "@/lib/hey-api"
import {
  getAssetGovernanceActionsOptions,
  getAssetOptions,
  getAssetQueryKey,
  replaceSkillDraftPackageMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

export function SkillPackageReplacePage({
  assetId,
  actorKey,
}: {
  assetId: string
  actorKey: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const assetOptions = getAssetOptions({ path: { assetId } })
  const asset = useQuery({
    ...assetOptions,
    queryKey: scopeAssetQueryKey(assetOptions.queryKey, actorKey),
  })
  const actionOptions = getAssetGovernanceActionsOptions({ path: { assetId } })
  const actions = useQuery({
    ...actionOptions,
    queryKey: scopeAssetQueryKey(actionOptions.queryKey, actorKey),
  })
  const replace = useMutation(replaceSkillDraftPackageMutation())
  const [file, setFile] = useState<File>()
  const [inspection, setInspection] = useState<SkillPackageInspection>()
  const [error, setError] = useState<string>()

  if (asset.isPending || actions.isPending) return <AssetPageLoading />
  if (asset.isError || actions.isError || !asset.data?.id) {
    return <AssetPageError title="Skill Draft is unavailable" onRetry={() => void asset.refetch()} />
  }
  if (asset.data.type !== "SKILL" || !asset.data.draft || !actions.data?.canEdit) {
    return (
      <AssetPageError
        title="This Skill Draft cannot be edited"
        onRetry={() => void Promise.all([asset.refetch(), actions.refetch()])}
      />
    )
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || !inspection || asset.data?.draft?.lockVersion === undefined) {
      setError("Inspect a valid replacement package first.")
      return
    }
    setError(undefined)
    try {
      await replace.mutateAsync({
        path: { assetId },
        query: { expectedLockVersion: asset.data.draft.lockVersion },
        body: { file },
      })
      await queryClient.invalidateQueries({ queryKey: getAssetQueryKey({ path: { assetId } }) })
      toast.success("Skill Draft package replaced")
      await navigate({ to: "/assets/$assetId/governance", params: { assetId } })
    } catch (failure) {
      setError(apiErrorMessage(failure, "The Skill Draft package could not be replaced."))
    }
  }

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Replace Skill package"
        description="Validate new bytes for the mutable Draft. Existing revisions and releases stay unchanged."
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets">Assets</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets/$assetId/governance" params={{ assetId }}>Governance</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbPage>Replace package</BreadcrumbPage></BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <form onSubmit={submit} className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_23rem]">
          <Card className="gap-0 bg-surface-raised py-0 shadow-none">
            <CardHeader className="border-b border-border-subtle px-6 py-5">
              <CardTitle>Replacement package</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6 p-6">
              <SkillPackageInput
                disabled={replace.isPending}
                onInspected={(archive, result) => {
                  setFile(archive)
                  setInspection(result)
                  setError(undefined)
                }}
              />
              <Alert>
                <AlertDescription>
                  Draft version {asset.data.draft.lockVersion}. If another author saves first, OrgMemory rejects this replacement and asks you to reload.
                </AlertDescription>
              </Alert>
              {error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}
              <div className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:justify-end">
                <Button variant="outline" asChild><Link to="/assets/$assetId/governance" params={{ assetId }}><ChevronLeft aria-hidden="true" />Back</Link></Button>
                <Button type="submit" disabled={replace.isPending || !inspection}>
                  {replace.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <RefreshCw aria-hidden="true" />}
                  {replace.isPending ? "Replacing package" : "Replace Draft package"}
                </Button>
              </div>
            </CardContent>
          </Card>

          {inspection ? (
            <SkillPackageInspectionCard inspection={inspection} />
          ) : (
            <Card className="h-fit border-dashed bg-surface-subtle shadow-none">
              <CardContent className="p-6 text-sm leading-6 text-content-secondary">
                The replacement is staged under a fresh object key. OrgMemory swaps only the Draft pointer and cleans the old object only when no immutable record references it.
              </CardContent>
            </Card>
          )}
        </form>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

import { useMutation, useQuery } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { ChevronLeft, LoaderCircle, Upload } from "lucide-react"
import { useState, type FormEvent } from "react"

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
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { SkillPackageInput } from "@/features/assets/components/skill-package-input"
import { SkillPackageInspectionCard } from "@/features/assets/components/skill-package-inspection-card"
import { validateSkillUpload } from "@/features/assets/skill-upload-validation"
import { apiErrorMessage } from "@/lib/api-error"
import type { KnowledgeSpaceResponse, SkillPackageInspection } from "@/lib/hey-api"
import {
  importSkillPackageMutation,
  listKnowledgeSpaceUploadTargetsOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"

type Classification = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"

function validSpace(
  space: KnowledgeSpaceResponse,
): space is KnowledgeSpaceResponse & { id: string; name: string } {
  return Boolean(space.id && space.name)
}

export function SkillUploadPage() {
  const navigate = useNavigate()
  const uploadTargets = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const createDraft = useMutation(importSkillPackageMutation())
  const [file, setFile] = useState<File>()
  const [inspection, setInspection] = useState<SkillPackageInspection>()
  const [namespace, setNamespace] = useState("")
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState("")
  const [classification, setClassification] = useState<Classification>("INTERNAL")
  const [error, setError] = useState<string>()
  const spaces = (uploadTargets.data ?? []).filter(validSpace)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validation = validateSkillUpload({ file, namespace, knowledgeSpaceId })
    if (!inspection || !validation.ok || !file) {
      setError(
        !inspection
          ? "Inspect a valid Skill package before creating the Draft."
          : validation.ok
            ? "Choose a Skill package."
            : validation.message,
      )
      return
    }
    setError(undefined)
    try {
      const asset = await createDraft.mutateAsync({
        body: { file },
        query: {
          namespace: validation.namespace,
          knowledgeSpaceId,
          classification,
        },
      })
      if (!asset.id) throw new Error("The created Draft did not include an Asset id.")
      await navigate({
        to: "/assets/$assetId/governance",
        params: { assetId: asset.id },
      })
    } catch (failure) {
      setError(apiErrorMessage(failure, "The Skill Draft could not be created."))
    }
  }

  const pending = createDraft.isPending
  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Upload a Skill"
        description="Inspect an existing package, then create a private governed Draft."
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild><Link to="/assets">Assets</Link></BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbLink asChild><Link to="/assets/new/skill">Create Skill</Link></BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbPage>Upload</BreadcrumbPage></BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <form onSubmit={submit} className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_23rem]">
          <div className="space-y-6">
            <Card className="gap-0 bg-surface-raised py-0 shadow-none">
              <CardHeader className="border-b border-border-subtle px-6 py-5">
                <CardTitle>1. Choose package</CardTitle>
              </CardHeader>
              <CardContent className="p-6">
                <SkillPackageInput
                  disabled={pending}
                  onInspected={(archive, result) => {
                    setFile(archive)
                    setInspection(result)
                    setError(undefined)
                  }}
                />
              </CardContent>
            </Card>

            <Card className="gap-0 bg-surface-raised py-0 shadow-none">
              <CardHeader className="border-b border-border-subtle px-6 py-5">
                <CardTitle>2. Place the Draft</CardTitle>
              </CardHeader>
              <CardContent className="space-y-6 p-6">
                <div className="space-y-2">
                  <Label htmlFor="skill-namespace">Namespace</Label>
                  <Input
                    id="skill-namespace"
                    value={namespace}
                    maxLength={128}
                    placeholder="engineering"
                    autoComplete="off"
                    disabled={pending}
                    onChange={(event) => {
                      setNamespace(event.currentTarget.value)
                      setError(undefined)
                    }}
                  />
                </div>

                <div className="grid gap-5 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="skill-space">Knowledge Space</Label>
                    <Select
                      value={knowledgeSpaceId}
                      disabled={pending || uploadTargets.isPending || uploadTargets.isError || spaces.length === 0}
                      onValueChange={setKnowledgeSpaceId}
                    >
                      <SelectTrigger id="skill-space" className="w-full">
                        <SelectValue placeholder={uploadTargets.isPending ? "Loading spaces" : "Choose a space"} />
                      </SelectTrigger>
                      <SelectContent>
                        {spaces.map((space) => (
                          <SelectItem key={space.id} value={space.id}>{space.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {uploadTargets.isError ? (
                      <Button type="button" variant="outline" size="sm" onClick={() => void uploadTargets.refetch()}>
                        Retry spaces
                      </Button>
                    ) : null}
                    {!uploadTargets.isPending && !uploadTargets.isError && spaces.length === 0 ? (
                      <p className="text-sm text-content-muted">No authorized upload target is available.</p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="skill-classification">Classification</Label>
                    <Select value={classification} disabled={pending} onValueChange={(value) => setClassification(value as Classification)}>
                      <SelectTrigger id="skill-classification" className="w-full"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="PUBLIC">Public</SelectItem>
                        <SelectItem value="INTERNAL">Internal</SelectItem>
                        <SelectItem value="CONFIDENTIAL">Confidential</SelectItem>
                        <SelectItem value="RESTRICTED">Restricted</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                {error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}

                <div className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:justify-end">
                  <Button variant="outline" asChild={!pending} disabled={pending}>
                    {pending ? <span><ChevronLeft aria-hidden="true" />Back</span> : <Link to="/assets/new/skill"><ChevronLeft aria-hidden="true" />Back</Link>}
                  </Button>
                  <Button type="submit" disabled={pending || !inspection || uploadTargets.isPending || uploadTargets.isError || spaces.length === 0}>
                    {pending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Upload aria-hidden="true" />}
                    {pending ? "Creating Draft" : "Create Draft"}
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>

          {inspection ? (
            <SkillPackageInspectionCard inspection={inspection} />
          ) : (
            <Card className="h-fit border-dashed bg-surface-subtle shadow-none">
              <CardContent className="p-6 text-sm leading-6 text-content-secondary">
                Package identity, instructions, file manifest, digest, and bounds appear here after server inspection.
              </CardContent>
            </Card>
          )}
        </form>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

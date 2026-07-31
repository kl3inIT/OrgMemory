import { useMutation, useQuery } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { ChevronLeft, FileArchive, LoaderCircle, Upload } from "lucide-react"
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
import type { KnowledgeSpaceResponse } from "@/lib/hey-api"
import {
  importSkillPackageMutation,
  listKnowledgeSpaceUploadTargetsOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import { validateSkillUpload } from "@/features/assets/skill-upload-validation"

type Classification = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"

function validSpace(
  space: KnowledgeSpaceResponse,
): space is KnowledgeSpaceResponse & { id: string; name: string } {
  return Boolean(space.id && space.name)
}

export function SkillUploadPage() {
  const navigate = useNavigate()
  const uploadTargets = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const importSkill = useMutation(importSkillPackageMutation())
  const [file, setFile] = useState<File>()
  const [namespace, setNamespace] = useState("")
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState("")
  const [classification, setClassification] = useState<Classification>("INTERNAL")
  const [error, setError] = useState<string>()
  const spaces = (uploadTargets.data ?? []).filter(validSpace)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const selectedFile = file
    const validation = validateSkillUpload({ file: selectedFile, namespace, knowledgeSpaceId })
    if (!validation.ok) {
      setError(validation.message)
      return
    }
    if (!selectedFile) return

    setError(undefined)
    try {
      const asset = await importSkill.mutateAsync({
        body: { file: selectedFile },
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
      setError(failure instanceof Error ? failure.message : "The Skill could not be imported.")
    }
  }

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Upload a Skill"
        description="Import an existing package as a private governed Draft."
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
        <form onSubmit={submit} className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <Card className="gap-0 bg-surface-raised py-0 shadow-none">
            <CardHeader className="border-b border-border-subtle px-6 py-5">
              <CardTitle>Skill package</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6 p-6">
              <div className="space-y-2">
                <Label htmlFor="skill-package">ZIP package</Label>
                <Input
                  id="skill-package"
                  type="file"
                  accept=".zip,application/zip"
                  required
                  disabled={importSkill.isPending}
                  onChange={(event) => {
                    setFile(event.target.files?.[0])
                    setError(undefined)
                  }}
                />
                <p className="text-xs leading-5 text-content-muted">
                  One Skill with a root `SKILL.md`, up to 20 MB. OrgMemory validates structure on
                  the server and does not execute package files.
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="skill-namespace">Namespace</Label>
                <Input
                  id="skill-namespace"
                  value={namespace}
                  maxLength={128}
                  placeholder="engineering"
                  autoComplete="off"
                  disabled={importSkill.isPending}
                  onChange={(event) => {
                    setNamespace(event.currentTarget.value)
                    setError(undefined)
                  }}
                />
                <p className="text-xs text-content-muted">Company-local grouping for the Skill coordinate.</p>
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="skill-space">Knowledge Space</Label>
                  {uploadTargets.isPending ? (
                    <div className="flex h-9 items-center gap-2 text-sm text-content-secondary" role="status">
                      <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                      Loading available spaces
                    </div>
                  ) : null}
                  {uploadTargets.isError ? (
                    <Alert variant="destructive">
                      <AlertDescription className="space-y-3">
                        <p>Available spaces could not be loaded.</p>
                        <Button type="button" variant="outline" size="sm" onClick={() => void uploadTargets.refetch()}>
                          Try again
                        </Button>
                      </AlertDescription>
                    </Alert>
                  ) : null}
                  {!uploadTargets.isPending && !uploadTargets.isError ? (
                    <Select value={knowledgeSpaceId} disabled={importSkill.isPending} onValueChange={setKnowledgeSpaceId}>
                      <SelectTrigger id="skill-space" className="w-full"><SelectValue placeholder="Choose a space" /></SelectTrigger>
                      <SelectContent>
                        {spaces.map((space) => <SelectItem key={space.id} value={space.id}>{space.name}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  ) : null}
                  {!uploadTargets.isPending && !uploadTargets.isError && spaces.length === 0 ? (
                    <p className="text-sm text-content-muted">You do not have an authorized upload target.</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="skill-classification">Classification</Label>
                  <Select value={classification} disabled={importSkill.isPending} onValueChange={(value) => setClassification(value as Classification)}>
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
                <Button variant="outline" asChild disabled={importSkill.isPending}>
                  <Link to="/assets/new/skill"><ChevronLeft aria-hidden="true" />Back</Link>
                </Button>
                <Button type="submit" disabled={importSkill.isPending || uploadTargets.isPending || uploadTargets.isError || spaces.length === 0}>
                  {importSkill.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Upload aria-hidden="true" />}
                  {importSkill.isPending ? "Creating Draft" : "Create Draft"}
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="h-fit gap-0 bg-surface-subtle py-0 shadow-none">
            <CardContent className="p-6">
              <span className="grid size-10 place-items-center rounded-xl bg-status-success-surface text-status-success-content">
                <FileArchive className="size-5" aria-hidden="true" />
              </span>
              <h2 className="mt-5 font-semibold text-content-primary">What happens next</h2>
              <ol className="mt-3 space-y-3 text-sm leading-6 text-content-secondary">
                <li>1. The server validates the package and your current Space permission.</li>
                <li>2. OrgMemory stores the exact bytes and creates a private Draft.</li>
                <li>3. You review and publish separately from the governance workspace.</li>
              </ol>
            </CardContent>
          </Card>
        </form>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

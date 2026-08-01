import { useMutation, useQuery } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { ChevronLeft, LoaderCircle, PackageCheck, Plus } from "lucide-react"
import { useState, type FormEvent, type ReactNode } from "react"

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
import { Textarea } from "@/components/ui/textarea"
import { SkillPackageInspectionCard } from "@/features/assets/components/skill-package-inspection-card"
import { buildScratchSkillPackage } from "@/features/assets/skill-package-browser"
import { validateSkillUpload } from "@/features/assets/skill-upload-validation"
import { apiErrorMessage } from "@/lib/api-error"
import type { KnowledgeSpaceResponse, SkillPackageInspection } from "@/lib/hey-api"
import {
  importSkillPackageMutation,
  inspectSkillPackageMutation,
  listKnowledgeSpaceUploadTargetsOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"

type Classification = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"

function validSpace(
  space: KnowledgeSpaceResponse,
): space is KnowledgeSpaceResponse & { id: string; name: string } {
  return Boolean(space.id && space.name)
}

export function SkillScratchPage() {
  const navigate = useNavigate()
  const uploadTargets = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const inspect = useMutation(inspectSkillPackageMutation())
  const createDraft = useMutation(importSkillPackageMutation())
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [instructions, setInstructions] = useState("")
  const [license, setLicense] = useState("")
  const [compatibility, setCompatibility] = useState("")
  const [allowedTools, setAllowedTools] = useState("")
  const [supportingFiles, setSupportingFiles] = useState<File[]>([])
  const [namespace, setNamespace] = useState("")
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState("")
  const [classification, setClassification] = useState<Classification>("INTERNAL")
  const [packageFile, setPackageFile] = useState<File>()
  const [inspection, setInspection] = useState<SkillPackageInspection>()
  const [error, setError] = useState<string>()
  const spaces = (uploadTargets.data ?? []).filter(validSpace)

  function invalidatePreview() {
    setPackageFile(undefined)
    setInspection(undefined)
    setError(undefined)
  }

  async function validatePackage() {
    setError(undefined)
    try {
      const archive = await buildScratchSkillPackage({
        name,
        description,
        instructions,
        license,
        compatibility,
        allowedTools,
        supportingFiles,
      })
      const result = await inspect.mutateAsync({ body: { file: archive } })
      setPackageFile(archive)
      setInspection(result)
    } catch (failure) {
      setError(apiErrorMessage(failure, "The Skill package could not be validated."))
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validation = validateSkillUpload({ file: packageFile, namespace, knowledgeSpaceId })
    if (!inspection || !packageFile || !validation.ok) {
      setError(
        !inspection
          ? "Validate the current Skill content before creating the Draft."
          : validation.ok
            ? "The validated package is unavailable."
            : validation.message,
      )
      return
    }
    setError(undefined)
    try {
      const asset = await createDraft.mutateAsync({
        body: { file: packageFile },
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

  const pending = inspect.isPending || createDraft.isPending
  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Start a Skill from scratch"
        description="Write the portable instructions, validate the package, then create a private Draft."
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets">Assets</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets/new/skill">Create Skill</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbPage>Scratch</BreadcrumbPage></BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <form
          onSubmit={submit}
          className={inspection ? "grid gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]" : "grid gap-6"}
        >
          <div className="space-y-6">
            <Card className="gap-0 bg-surface-raised py-0 shadow-none">
              <CardHeader className="border-b border-border-subtle px-6 py-5"><CardTitle>Skill content</CardTitle></CardHeader>
              <CardContent className="space-y-6 p-6">
                <div className="grid gap-5 md:grid-cols-2">
                  <Field label="Skill name" id="scratch-name" hint="Portable package id, for example support-triage.">
                    <Input id="scratch-name" value={name} maxLength={64} placeholder="support-triage" disabled={pending} onChange={(event) => { setName(event.currentTarget.value); invalidatePreview() }} />
                  </Field>
                  <Field label="Description" id="scratch-description" hint="When an assistant should use this Skill.">
                    <Input id="scratch-description" value={description} maxLength={1024} disabled={pending} onChange={(event) => { setDescription(event.currentTarget.value); invalidatePreview() }} />
                  </Field>
                </div>

                <Field label="Instructions" id="scratch-instructions" hint="Markdown instructions stored in SKILL.md.">
                  <Textarea id="scratch-instructions" value={instructions} rows={14} placeholder="# Workflow\n\nDescribe the task, boundaries, and expected output." disabled={pending} onChange={(event) => { setInstructions(event.currentTarget.value); invalidatePreview() }} />
                </Field>

                <div className="grid gap-5 md:grid-cols-3">
                  <Field label="License" id="scratch-license"><Input id="scratch-license" value={license} disabled={pending} onChange={(event) => { setLicense(event.currentTarget.value); invalidatePreview() }} /></Field>
                  <Field label="Compatibility" id="scratch-compatibility"><Input id="scratch-compatibility" value={compatibility} disabled={pending} onChange={(event) => { setCompatibility(event.currentTarget.value); invalidatePreview() }} /></Field>
                  <Field label="Declared tools" id="scratch-tools" hint="Portability metadata only; it grants no permission."><Input id="scratch-tools" value={allowedTools} disabled={pending} onChange={(event) => { setAllowedTools(event.currentTarget.value); invalidatePreview() }} /></Field>
                </div>

                <Field label="Supporting files" id="scratch-files" hint="Optional references are stored under references/. Files are never executed.">
                  <Input id="scratch-files" type="file" multiple disabled={pending} onChange={(event) => { setSupportingFiles(Array.from(event.currentTarget.files ?? [])); invalidatePreview() }} />
                </Field>
              </CardContent>
            </Card>

            <Card className="gap-0 bg-surface-raised py-0 shadow-none">
              <CardHeader className="border-b border-border-subtle px-6 py-5"><CardTitle>Governance placement</CardTitle></CardHeader>
              <CardContent className="grid gap-5 p-6 md:grid-cols-3">
                <Field label="Namespace" id="scratch-namespace"><Input id="scratch-namespace" value={namespace} maxLength={128} placeholder="engineering" disabled={pending} onChange={(event) => setNamespace(event.currentTarget.value)} /></Field>
                <Field label="Knowledge Space" id="scratch-space">
                  <Select value={knowledgeSpaceId} disabled={pending || uploadTargets.isPending || uploadTargets.isError || spaces.length === 0} onValueChange={setKnowledgeSpaceId}>
                    <SelectTrigger id="scratch-space" className="w-full"><SelectValue placeholder={uploadTargets.isPending ? "Loading spaces" : "Choose a space"} /></SelectTrigger>
                    <SelectContent>{spaces.map((space) => <SelectItem key={space.id} value={space.id}>{space.name}</SelectItem>)}</SelectContent>
                  </Select>
                </Field>
                <Field label="Classification" id="scratch-classification">
                  <Select value={classification} disabled={pending} onValueChange={(value) => setClassification(value as Classification)}>
                    <SelectTrigger id="scratch-classification" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="PUBLIC">Public</SelectItem><SelectItem value="INTERNAL">Internal</SelectItem><SelectItem value="CONFIDENTIAL">Confidential</SelectItem><SelectItem value="RESTRICTED">Restricted</SelectItem>
                    </SelectContent>
                  </Select>
                </Field>
              </CardContent>
            </Card>

            {error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}

            <div className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:justify-between">
              <Button variant="outline" asChild><Link to="/assets/new/skill"><ChevronLeft aria-hidden="true" />Back</Link></Button>
              <div className="flex flex-col gap-3 sm:flex-row">
                <Button type="button" variant="outline" disabled={pending} onClick={() => void validatePackage()}>
                  {inspect.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <PackageCheck aria-hidden="true" />}
                  Validate package
                </Button>
                <Button type="submit" disabled={pending || !inspection || uploadTargets.isPending || uploadTargets.isError || spaces.length === 0}>
                  {createDraft.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Plus aria-hidden="true" />}
                  {createDraft.isPending ? "Creating Draft" : "Create Draft"}
                </Button>
              </div>
            </div>
          </div>

          {inspection ? <SkillPackageInspectionCard inspection={inspection} /> : null}
        </form>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

function Field({ label, id, hint, children }: { label: string; id: string; hint?: string; children: ReactNode }) {
  return <div className="space-y-2"><Label htmlFor={id}>{label}</Label>{children}{hint ? <p className="text-xs leading-5 text-content-muted">{hint}</p> : null}</div>
}

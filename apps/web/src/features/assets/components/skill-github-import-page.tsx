import { useMutation, useQuery } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  FileCode2,
  GitBranch,
  GitFork,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
} from "lucide-react"
import { useMemo, useState, type FormEvent } from "react"

import { PageLayout } from "@/components/layouts/page-layout"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
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
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { apiErrorMessage } from "@/lib/api-error"
import type {
  ImportResult,
  KnowledgeSpaceResponse,
  Preview,
  PreviewItem,
} from "@/lib/hey-api"
import {
  importGitHubSkillsMutation,
  listGitHubSkillConnectionsOptions,
  listKnowledgeSpaceUploadTargetsOptions,
  previewGitHubSkillsMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"

type Classification = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"

const PUBLIC_ACCESS = "__public__"

function validSpace(
  space: KnowledgeSpaceResponse,
): space is KnowledgeSpaceResponse & { id: string; name: string } {
  return Boolean(space.id && space.name)
}

function importableSkill(
  skill: PreviewItem,
): skill is PreviewItem & { path: string; importable: true } {
  return skill.importable === true && Boolean(skill.path)
}

export function SkillGitHubImportPage() {
  const previewMutation = useMutation(previewGitHubSkillsMutation())
  const importMutation = useMutation(importGitHubSkillsMutation())
  const uploadTargets = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const [repository, setRepository] = useState("")
  const [revision, setRevision] = useState("main")
  const [subpath, setSubpath] = useState("")
  const [access, setAccess] = useState(PUBLIC_ACCESS)
  const [preview, setPreview] = useState<Preview>()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [namespace, setNamespace] = useState("")
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState("")
  const [classification, setClassification] = useState<Classification>("INTERNAL")
  const [result, setResult] = useState<ImportResult>()
  const [error, setError] = useState<string>()
  const connections = useQuery({
    ...listGitHubSkillConnectionsOptions({ query: { knowledgeSpaceId } }),
    enabled: Boolean(knowledgeSpaceId),
  })
  const spaces = (uploadTargets.data ?? []).filter(validSpace)
  const importable = useMemo(
    () => (preview?.skills ?? []).filter(importableSkill),
    [preview],
  )
  const pending = previewMutation.isPending || importMutation.isPending

  async function inspectRepository(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!repository.trim() || !knowledgeSpaceId) {
      setError("Choose a Knowledge Space and enter a GitHub repository before previewing it.")
      return
    }
    setError(undefined)
    setResult(undefined)
    try {
      const response = await previewMutation.mutateAsync({
        body: {
          repository: repository.trim(),
          revision: revision.trim() || "HEAD",
          subpath: subpath.trim() || undefined,
          connectionKey: access === PUBLIC_ACCESS ? undefined : access,
          knowledgeSpaceId,
        },
      })
      setPreview(response)
      setSelected(new Set((response.skills ?? []).filter(importableSkill).map((skill) => skill.path)))
    } catch (failure) {
      setPreview(undefined)
      setSelected(new Set())
      setError(apiErrorMessage(failure, "The repository could not be previewed."))
    }
  }

  async function importSkills() {
    if (!preview?.revision || !preview.repository) {
      setError("Preview the repository before importing Skills.")
      return
    }
    if (selected.size === 0) {
      setError("Select at least one valid Skill.")
      return
    }
    if (!namespace.trim() || !knowledgeSpaceId) {
      setError("Choose a namespace and Knowledge Space for the Drafts.")
      return
    }
    setError(undefined)
    try {
      const response = await importMutation.mutateAsync({
        body: {
          source: {
            repository: preview.repository,
            revision: preview.revision,
            subpath: subpath.trim() || undefined,
            connectionKey: access === PUBLIC_ACCESS ? undefined : access,
            knowledgeSpaceId,
          },
          paths: Array.from(selected),
          namespace: namespace.trim(),
          classification,
        },
      })
      setResult(response)
    } catch (failure) {
      setError(apiErrorMessage(failure, "The selected Skills could not be imported."))
    }
  }

  function toggle(path: string, checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (checked) next.add(path)
      else next.delete(path)
      return next
    })
    setResult(undefined)
    setError(undefined)
  }

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Import Skills from GitHub"
        description="Discover Skills in a repository, pin an exact commit, and create private governed Drafts."
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets">Assets</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets/new/skill">Create Skill</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbPage>GitHub</BreadcrumbPage></BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="space-y-6">
            <Card className="gap-0 bg-surface-raised py-0 shadow-none">
              <CardHeader className="border-b border-border-subtle px-6 py-5">
                <CardTitle>1. Choose repository</CardTitle>
              </CardHeader>
              <CardContent className="p-6">
                <form className="space-y-5" onSubmit={inspectRepository}>
                  <div className="grid gap-5 md:grid-cols-[minmax(0,2fr)_minmax(16rem,1fr)]">
                    <div className="space-y-2">
                      <Label htmlFor="github-repository">Repository</Label>
                      <Input
                        id="github-repository"
                        value={repository}
                        placeholder="owner/repository or https://github.com/owner/repository"
                        autoComplete="off"
                        disabled={pending}
                        onChange={(event) => {
                          setRepository(event.currentTarget.value)
                          setPreview(undefined)
                          setResult(undefined)
                        }}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="github-space">Knowledge Space</Label>
                      <Select
                        value={knowledgeSpaceId}
                        disabled={pending || uploadTargets.isPending || spaces.length === 0}
                        onValueChange={(value) => {
                          setKnowledgeSpaceId(value)
                          setAccess(PUBLIC_ACCESS)
                          setPreview(undefined)
                          setSelected(new Set())
                          setResult(undefined)
                          setError(undefined)
                        }}
                      >
                        <SelectTrigger id="github-space" className="w-full"><SelectValue placeholder="Choose a space" /></SelectTrigger>
                        <SelectContent>{spaces.map((space) => <SelectItem key={space.id} value={space.id}>{space.name}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                  </div>
                  <div className="grid gap-5 md:grid-cols-3">
                    <div className="space-y-2">
                      <Label htmlFor="github-revision">Branch, tag, or commit</Label>
                      <Input
                        id="github-revision"
                        value={revision}
                        placeholder="main"
                        autoComplete="off"
                        disabled={pending}
                        onChange={(event) => {
                          setRevision(event.currentTarget.value)
                          setPreview(undefined)
                          setResult(undefined)
                        }}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="github-subpath">Directory (optional)</Label>
                      <Input
                        id="github-subpath"
                        value={subpath}
                        placeholder="skills"
                        autoComplete="off"
                        disabled={pending}
                        onChange={(event) => {
                          setSubpath(event.currentTarget.value)
                          setPreview(undefined)
                          setResult(undefined)
                        }}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="github-access">Repository access</Label>
                      <Select value={access} disabled={pending || connections.isPending} onValueChange={(value) => {
                        setAccess(value)
                        setPreview(undefined)
                        setResult(undefined)
                      }}>
                        <SelectTrigger id="github-access" className="w-full"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value={PUBLIC_ACCESS}>Public repository</SelectItem>
                          {(connections.data ?? []).flatMap((connection) => connection.key ? [
                            <SelectItem key={connection.key} value={connection.key}>{connection.key}</SelectItem>,
                          ] : [])}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                  {connections.isError ? (
                    <Alert variant="destructive">
                      <AlertDescription>
                        Private repository connections could not be loaded. Public import is still available.{" "}
                        <Button variant="link" className="h-auto p-0" onClick={() => void connections.refetch()}>Retry</Button>
                      </AlertDescription>
                    </Alert>
                  ) : null}
                  {uploadTargets.isError ? <Alert variant="destructive"><AlertDescription>Knowledge Spaces could not be loaded. <Button variant="link" className="h-auto p-0" onClick={() => void uploadTargets.refetch()}>Retry</Button></AlertDescription></Alert> : null}
                  <div className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:justify-between">
                    <Button variant="outline" asChild><Link to="/assets/new/skill"><ChevronLeft aria-hidden="true" />Back</Link></Button>
                    <Button type="submit" disabled={pending || !repository.trim() || !knowledgeSpaceId}>
                      {previewMutation.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <GitFork aria-hidden="true" />}
                      {previewMutation.isPending ? "Reading repository" : preview ? "Refresh preview" : "Preview Skills"}
                    </Button>
                  </div>
                </form>
              </CardContent>
            </Card>

            {preview ? (
              <Card className="gap-0 bg-surface-raised py-0 shadow-none">
                <CardHeader className="border-b border-border-subtle px-6 py-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <CardTitle>2. Select Skills</CardTitle>
                    <div className="flex items-center gap-2">
                      <Badge variant="outline">{preview.visibility === "PRIVATE" ? <LockKeyhole aria-hidden="true" /> : <GitFork aria-hidden="true" />}{preview.visibility === "PRIVATE" ? "Private" : "Public"}</Badge>
                      <Badge variant="secondary"><GitBranch aria-hidden="true" />{preview.revision?.slice(0, 8)}</Badge>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4 p-6">
                  {importable.length > 0 ? (
                    <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-border-subtle bg-surface-subtle px-4 py-3 text-sm font-medium text-content-primary">
                      <Checkbox
                        checked={selected.size === importable.length}
                        onCheckedChange={(checked) => {
                          setSelected(checked === true ? new Set(importable.map((skill) => skill.path)) : new Set())
                          setResult(undefined)
                          setError(undefined)
                        }}
                      />
                      Select all valid Skills
                      <span className="ml-auto text-content-muted">{selected.size} of {importable.length}</span>
                    </label>
                  ) : null}
                  <div className="grid gap-3 md:grid-cols-2">
                    {(preview.skills ?? []).map((skill, index) => {
                      const path = skill.path ?? `skill-${index}`
                      const valid = importableSkill(skill)
                      return (
                        <label
                          key={path}
                          className={`flex gap-3 rounded-xl border p-4 ${valid ? "cursor-pointer border-border-subtle bg-surface-raised hover:border-border-strong" : "border-status-warning-border bg-status-warning-surface"}`}
                        >
                          <Checkbox
                            checked={valid && selected.has(path)}
                            disabled={!valid || pending}
                            onCheckedChange={(checked) => valid && toggle(path, checked === true)}
                          />
                          <span className="min-w-0 flex-1">
                            <span className="flex items-start justify-between gap-3">
                              <span className="font-semibold text-content-primary">{skill.name || path}</span>
                              {valid ? <Badge variant="outline">{skill.fileCount ?? 0} files</Badge> : <AlertTriangle className="size-4 shrink-0 text-status-warning-content" aria-hidden="true" />}
                            </span>
                            <span className="mt-1 block text-sm leading-5 text-content-secondary">{skill.description || skill.errorMessage}</span>
                            <span className="mt-3 block truncate font-mono text-xs text-content-muted">{path}</span>
                          </span>
                        </label>
                      )
                    })}
                  </div>
                  {importable.length === 0 ? (
                    <Alert><AlertDescription>No importable Skills were found at this revision.</AlertDescription></Alert>
                  ) : null}
                </CardContent>
              </Card>
            ) : null}

            {preview && importable.length > 0 ? (
              <Card className="gap-0 bg-surface-raised py-0 shadow-none">
                <CardHeader className="border-b border-border-subtle px-6 py-5"><CardTitle>3. Place the Drafts</CardTitle></CardHeader>
                <CardContent className="space-y-5 p-6">
                  <div className="grid gap-5 md:grid-cols-2">
                    <div className="space-y-2">
                      <Label htmlFor="github-namespace">Namespace</Label>
                      <Input id="github-namespace" value={namespace} placeholder="engineering" disabled={pending} onChange={(event) => setNamespace(event.currentTarget.value)} />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="github-classification">Classification</Label>
                      <Select value={classification} disabled={pending} onValueChange={(value) => setClassification(value as Classification)}>
                        <SelectTrigger id="github-classification" className="w-full"><SelectValue /></SelectTrigger>
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
                  <div className="flex justify-end border-t border-border-subtle pt-5">
                    <Button onClick={() => void importSkills()} disabled={pending || selected.size === 0 || !namespace.trim() || !knowledgeSpaceId}>
                      {importMutation.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <FileCode2 aria-hidden="true" />}
                      {importMutation.isPending ? "Creating Drafts" : `Import ${selected.size} Skill${selected.size === 1 ? "" : "s"}`}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ) : error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}

            {result ? <ImportResults result={result} /> : null}
          </div>

          <Card className="h-fit gap-0 border-dashed bg-surface-subtle py-0 shadow-none xl:sticky xl:top-24">
            <CardContent className="space-y-5 p-6 text-sm leading-6 text-content-secondary">
              <div className="flex gap-3"><GitBranch className="mt-0.5 size-4 shrink-0 text-content-primary" aria-hidden="true" /><p>Preview resolves a branch or tag to a full commit SHA. Import fetches that exact SHA again.</p></div>
              <div className="flex gap-3"><LockKeyhole className="mt-0.5 size-4 shrink-0 text-content-primary" aria-hidden="true" /><p>Private repositories use only GitHub App connections enabled by an administrator. Credentials never enter the browser.</p></div>
              <div className="flex gap-3"><RefreshCw className="mt-0.5 size-4 shrink-0 text-content-primary" aria-hidden="true" /><p>Each selected Skill creates its own Draft. One conflict does not roll back the successful imports.</p></div>
            </CardContent>
          </Card>
        </div>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}

function ImportResults({ result }: { result: ImportResult }) {
  const imported = (result.skills ?? []).filter((skill) => skill.imported)
  const failed = (result.skills ?? []).filter((skill) => !skill.imported)
  return (
    <Card className="gap-0 bg-surface-raised py-0 shadow-none" aria-live="polite">
      <CardHeader className="border-b border-border-subtle px-6 py-5">
        <CardTitle>Import result</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5 p-6">
        {imported.length > 0 ? (
          <section className="space-y-3" aria-label="Imported Skills">
            <h3 className="flex items-center gap-2 font-semibold text-status-success-content"><CheckCircle2 className="size-4" aria-hidden="true" />{imported.length} Draft{imported.length === 1 ? "" : "s"} created</h3>
            <div className="grid gap-2 sm:grid-cols-2">
              {imported.map((skill) => skill.asset?.id ? (
                <Button key={skill.path} variant="outline" asChild className="h-auto justify-between py-3">
                  <Link to="/assets/$assetId/governance" params={{ assetId: skill.asset.id }}><span className="truncate">{skill.asset.slug || skill.path}</span><span>Open Draft</span></Link>
                </Button>
              ) : null)}
            </div>
          </section>
        ) : null}
        {failed.length > 0 ? (
          <section className="space-y-3" aria-label="Skills not imported">
            <h3 className="flex items-center gap-2 font-semibold text-status-warning-content"><AlertTriangle className="size-4" aria-hidden="true" />{failed.length} not imported</h3>
            <div className="space-y-2">{failed.map((skill) => <Alert key={skill.path}><AlertDescription><strong>{skill.path}</strong>: {skill.errorMessage}</AlertDescription></Alert>)}</div>
          </section>
        ) : null}
      </CardContent>
    </Card>
  )
}

import { useMutation, useQuery } from "@tanstack/react-query"
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  ExternalLink,
  FileText,
  GitBranchPlus,
  GitMerge,
  Pencil,
  Scissors,
  Trash2,
  X,
} from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"

import { SplitLayout } from "@/components/layouts/split-layout"
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import {
  curateGraphEntityMutation,
  curateGraphRelationMutation,
  mergeGraphIdentityMutation,
  suppressGraphIdentityMutation,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import { readCitationExcerpt } from "@/lib/hey-api"
import type { CitationEvidenceExcerpt } from "@/lib/hey-api"
import type {
  Entity,
  EvidenceReference,
  KnowledgeGraphView,
  Relation,
} from "@/lib/hey-api/types.gen"

export function GraphPropertiesPanel({
  graph,
  selectedEntity,
  selectedRelation,
  onSelectEntity,
  onClose,
  onExpandEntity,
  onHideEntity,
  onChanged,
}: {
  graph: KnowledgeGraphView
  selectedEntity: Entity | null
  selectedRelation: Relation | null
  onSelectEntity: (entityId: string) => void
  onClose: () => void
  onExpandEntity: (entity: Entity) => Promise<void>
  onHideEntity: (entityId: string) => void
  onChanged: () => Promise<unknown>
}) {
  if (!selectedEntity && !selectedRelation) return null

  return (
    <SplitLayout.Aside className="absolute right-3 top-3 z-(--z-detail-panel) max-h-[calc(100%-1.5rem)] w-[min(var(--detail-panel-width),calc(100%-1.5rem))] rounded-lg border border-border-default bg-background/95 shadow-lg backdrop-blur lg:relative lg:right-auto lg:top-auto lg:max-h-none lg:rounded-none lg:border-y-0 lg:border-r-0 lg:bg-surface-base lg:shadow-none lg:backdrop-blur-none">
      <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b bg-background/95 px-5 py-4 backdrop-blur">
        <div className="min-w-0">
          <Badge variant="muted" className="mb-2">
            {selectedEntity ? readableGraphLabel(selectedEntity.type ?? "Entity") : "Relation"}
          </Badge>
          <h2 className="truncate text-base font-semibold leading-tight">
            {selectedEntity?.name ?? readableGraphLabel(selectedRelation?.type ?? "Graph property")}
          </h2>
        </div>
        <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label="Close properties">
          <X className="size-4" />
        </Button>
      </div>

      <div className="px-5 py-4">
        {selectedEntity ? (
          <EntityProperties
            graph={graph}
            entity={selectedEntity}
            onSelectEntity={onSelectEntity}
            onClose={onClose}
            onExpandEntity={onExpandEntity}
            onHideEntity={onHideEntity}
            onChanged={onChanged}
          />
        ) : null}
        {selectedRelation ? (
          <RelationProperties
            graph={graph}
            relation={selectedRelation}
            onSelectEntity={onSelectEntity}
            onClose={onClose}
            onChanged={onChanged}
          />
        ) : null}
      </div>
    </SplitLayout.Aside>
  )
}

function EntityProperties({
  graph,
  entity,
  onSelectEntity,
  onClose,
  onExpandEntity,
  onHideEntity,
  onChanged,
}: {
  graph: KnowledgeGraphView
  entity: Entity
  onSelectEntity: (entityId: string) => void
  onClose: () => void
  onExpandEntity: (entity: Entity) => Promise<void>
  onHideEntity: (entityId: string) => void
  onChanged: () => Promise<unknown>
}) {
  const relations = (graph.relations ?? []).filter(
    (relation) => relation.sourceEntityId === entity.id || relation.targetEntityId === entity.id,
  )
  return (
    <>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => void onExpandEntity(entity)}
        >
          <GitBranchPlus className="size-4" />
          Expand neighbors
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => entity.id && onHideEntity(entity.id)}
        >
          <Scissors className="size-4" />
          Hide
        </Button>
      </div>
      <section className="mt-5" aria-labelledby="entity-about-heading">
        <h3 id="entity-about-heading" className="text-sm font-semibold">About</h3>
        <p className="mt-2 text-sm leading-6 text-content-secondary">
          {entity.description || "No description is available for this entity."}
        </p>
      </section>
      <PropertyActions
        graph={graph}
        kind="ENTITY"
        identity={entity}
        onClose={onClose}
        onChanged={onChanged}
      />
      <Separator className="my-5" />
      <PropertyHeading label="Connections" value={String(relations.length)} />
      <div className="mt-2 divide-y divide-border-subtle">
        {relations.length ? (
          relations.map((relation) => {
            const otherId =
              relation.sourceEntityId === entity.id
                ? relation.targetEntityId
                : relation.sourceEntityId
            const other = graph.entities?.find((candidate) => candidate.id === otherId)
            const outgoing = relation.sourceEntityId === entity.id
            return (
              <button
                key={relation.id}
                type="button"
                className="group flex w-full items-start gap-3 rounded-md px-2 py-3 text-left hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                onClick={() => otherId && onSelectEntity(otherId)}
              >
                {outgoing ? (
                  <ArrowUpRight className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                ) : (
                  <ArrowDownLeft className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                )}
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">
                    {other?.name ?? "Visible entity"}
                  </span>
                  <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                    {outgoing ? "Outgoing" : "Incoming"} · {readableGraphLabel(relation.type ?? "Related")}
                  </span>
                </span>
                <ArrowRight className="mt-0.5 size-4 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" />
              </button>
            )
          })
        ) : (
          <p className="text-sm text-muted-foreground">No visible connections.</p>
        )}
      </div>
      <EvidenceLinks citationIds={entity.citationChunkIds ?? []} />
    </>
  )
}

function RelationProperties({
  graph,
  relation,
  onSelectEntity,
  onClose,
  onChanged,
}: {
  graph: KnowledgeGraphView
  relation: Relation
  onSelectEntity: (entityId: string) => void
  onClose: () => void
  onChanged: () => Promise<unknown>
}) {
  const source = graph.entities?.find((entity) => entity.id === relation.sourceEntityId)
  const target = graph.entities?.find((entity) => entity.id === relation.targetEntityId)
  return (
    <>
      <section aria-labelledby="relation-about-heading">
        <h3 id="relation-about-heading" className="text-sm font-semibold">About</h3>
        <p className="mt-2 text-sm leading-6 text-content-secondary">
          {relation.description || "No description is available for this relation."}
        </p>
      </section>
      <PropertyActions
        graph={graph}
        kind="RELATION"
        identity={relation}
        onClose={onClose}
        onChanged={onChanged}
      />
      <Separator className="my-4" />
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <EndpointButton entity={source} onSelectEntity={onSelectEntity} />
        <ArrowRight className="size-4 text-muted-foreground" />
        <EndpointButton entity={target} onSelectEntity={onSelectEntity} />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <PropertyLabel label="Weight" value={(relation.weight ?? 1).toFixed(2)} />
        </div>
        <div>
          <PropertyLabel label="Keywords" value={String(relation.keywords?.length ?? 0)} />
        </div>
      </div>
      {relation.keywords?.length ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {relation.keywords.map((keyword) => (
            <Badge key={keyword} variant="muted">
              {keyword}
            </Badge>
          ))}
        </div>
      ) : null}
      <EvidenceLinks citationIds={relation.citationChunkIds ?? []} />
    </>
  )
}

function PropertyActions({
  graph,
  kind,
  identity,
  onClose,
  onChanged,
}: {
  graph: KnowledgeGraphView
  kind: "ENTITY" | "RELATION"
  identity: Entity | Relation
  onClose: () => void
  onChanged: () => Promise<unknown>
}) {
  if (!graph.canCurate) return null
  const evidence = identity.governingEvidence
  if (!isCompleteEvidence(evidence) || !identity.id || !graph.knowledgeSpaceId) return null

  return (
    <div className="mt-4 flex flex-wrap gap-2">
      <EditIdentityDialog
        key={`${kind}-${identity.id}`}
        graph={graph}
        kind={kind}
        identity={identity}
        evidence={evidence}
        onChanged={onChanged}
      />
      {kind === "ENTITY" ? (
        <MergeEntityDialog
          graph={graph}
          entity={identity as Entity}
          onClose={onClose}
          onChanged={onChanged}
        />
      ) : null}
      <SuppressIdentityDialog
        graph={graph}
        kind={kind}
        identityId={identity.id}
        onClose={onClose}
        onChanged={onChanged}
      />
    </div>
  )
}

function EditIdentityDialog({
  graph,
  kind,
  identity,
  evidence,
  onChanged,
}: {
  graph: KnowledgeGraphView
  kind: "ENTITY" | "RELATION"
  identity: Entity | Relation
  evidence: Required<EvidenceReference>
  onChanged: () => Promise<unknown>
}) {
  const entity = kind === "ENTITY" ? (identity as Entity) : null
  const relation = kind === "RELATION" ? (identity as Relation) : null
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(entity?.name ?? "")
  const [type, setType] = useState(identity.type ?? "")
  const [description, setDescription] = useState(identity.description ?? "")
  const [keywords, setKeywords] = useState(relation?.keywords?.join(", ") ?? "")
  const [weight, setWeight] = useState(String(relation?.weight ?? 1))
  const [reason, setReason] = useState("")
  const entityMutation = useMutation(curateGraphEntityMutation())
  const relationMutation = useMutation(curateGraphRelationMutation())
  const pending = entityMutation.isPending || relationMutation.isPending
  const parsedWeight = Number(weight)
  const validWeight = !relation || (Number.isFinite(parsedWeight) && parsedWeight > 0)

  async function submit() {
    if (!graph.knowledgeSpaceId || !identity.id || !reason.trim() || !type.trim() || !validWeight) {
      return
    }
    try {
      const common = {
        idempotencyKey: crypto.randomUUID(),
        reason: reason.trim(),
        authorizationGeneration: graph.authorizationGeneration,
        evidence,
      }
      if (entity) {
        await entityMutation.mutateAsync({
          path: { knowledgeSpaceId: graph.knowledgeSpaceId },
          body: {
            ...common,
            entityId: identity.id,
            name: name.trim(),
            type: type.trim(),
            description: description.trim(),
          },
        })
      } else if (relation?.sourceEntityId && relation.targetEntityId) {
        await relationMutation.mutateAsync({
          path: { knowledgeSpaceId: graph.knowledgeSpaceId },
          body: {
            ...common,
            relationId: identity.id,
            sourceEntityId: relation.sourceEntityId,
            targetEntityId: relation.targetEntityId,
            type: type.trim(),
            description: description.trim(),
            keywords: keywords
              .split(",")
              .map((keyword) => keyword.trim())
              .filter(Boolean),
            weight: parsedWeight,
          },
        })
      }
      toast.success(`${kind === "ENTITY" ? "Entity" : "Relation"} curation saved`)
      setOpen(false)
      await onChanged()
    } catch {
      toast.error("The graph curation could not be saved")
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Pencil className="size-4" />
          Edit
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit {kind === "ENTITY" ? "entity" : "relation"}</DialogTitle>
          <DialogDescription>
            This creates an audited, reversible curation over extracted evidence.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          {entity ? <Field label="Name" value={name} onChange={setName} /> : null}
          <Field label="Type" value={type} onChange={setType} />
          <div className="grid gap-2">
            <Label htmlFor="graph-description">Description</Label>
            <Textarea
              id="graph-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          {relation ? (
            <>
              <Field label="Keywords" value={keywords} onChange={setKeywords} />
              <Field label="Weight" value={weight} onChange={setWeight} type="number" />
              {!validWeight ? (
                <p className="text-sm text-destructive">Weight must be a positive number.</p>
              ) : null}
            </>
          ) : null}
          <Field label="Reason" value={reason} onChange={setReason} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            onClick={submit}
            disabled={
              pending ||
              !reason.trim() ||
              !type.trim() ||
              !validWeight ||
              (entity ? !name.trim() : false)
            }
          >
            Save curation
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function MergeEntityDialog({
  graph,
  entity,
  onClose,
  onChanged,
}: {
  graph: KnowledgeGraphView
  entity: Entity
  onClose: () => void
  onChanged: () => Promise<unknown>
}) {
  const candidates = useMemo(
    () => (graph.entities ?? []).filter((candidate) => candidate.id && candidate.id !== entity.id),
    [entity.id, graph.entities],
  )
  const [open, setOpen] = useState(false)
  const [targetId, setTargetId] = useState("")
  const [reason, setReason] = useState("")
  const mutation = useMutation(mergeGraphIdentityMutation())

  async function submit() {
    if (!graph.knowledgeSpaceId || !entity.id || !targetId || !reason.trim()) return
    try {
      await mutation.mutateAsync({
        path: { knowledgeSpaceId: graph.knowledgeSpaceId },
        body: {
          idempotencyKey: crypto.randomUUID(),
          reason: reason.trim(),
          authorizationGeneration: graph.authorizationGeneration,
          kind: "ENTITY",
          sourceIdentityId: entity.id,
          targetIdentityId: targetId,
        },
      })
      toast.success("Entity alias created")
      setOpen(false)
      onClose()
      await onChanged()
    } catch {
      toast.error("The entity alias could not be created")
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" disabled={!candidates.length}>
          <GitMerge className="size-4" />
          Merge
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Merge entity identity</DialogTitle>
          <DialogDescription>
            The source becomes a reversible alias. Extracted evidence is not deleted.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid gap-2">
            <Label>Target entity</Label>
            <Select value={targetId} onValueChange={setTargetId}>
              <SelectTrigger>
                <SelectValue placeholder="Choose a visible entity" />
              </SelectTrigger>
              <SelectContent>
                {candidates.map((candidate) => (
                  <SelectItem key={candidate.id} value={candidate.id!}>
                    {candidate.name ?? candidate.id}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Field label="Reason" value={reason} onChange={setReason} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={mutation.isPending || !targetId || !reason.trim()}>
            Create alias
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function SuppressIdentityDialog({
  graph,
  kind,
  identityId,
  onClose,
  onChanged,
}: {
  graph: KnowledgeGraphView
  kind: "ENTITY" | "RELATION"
  identityId: string
  onClose: () => void
  onChanged: () => Promise<unknown>
}) {
  const [reason, setReason] = useState("")
  const mutation = useMutation(suppressGraphIdentityMutation())

  async function submit() {
    if (!graph.knowledgeSpaceId || !reason.trim()) return
    try {
      await mutation.mutateAsync({
        path: { knowledgeSpaceId: graph.knowledgeSpaceId },
        body: {
          idempotencyKey: crypto.randomUUID(),
          reason: reason.trim(),
          authorizationGeneration: graph.authorizationGeneration,
          kind,
          identityId,
        },
      })
      toast.success(`${kind === "ENTITY" ? "Entity" : "Relation"} suppressed`)
      onClose()
      await onChanged()
    } catch {
      toast.error("The graph identity could not be suppressed")
    }
  }

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="ghost" size="sm" className="text-status-danger-content">
          <Trash2 className="size-4" />
          Suppress
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Suppress this graph identity?</AlertDialogTitle>
          <AlertDialogDescription>
            It disappears from the effective graph, while source evidence and the audit trail
            remain.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="suppression-reason">Reason</Label>
          <Input
            id="suppression-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={submit}
            disabled={mutation.isPending || !reason.trim()}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            Suppress
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function EndpointButton({
  entity,
  onSelectEntity,
}: {
  entity: Entity | undefined
  onSelectEntity: (entityId: string) => void
}) {
  return (
    <button
      type="button"
      disabled={!entity?.id}
      onClick={() => entity?.id && onSelectEntity(entity.id)}
      className="min-w-0 rounded-lg border p-2 text-left hover:bg-accent disabled:pointer-events-none"
    >
      <span className="block truncate text-sm font-medium">{entity?.name ?? "Entity"}</span>
      <span className="block truncate text-xs text-muted-foreground">{entity?.type}</span>
    </button>
  )
}

function EvidenceLinks({ citationIds }: { citationIds: string[] }) {
  if (!citationIds.length) return null
  return (
    <div className="mt-5 border-t pt-4">
      <PropertyHeading label="Evidence" value={String(citationIds.length)} />
      <div className="mt-2 space-y-1">
        {citationIds.map((citationId) => (
          <EvidenceLink key={citationId} citationId={citationId} />
        ))}
      </div>
    </div>
  )
}

function EvidenceLink({ citationId }: { citationId: string }) {
  const excerpt = useQuery({
    queryKey: ["graph-citation-excerpt", citationId],
    queryFn: async (): Promise<CitationEvidenceExcerpt> => {
      const { data } = await readCitationExcerpt({
        path: { chunkId: citationId },
        throwOnError: true,
      })
      return data
    },
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  })

  if (excerpt.isError) {
    return (
      <div className="flex items-center gap-3 rounded-md px-2 py-3 text-sm text-muted-foreground">
        <FileText className="size-4 shrink-0" aria-hidden="true" />
        Evidence unavailable
      </div>
    )
  }

  return (
    <Button variant="ghost" className="h-auto w-full justify-start gap-3 px-2 py-3 text-left" asChild>
      <a href={`/api/citations/${citationId}/content`} target="_blank" rel="noreferrer">
        <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium">
            {excerpt.isPending ? "Loading evidence…" : excerpt.data?.title || "Evidence"}
          </span>
          {excerpt.data ? (
            <span className="mt-0.5 block truncate text-xs font-normal text-muted-foreground">
              {citationLocation(excerpt.data)}
            </span>
          ) : null}
        </span>
        <ExternalLink className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
      </a>
    </Button>
  )
}

function PropertyHeading({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <h3 className="text-sm font-semibold">{label}</h3>
      <Badge variant="muted" className="min-w-6 justify-center tabular-nums">{value}</Badge>
    </div>
  )
}

function PropertyLabel({ label, value }: { label: string; value: string }) {
  return (
    <p className="text-xs text-muted-foreground">
      {label} <span className="ml-1 font-medium text-foreground">{value}</span>
    </p>
  )
}

function citationLocation(excerpt: CitationEvidenceExcerpt) {
  if (excerpt.heading) return excerpt.heading
  if (excerpt.startPage && excerpt.endPage && excerpt.startPage !== excerpt.endPage) {
    return `Pages ${excerpt.startPage}–${excerpt.endPage}`
  }
  if (excerpt.startPage) return `Page ${excerpt.startPage}`
  return "Permission-verified excerpt"
}

function readableGraphLabel(value: string) {
  const spaced = value.trim().replaceAll(/[_-]+/g, " ").replaceAll(/\s+/g, " ")
  if (!spaced || spaced !== spaced.toLocaleUpperCase()) return spaced || "Entity"
  const lower = spaced.toLocaleLowerCase()
  return lower.charAt(0).toLocaleUpperCase() + lower.slice(1)
}

function Field({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
}) {
  const id = `graph-field-${label.toLowerCase().replaceAll(" ", "-")}`
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={type} value={value} onChange={(event) => onChange(event.target.value)} />
    </div>
  )
}

function isCompleteEvidence(
  evidence: EvidenceReference | undefined,
): evidence is Required<EvidenceReference> {
  return Boolean(
    evidence?.organizationId &&
      evidence.knowledgeAssetId &&
      evidence.sourceRevisionId &&
      evidence.chunkId &&
      evidence.aclSnapshotId &&
      evidence.aclGeneration !== undefined,
  )
}

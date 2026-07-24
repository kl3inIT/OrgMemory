import {
  ControlsContainer,
  FullScreenControl,
  SigmaContainer,
  useCamera,
  useLoadGraph,
  useRegisterEvents,
  useSetSettings,
  useSigma,
  ZoomControl,
} from "@react-sigma/core"
import "@react-sigma/core/lib/style.css"
import { useLayoutCircular } from "@react-sigma/layout-circular"
import { LayoutForceAtlas2Control } from "@react-sigma/layout-forceatlas2"
import { useQuery } from "@tanstack/react-query"
import { MultiDirectedGraph } from "graphology"
import { LoaderCircle, Network, RefreshCw, Search, X } from "lucide-react"
import { useEffect, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useGraphExplorerStore } from "@/features/sources/store/graph-explorer-store"
import {
  exploreKnowledgeGraphOptions,
  listVisibleKnowledgeSpacesOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { Entity, KnowledgeGraphView, Relation } from "@/lib/hey-api/types.gen"

const ENTITY_LIMITS = [200, 500, 1000] as const
const NODE_COLORS = ["#2563eb", "#0d9488", "#7c3aed", "#c2410c", "#be123c", "#4f46e5"]

export function KnowledgeGraphPanel() {
  const spaces = useQuery(listVisibleKnowledgeSpacesOptions())
  const selectedKnowledgeSpaceId = useGraphExplorerStore(
    (state) => state.selectedKnowledgeSpaceId,
  )
  const setSelectedKnowledgeSpaceId = useGraphExplorerStore(
    (state) => state.setSelectedKnowledgeSpaceId,
  )
  const entityLimit = useGraphExplorerStore((state) => state.entityLimit)
  const setEntityLimit = useGraphExplorerStore((state) => state.setEntityLimit)
  const [queryDraft, setQueryDraft] = useState("")
  const [query, setQuery] = useState("")
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null)

  const visibleSpaces = spaces.data ?? []
  const selectedSpace =
    visibleSpaces.find((space) => space.id === selectedKnowledgeSpaceId) ?? visibleSpaces[0]

  useEffect(() => {
    if (selectedSpace?.id && selectedSpace.id !== selectedKnowledgeSpaceId) {
      setSelectedKnowledgeSpaceId(selectedSpace.id)
    }
  }, [selectedKnowledgeSpaceId, selectedSpace?.id, setSelectedKnowledgeSpaceId])

  const graph = useQuery({
    ...exploreKnowledgeGraphOptions({
      path: { knowledgeSpaceId: selectedSpace?.id ?? "" },
      query: { q: query || undefined, entityLimit },
    }),
    enabled: Boolean(selectedSpace?.id),
  })

  const selectedEntity =
    graph.data?.entities?.find((entity) => entity.id === selectedEntityId) ?? null
  const selectedRelations = selectedEntity
    ? (graph.data?.relations ?? []).filter(
        (relation) =>
          relation.sourceEntityId === selectedEntity.id ||
          relation.targetEntityId === selectedEntity.id,
      )
    : []

  return (
    <div className="space-y-4">
      <header className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <h1 className="text-page-title text-content-primary">Knowledge graph</h1>
        <form
          className="flex flex-col gap-2 sm:flex-row"
          onSubmit={(event) => {
            event.preventDefault()
            setSelectedEntityId(null)
            setQuery(queryDraft.trim())
          }}
        >
          <Select
            value={selectedSpace?.id}
            onValueChange={(value: string) => {
              setSelectedKnowledgeSpaceId(value)
              setSelectedEntityId(null)
            }}
          >
            <SelectTrigger className="w-full sm:w-56" aria-label="Knowledge space">
              <SelectValue placeholder="Select a space" />
            </SelectTrigger>
            <SelectContent>
              {visibleSpaces.map((space) =>
                space.id ? (
                  <SelectItem key={space.id} value={space.id}>
                    {space.name ?? space.key ?? "Knowledge space"}
                  </SelectItem>
                ) : null,
              )}
            </SelectContent>
          </Select>
          <Select
            value={String(entityLimit)}
            onValueChange={(value: string) =>
              setEntityLimit(Number(value) as (typeof ENTITY_LIMITS)[number])
            }
          >
            <SelectTrigger className="w-full sm:w-32" aria-label="Maximum graph nodes">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ENTITY_LIMITS.map((limit) => (
                <SelectItem key={limit} value={String(limit)}>
                  {limit.toLocaleString()} nodes
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <InputGroup className="w-full shadow-none sm:w-72">
            <InputGroupAddon>
              <Search aria-hidden="true" />
            </InputGroupAddon>
            <InputGroupInput
              value={queryDraft}
              onChange={(event) => setQueryDraft(event.target.value)}
              placeholder="Find an entity or relation"
              aria-label="Find an entity or relation"
            />
          </InputGroup>
          <Button type="submit" disabled={!selectedSpace?.id || graph.isFetching}>
            {graph.isFetching ? (
              <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <Search className="size-4" aria-hidden="true" />
            )}
            Explore
          </Button>
        </form>
      </header>

      {spaces.isPending ? <GraphState label="Loading knowledge spaces" loading /> : null}
      {spaces.isError ? <GraphState label="Knowledge spaces could not be loaded" /> : null}
      {spaces.data?.length === 0 ? (
        <GraphState label="No knowledge space is available to this account" />
      ) : null}
      {selectedSpace && graph.isPending ? <GraphState label="Loading graph" loading /> : null}
      {selectedSpace && graph.isError ? (
        <GraphState
          label="The permission-scoped graph could not be loaded"
          action={
            <Button variant="outline" size="sm" onClick={() => graph.refetch()}>
              <RefreshCw className="size-4" aria-hidden="true" />
              Try again
            </Button>
          }
        />
      ) : null}
      {graph.data && (graph.data.entities?.length ?? 0) === 0 ? (
        <GraphState label={query ? "No graph entities match this query" : "No graph has been published yet"} />
      ) : null}
      {graph.data && (graph.data.entities?.length ?? 0) > 0 ? (
        <section className="relative h-[min(72vh,52rem)] min-h-[32rem] overflow-hidden rounded-xl border bg-card">
          <div className="absolute left-3 top-3 z-10 flex items-center gap-2">
            <Badge variant="muted">
              {(graph.data.entities?.length ?? 0).toLocaleString()} entities
            </Badge>
            <Badge variant="muted">
              {(graph.data.relations?.length ?? 0).toLocaleString()} relations
            </Badge>
            {graph.data.truncated ? <Badge variant="warning">Limited view</Badge> : null}
          </div>
          <GraphCanvas
            graph={graph.data}
            selectedEntityId={selectedEntityId}
            onSelectEntity={setSelectedEntityId}
          />
          {selectedEntity ? (
            <EntityDetails
              entity={selectedEntity}
              relations={selectedRelations}
              onClose={() => setSelectedEntityId(null)}
            />
          ) : null}
        </section>
      ) : null}
    </div>
  )
}

function GraphCanvas({
  graph,
  selectedEntityId,
  onSelectEntity,
}: {
  graph: KnowledgeGraphView
  selectedEntityId: string | null
  onSelectEntity: (entityId: string | null) => void
}) {
  return (
    <SigmaContainer
      className="h-full w-full bg-background"
      settings={{
        defaultEdgeType: "arrow",
        hideEdgesOnMove: true,
        labelDensity: 0.12,
        labelRenderedSizeThreshold: 9,
        renderEdgeLabels: false,
      }}
    >
      <LoadGraph graph={graph} />
      <GraphInteractions selectedEntityId={selectedEntityId} onSelectEntity={onSelectEntity} />
      <ControlsContainer position="bottom-right">
        <ZoomControl />
        <FullScreenControl />
        <LayoutForceAtlas2Control autoRunFor={2500} />
      </ControlsContainer>
    </SigmaContainer>
  )
}

function LoadGraph({ graph: view }: { graph: KnowledgeGraphView }) {
  const loadGraph = useLoadGraph()
  const { assign } = useLayoutCircular()
  const { reset } = useCamera({ duration: 250 })

  useEffect(() => {
    const graph = new MultiDirectedGraph()
    const entities = (view.entities ?? []).filter(
      (entity): entity is Entity & { id: string } => Boolean(entity.id),
    )
    entities.forEach((entity) => {
      graph.addNode(entity.id, {
        label: entity.name ?? "Unnamed entity",
        entityType: entity.type ?? "unknown",
        size: 7 + Math.min(entity.citationChunkIds?.length ?? 0, 8),
        color: colorFor(entity.type ?? "unknown"),
      })
    })
    for (const relation of view.relations ?? []) {
      if (
        !relation.id ||
        !relation.sourceEntityId ||
        !relation.targetEntityId ||
        !graph.hasNode(relation.sourceEntityId) ||
        !graph.hasNode(relation.targetEntityId)
      ) {
        continue
      }
      graph.addDirectedEdgeWithKey(relation.id, relation.sourceEntityId, relation.targetEntityId, {
        label: relation.type ?? "related",
        size: Math.max(0.5, Math.min(relation.weight ?? 1, 4)),
        color: "#94a3b8",
      })
    }
    loadGraph(graph)
    assign()
    requestAnimationFrame(() => reset())
  }, [assign, loadGraph, reset, view])

  return null
}

function GraphInteractions({
  selectedEntityId,
  onSelectEntity,
}: {
  selectedEntityId: string | null
  onSelectEntity: (entityId: string | null) => void
}) {
  const sigma = useSigma()
  const registerEvents = useRegisterEvents()
  const setSettings = useSetSettings()
  const [hoveredEntityId, setHoveredEntityId] = useState<string | null>(null)

  useEffect(() => {
    registerEvents({
      clickNode: ({ node }) => onSelectEntity(node),
      clickStage: () => onSelectEntity(null),
      enterNode: ({ node }) => setHoveredEntityId(node),
      leaveNode: () => setHoveredEntityId(null),
    })
  }, [onSelectEntity, registerEvents])

  useEffect(() => {
    const activeEntityId = hoveredEntityId ?? selectedEntityId
    setSettings({
      nodeReducer: (node, data) => {
        if (!activeEntityId) return data
        const related =
          node === activeEntityId || sigma.getGraph().neighbors(activeEntityId).includes(node)
        return related
          ? { ...data, highlighted: true }
          : { ...data, color: "#cbd5e1", label: "" }
      },
      edgeReducer: (edge, data) => {
        if (!activeEntityId) return data
        return sigma.getGraph().extremities(edge).includes(activeEntityId)
          ? data
          : { ...data, hidden: true }
      },
    })
  }, [hoveredEntityId, selectedEntityId, setSettings, sigma])

  return null
}

function EntityDetails({
  entity,
  relations,
  onClose,
}: {
  entity: Entity
  relations: Relation[]
  onClose: () => void
}) {
  return (
    <aside className="absolute inset-y-3 right-3 z-20 w-[min(22rem,calc(100%-1.5rem))] overflow-y-auto rounded-xl border bg-background/95 p-4 shadow-lg backdrop-blur">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold">{entity.name ?? "Unnamed entity"}</p>
          <p className="mt-1 text-xs uppercase tracking-wide text-muted-foreground">
            {entity.type ?? "Unknown type"}
          </p>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close entity details">
          <X className="size-4" aria-hidden="true" />
        </Button>
      </div>
      {entity.description ? (
        <p className="mt-4 text-sm leading-relaxed text-content-secondary">{entity.description}</p>
      ) : null}
      <div className="mt-5 space-y-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          Connections
        </p>
        {relations.length > 0 ? (
          relations.map((relation) => (
            <div key={relation.id} className="rounded-lg border border-border-subtle p-3">
              <p className="text-sm font-medium">{relation.type ?? "Related"}</p>
              {relation.description ? (
                <p className="mt-1 line-clamp-3 text-xs text-muted-foreground">
                  {relation.description}
                </p>
              ) : null}
            </div>
          ))
        ) : (
          <p className="text-sm text-muted-foreground">No visible connections.</p>
        )}
      </div>
      {(entity.citationChunkIds?.length ?? 0) > 0 ? (
        <div className="mt-5">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Evidence
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {entity.citationChunkIds?.map((citationId, index) => (
              <Button key={citationId} variant="outline" size="sm" asChild>
                <a
                  href={`/api/citations/${citationId}/content`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Source {index + 1}
                </a>
              </Button>
            ))}
          </div>
        </div>
      ) : null}
    </aside>
  )
}

function GraphState({
  label,
  loading = false,
  action,
}: {
  label: string
  loading?: boolean
  action?: React.ReactNode
}) {
  return (
    <div className="grid min-h-80 place-items-center rounded-xl border bg-card p-6 text-center">
      <div className="flex flex-col items-center gap-3">
        {loading ? (
          <LoaderCircle className="size-5 animate-spin text-muted-foreground" aria-hidden="true" />
        ) : (
          <Network className="size-6 text-muted-foreground" aria-hidden="true" />
        )}
        <p className="text-sm text-muted-foreground">{label}</p>
        {action}
      </div>
    </div>
  )
}

function colorFor(type: string) {
  let hash = 0
  for (const char of type) hash = (hash * 31 + char.charCodeAt(0)) | 0
  return NODE_COLORS[Math.abs(hash) % NODE_COLORS.length]
}

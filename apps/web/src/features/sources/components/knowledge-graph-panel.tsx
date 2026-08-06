import {
  SigmaContainer,
  useLoadGraph,
  useRegisterEvents,
  useSetSettings,
  useSigma,
} from "@react-sigma/core"
import "@react-sigma/core/lib/style.css"
import { EdgeCurvedArrowProgram } from "@sigma/edge-curve"
import { NodeBorderProgram } from "@sigma/node-border"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { MultiDirectedGraph } from "graphology"
import { LoaderCircle, Network, RefreshCw, Search } from "lucide-react"
import { useTheme } from "next-themes"
import { useEffect, useMemo, useState } from "react"
import { EdgeRectangleProgram, NodeCircleProgram, NodePointProgram } from "sigma/rendering"
import { toast } from "sonner"

import { PageLayout } from "@/components/layouts/page-layout"
import { SplitLayout } from "@/components/layouts/split-layout"
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
import { GraphPropertiesPanel } from "@/features/sources/components/graph/graph-properties-panel"
import { GraphViewerControls } from "@/features/sources/components/graph/graph-viewer-controls"
import { RenderWhenSized } from "@/features/sources/components/graph/render-when-sized"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import {
  type GraphEntityLimit,
  useGraphExplorerStore,
} from "@/features/sources/store/graph-explorer-store"
import {
  exploreKnowledgeGraphOptions,
  listVisibleKnowledgeSpacesOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"
import type { Entity, KnowledgeGraphView } from "@/lib/hey-api/types.gen"

const ENTITY_LIMITS: GraphEntityLimit[] = [200, 500, 1000]
const MAXIMUM_DEPTHS = [1, 2, 3, 4, 5, 6, 7, 8] as const
const NODE_COLORS = ["#2563eb", "#0d9488", "#7c3aed", "#c2410c", "#be123c", "#4f46e5"]

export function KnowledgeGraphPanel() {
  const queryClient = useQueryClient()
  const spaces = useQuery(listVisibleKnowledgeSpacesOptions())
  const selectedKnowledgeSpaceId = useGraphExplorerStore((state) => state.selectedKnowledgeSpaceId)
  const setSelectedKnowledgeSpaceId = useGraphExplorerStore(
    (state) => state.setSelectedKnowledgeSpaceId,
  )
  const entityLimit = useGraphExplorerStore((state) => state.entityLimit)
  const setEntityLimit = useGraphExplorerStore((state) => state.setEntityLimit)
  const maximumDepth = useGraphExplorerStore((state) => state.maximumDepth)
  const setMaximumDepth = useGraphExplorerStore((state) => state.setMaximumDepth)
  const showPropertyPanel = useGraphExplorerStore((state) => state.showPropertyPanel)
  const [queryDraft, setQueryDraft] = useState("")
  const query = useDebouncedValue(queryDraft.trim())
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null)
  const [selectedRelationId, setSelectedRelationId] = useState<string | null>(null)
  const [selectedTypes, setSelectedTypes] = useState<Set<string>>(new Set())
  const [expandedViews, setExpandedViews] = useState<KnowledgeGraphView[]>([])
  const [hiddenEntityIds, setHiddenEntityIds] = useState<Set<string>>(new Set())

  function resetExploration() {
    setSelectedEntityId(null)
    setSelectedRelationId(null)
    setSelectedTypes(new Set())
    setExpandedViews([])
    setHiddenEntityIds(new Set())
  }

  const visibleSpaces = spaces.data ?? []
  const selectedSpace =
    visibleSpaces.find((space) => space.id === selectedKnowledgeSpaceId) ?? visibleSpaces[0]

  useEffect(() => {
    if (selectedSpace?.id && selectedSpace.id !== selectedKnowledgeSpaceId) {
      setSelectedKnowledgeSpaceId(selectedSpace.id)
    }
  }, [selectedKnowledgeSpaceId, selectedSpace?.id, setSelectedKnowledgeSpaceId])

  useEffect(() => {
    setSelectedEntityId(null)
    setSelectedRelationId(null)
    setSelectedTypes(new Set())
    setExpandedViews([])
    setHiddenEntityIds(new Set())
  }, [query])

  const graph = useQuery({
    ...exploreKnowledgeGraphOptions({
      path: { knowledgeSpaceId: selectedSpace?.id ?? "" },
      query: { q: query || undefined, entityLimit, maxDepth: maximumDepth },
    }),
    enabled: Boolean(selectedSpace?.id),
  })

  const displayedGraph = useMemo(
    () => mergeGraphViews(graph.data, expandedViews, hiddenEntityIds),
    [expandedViews, graph.data, hiddenEntityIds],
  )
  const selectedEntity =
    displayedGraph?.entities?.find((entity) => entity.id === selectedEntityId) ?? null
  const selectedRelation =
    displayedGraph?.relations?.find((relation) => relation.id === selectedRelationId) ?? null
  const entityTypes = useMemo(() => {
    const byType = new Map<string, { type: string; color: string; count: number }>()
    for (const entity of displayedGraph?.entities ?? []) {
      const type = entity.type ?? "unknown"
      const current = byType.get(type)
      byType.set(type, {
        type,
        color: colorFor(type),
        count: (current?.count ?? 0) + 1,
      })
    }
    return [...byType.values()].sort((left, right) => right.count - left.count)
  }, [displayedGraph?.entities])

  useEffect(() => {
    const availableTypes = new Set(entityTypes.map(({ type }) => type))
    setSelectedTypes((current) => {
      if (current.size === 0) return current
      const next = new Set([...current].filter((type) => availableTypes.has(type)))
      return next.size === current.size ? current : next
    })
  }, [entityTypes])

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-4">
      <PageLayout.Header
        title="Knowledge graph"
        actions={
          <div
            className="flex w-full max-w-full flex-col gap-2 sm:w-auto sm:flex-row sm:flex-wrap sm:justify-end"
          >
            <Select
              value={selectedSpace?.id ?? ""}
              onValueChange={(value: string) => {
                setSelectedKnowledgeSpaceId(value)
                resetExploration()
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
              onValueChange={(value: string) => setEntityLimit(Number(value) as GraphEntityLimit)}
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
            <Select
              value={String(maximumDepth)}
              onValueChange={(value: string) => setMaximumDepth(Number(value))}
            >
              <SelectTrigger className="w-full sm:w-32" aria-label="Maximum graph depth">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MAXIMUM_DEPTHS.map((depth) => (
                  <SelectItem key={depth} value={String(depth)}>
                    Depth {depth}
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
          </div>
        }
      />

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
      {displayedGraph && (displayedGraph.entities?.length ?? 0) === 0 ? (
        <GraphState
          label={query ? "No graph entities match this query" : "No graph has been published yet"}
        />
      ) : null}
      {displayedGraph && (displayedGraph.entities?.length ?? 0) > 0 ? (
        <PageLayout.Canvas aria-label="Knowledge graph explorer">
          <SplitLayout.Root>
            <SplitLayout.Main className="relative overflow-hidden">
              <div className="absolute bottom-3 left-14 z-10 flex items-center gap-2">
                <Badge variant="muted">
                  {(displayedGraph.entities?.length ?? 0).toLocaleString()} entities
                </Badge>
                <Badge variant="muted">
                  {(displayedGraph.relations?.length ?? 0).toLocaleString()} relations
                </Badge>
                {displayedGraph.truncated ? <Badge variant="warning">Limited view</Badge> : null}
              </div>
              <GraphCanvas
                graph={displayedGraph}
                selectedEntityId={selectedEntityId}
                selectedRelationId={selectedRelationId}
                selectedTypes={selectedTypes}
                entityTypes={entityTypes}
                onSelectedTypesChange={setSelectedTypes}
                onSelectEntity={(entityId) => {
                  setSelectedEntityId(entityId)
                  if (entityId) setSelectedRelationId(null)
                }}
                onSelectRelation={(relationId) => {
                  setSelectedRelationId(relationId)
                  if (relationId) setSelectedEntityId(null)
                }}
                onRefresh={() => {
                  setExpandedViews([])
                  setHiddenEntityIds(new Set())
                  return graph.refetch()
                }}
              />
            </SplitLayout.Main>
            {showPropertyPanel && (selectedEntity || selectedRelation) ? (
              <GraphPropertiesPanel
                graph={displayedGraph}
                selectedEntity={selectedEntity}
                selectedRelation={selectedRelation}
                onSelectEntity={(entityId) => {
                  setSelectedEntityId(entityId)
                  setSelectedRelationId(null)
                }}
                onClose={() => {
                  setSelectedEntityId(null)
                  setSelectedRelationId(null)
                }}
                onExpandEntity={async (entity) => {
                  if (!selectedSpace?.id || !entity.id || !entity.name) return
                  try {
                    const expanded = await queryClient.fetchQuery(
                      exploreKnowledgeGraphOptions({
                        path: { knowledgeSpaceId: selectedSpace.id },
                        query: {
                          q: entity.name,
                          entityLimit: Math.min(1_000, entityLimit),
                          maxDepth: Math.max(2, maximumDepth),
                        },
                      }),
                    )
                    if (
                      expanded.authorizationGeneration !==
                      displayedGraph.authorizationGeneration
                    ) {
                      setExpandedViews([])
                      setHiddenEntityIds(new Set())
                      await graph.refetch()
                      toast.info("Permissions changed; the graph view was refreshed")
                      return
                    }
                    const currentIds = new Set(
                      displayedGraph.entities?.map((candidate) => candidate.id) ?? [],
                    )
                    const added = (expanded.entities ?? []).filter(
                      (candidate) => candidate.id && !currentIds.has(candidate.id),
                    ).length
                    if (added === 0) {
                      toast.info("No new visible neighbors")
                      return
                    }
                    setExpandedViews((current) => [...current, expanded])
                    toast.success(
                      `${added} visible ${added === 1 ? "neighbor" : "neighbors"} added`,
                    )
                  } catch {
                    toast.error("Visible neighbors could not be loaded")
                  }
                }}
                onHideEntity={(entityId) => {
                  const entitiesToHide = prunableEntityIds(displayedGraph, entityId)
                  if (entitiesToHide.size >= (displayedGraph.entities?.length ?? 0)) {
                    toast.error("At least one visible entity must remain")
                    return
                  }
                  setHiddenEntityIds((current) => new Set([...current, ...entitiesToHide]))
                  setSelectedEntityId(null)
                  setSelectedRelationId(null)
                  if (entitiesToHide.size > 1) {
                    toast.info(`${entitiesToHide.size} entities hidden from this view`)
                  }
                }}
                onChanged={async () => {
                  setExpandedViews([])
                  setHiddenEntityIds(new Set())
                  return graph.refetch()
                }}
              />
            ) : null}
          </SplitLayout.Root>
        </PageLayout.Canvas>
      ) : null}
    </div>
  )
}

function mergeGraphViews(
  base: KnowledgeGraphView | undefined,
  expansions: KnowledgeGraphView[],
  hiddenEntityIds: Set<string>,
): KnowledgeGraphView | undefined {
  if (!base) return undefined
  const entities = new Map(
    [base, ...expansions]
      .flatMap((view) => view.entities ?? [])
      .filter((entity): entity is Entity & { id: string } => Boolean(entity.id))
      .map((entity) => [entity.id, entity]),
  )
  const relations = new Map(
    [base, ...expansions]
      .flatMap((view) => view.relations ?? [])
      .filter((relation) => Boolean(relation.id))
      .map((relation) => [relation.id!, relation]),
  )
  for (const entityId of hiddenEntityIds) entities.delete(entityId)
  for (const [relationId, relation] of relations) {
    if (
      !relation.sourceEntityId ||
      !relation.targetEntityId ||
      !entities.has(relation.sourceEntityId) ||
      !entities.has(relation.targetEntityId)
    ) {
      relations.delete(relationId)
    }
  }
  return {
    ...base,
    authorizationGeneration: Math.max(
      base.authorizationGeneration ?? 0,
      ...expansions.map((view) => view.authorizationGeneration ?? 0),
    ),
    canCurate: [base, ...expansions].every((view) => view.canCurate),
    entities: [...entities.values()],
    relations: [...relations.values()],
    truncated: [base, ...expansions].some((view) => view.truncated),
  }
}

function prunableEntityIds(view: KnowledgeGraphView, entityId: string): Set<string> {
  const degreeByEntity = new Map<string, number>()
  const directNeighbors = new Set<string>()
  for (const relation of view.relations ?? []) {
    if (!relation.sourceEntityId || !relation.targetEntityId) continue
    degreeByEntity.set(
      relation.sourceEntityId,
      (degreeByEntity.get(relation.sourceEntityId) ?? 0) + 1,
    )
    degreeByEntity.set(
      relation.targetEntityId,
      (degreeByEntity.get(relation.targetEntityId) ?? 0) + 1,
    )
    if (relation.sourceEntityId === entityId) directNeighbors.add(relation.targetEntityId)
    if (relation.targetEntityId === entityId) directNeighbors.add(relation.sourceEntityId)
  }
  const result = new Set([entityId])
  for (const neighborId of directNeighbors) {
    if (degreeByEntity.get(neighborId) === 1) result.add(neighborId)
  }
  return result
}

function GraphCanvas({
  graph,
  selectedEntityId,
  selectedRelationId,
  selectedTypes,
  entityTypes,
  onSelectedTypesChange,
  onSelectEntity,
  onSelectRelation,
  onRefresh,
}: {
  graph: KnowledgeGraphView
  selectedEntityId: string | null
  selectedRelationId: string | null
  selectedTypes: Set<string>
  entityTypes: Array<{ type: string; color: string; count: number }>
  onSelectedTypesChange: (types: Set<string>) => void
  onSelectEntity: (entityId: string | null) => void
  onSelectRelation: (relationId: string | null) => void
  onRefresh: () => Promise<unknown>
}) {
  const { resolvedTheme } = useTheme()
  const enableEdgeEvents = useGraphExplorerStore((state) => state.enableEdgeEvents)
  const effectiveEdgeEvents = enableEdgeEvents && (graph.relations?.length ?? 0) <= 5_000
  const useCurvedEdges = (graph.relations?.length ?? 0) <= 5_000
  const searchDocuments = useMemo(
    () =>
      (graph.entities ?? [])
        .filter((entity): entity is Entity & { id: string } => Boolean(entity.id))
        .map((entity) => ({
          id: entity.id,
          label: entity.name ?? "Unnamed entity",
          entityType: entity.type ?? "unknown",
        })),
    [graph.entities],
  )
  return (
    <RenderWhenSized className="h-full w-full bg-background">
      <SigmaContainer
        key={`${resolvedTheme}-${effectiveEdgeEvents}`}
        graph={MultiDirectedGraph}
        className="h-full w-full"
        settings={{
          defaultNodeType: "border",
          defaultEdgeType: useCurvedEdges ? "curvedArrow" : "rect",
          defaultEdgeColor: resolvedTheme === "dark" ? "#475569" : "#cbd5e1",
          hideEdgesOnMove: true,
          labelGridCellSize: 60,
          labelRenderedSizeThreshold: 12,
          renderEdgeLabels: false,
          enableEdgeEvents: effectiveEdgeEvents,
          nodeProgramClasses: {
            point: NodePointProgram,
            default: NodePointProgram,
            circle: NodeCircleProgram,
            border: NodeBorderProgram,
          },
          edgeProgramClasses: {
            rect: EdgeRectangleProgram,
            curvedArrow: EdgeCurvedArrowProgram,
          },
        }}
      >
        <LoadGraph graph={graph} />
        <GraphInteractions
          selectedEntityId={selectedEntityId}
          selectedRelationId={selectedRelationId}
          selectedTypes={selectedTypes}
          onSelectEntity={onSelectEntity}
          onSelectRelation={onSelectRelation}
        />
        <GraphViewerControls
          entityTypes={entityTypes}
          searchDocuments={searchDocuments}
          selectedTypes={selectedTypes}
          onSelectedTypesChange={onSelectedTypesChange}
          onSelectEntity={onSelectEntity}
          onRefresh={onRefresh}
          edgeCount={graph.relations?.length ?? 0}
        />
      </SigmaContainer>
    </RenderWhenSized>
  )
}

function LoadGraph({ graph: view }: { graph: KnowledgeGraphView }) {
  const loadGraph = useLoadGraph()

  useEffect(() => {
    const graph = new MultiDirectedGraph()
    const entities = (view.entities ?? []).filter((entity): entity is Entity & { id: string } =>
      Boolean(entity.id),
    )
    entities.forEach((entity) => {
      if (graph.hasNode(entity.id)) return
      const { x, y } = stableNodePosition(entity.id)
      graph.addNode(entity.id, {
        label: entity.name ?? "Unnamed entity",
        entityType: entity.type ?? "unknown",
        size: 7 + Math.min(entity.citationChunkIds?.length ?? 0, 8),
        color: colorFor(entity.type ?? "unknown"),
        x,
        y,
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
      if (graph.hasEdge(relation.id)) continue
      graph.addDirectedEdgeWithKey(relation.id, relation.sourceEntityId, relation.targetEntityId, {
        label: relation.type ?? "related",
        size: Math.max(1, Math.min(relation.weight ?? 1, 5)),
        color: "#94a3b8",
        type: (view.relations?.length ?? 0) <= 5_000 ? "curvedArrow" : "rect",
      })
    }
    loadGraph(graph)
  }, [loadGraph, view])

  return null
}

/**
 * Sigma requires finite coordinates before a graph can be loaded. Keep the
 * initial layout deterministic so refreshes do not make the same graph jump.
 * ForceAtlas2 can refine these positions after the graph is mounted.
 */
function stableNodePosition(id: string): { x: number; y: number } {
  let xHash = 0xdeadbeef ^ id.length
  let yHash = 0x41c6ce57 ^ id.length

  for (let index = 0; index < id.length; index += 1) {
    const character = id.charCodeAt(index)
    xHash = Math.imul(xHash ^ character, 2654435761)
    yHash = Math.imul(yHash ^ character, 1597334677)
  }

  xHash =
    Math.imul(xHash ^ (xHash >>> 16), 2246822507) ^ Math.imul(yHash ^ (yHash >>> 13), 3266489909)
  yHash =
    Math.imul(yHash ^ (yHash >>> 16), 2246822507) ^ Math.imul(xHash ^ (xHash >>> 13), 3266489909)

  return {
    x: (xHash >>> 0) / 4294967296,
    y: (yHash >>> 0) / 4294967296,
  }
}

function GraphInteractions({
  selectedEntityId,
  selectedRelationId,
  selectedTypes,
  onSelectEntity,
  onSelectRelation,
}: {
  selectedEntityId: string | null
  selectedRelationId: string | null
  selectedTypes: Set<string>
  onSelectEntity: (entityId: string | null) => void
  onSelectRelation: (relationId: string | null) => void
}) {
  const sigma = useSigma()
  const registerEvents = useRegisterEvents()
  const setSettings = useSetSettings()
  const [hoveredEntityId, setHoveredEntityId] = useState<string | null>(null)
  const [hoveredRelationId, setHoveredRelationId] = useState<string | null>(null)
  const [draggedNode, setDraggedNode] = useState<string | null>(null)
  const showNodeLabels = useGraphExplorerStore((state) => state.showNodeLabels)
  const enableNodeDrag = useGraphExplorerStore((state) => state.enableNodeDrag)
  const showEdgeLabels = useGraphExplorerStore((state) => state.showEdgeLabels)
  const hideUnselectedEdges = useGraphExplorerStore((state) => state.hideUnselectedEdges)
  const enableEdgeEvents = useGraphExplorerStore((state) => state.enableEdgeEvents)
  const minimumEdgeSize = useGraphExplorerStore((state) => state.minimumEdgeSize)
  const maximumEdgeSize = useGraphExplorerStore((state) => state.maximumEdgeSize)
  const renderNodeLabels = showNodeLabels && sigma.getGraph().order <= 2_000

  useEffect(() => {
    registerEvents({
      clickNode: ({ node }) => {
        onSelectRelation(null)
        onSelectEntity(node)
      },
      clickEdge: ({ edge }) => {
        if (!enableEdgeEvents) return
        onSelectEntity(null)
        onSelectRelation(edge)
      },
      clickStage: () => {
        onSelectEntity(null)
        onSelectRelation(null)
      },
      enterNode: ({ node }) => setHoveredEntityId(node),
      leaveNode: () => setHoveredEntityId(null),
      enterEdge: ({ edge }) => enableEdgeEvents && setHoveredRelationId(edge),
      leaveEdge: () => setHoveredRelationId(null),
      downNode: ({ node }) => {
        if (!enableNodeDrag) return
        setDraggedNode(node)
        sigma.getGraph().setNodeAttribute(node, "highlighted", true)
      },
      mousemovebody: (event) => {
        if (!enableNodeDrag || !draggedNode) return
        const position = sigma.viewportToGraph(event)
        sigma.getGraph().mergeNodeAttributes(draggedNode, position)
        event.preventSigmaDefault()
        event.original.preventDefault()
        event.original.stopPropagation()
      },
      mouseup: () => {
        if (!draggedNode) return
        sigma.getGraph().removeNodeAttribute(draggedNode, "highlighted")
        setDraggedNode(null)
      },
      mousedown: (event) => {
        if (!enableNodeDrag) return
        const original = event.original as MouseEvent
        if (original.buttons !== 0 && !sigma.getCustomBBox()) {
          sigma.setCustomBBox(sigma.getBBox())
        }
      },
    })
  }, [
    draggedNode,
    enableEdgeEvents,
    enableNodeDrag,
    onSelectEntity,
    onSelectRelation,
    registerEvents,
    sigma,
  ])

  useEffect(() => {
    const graph = sigma.getGraph()
    const candidateEntityId = hoveredEntityId ?? selectedEntityId
    const candidateRelationId = hoveredRelationId ?? selectedRelationId
    const activeEntityId =
      candidateEntityId && graph.hasNode(candidateEntityId) ? candidateEntityId : null
    const activeRelationId =
      candidateRelationId && graph.hasEdge(candidateRelationId) ? candidateRelationId : null
    const highlightedNodes = activeRelationId
      ? new Set(graph.extremities(activeRelationId))
      : activeEntityId
        ? new Set([activeEntityId, ...graph.neighbors(activeEntityId)])
        : null
    setSettings({
      nodeReducer: (node, data) => {
        const entityType = String(data.entityType ?? "unknown")
        if (selectedTypes.size > 0 && !selectedTypes.has(entityType)) {
          return { ...data, hidden: true }
        }
        const base = renderNodeLabels ? data : { ...data, label: "" }
        if (!highlightedNodes) return base
        return highlightedNodes.has(node)
          ? { ...base, highlighted: true }
          : { ...base, color: "#cbd5e1", label: "" }
      },
      edgeReducer: (edge, data) => {
        const [source, target] = graph.extremities(edge)
        const sourceType = String(graph.getNodeAttribute(source, "entityType") ?? "unknown")
        const targetType = String(graph.getNodeAttribute(target, "entityType") ?? "unknown")
        if (
          selectedTypes.size > 0 &&
          (!selectedTypes.has(sourceType) || !selectedTypes.has(targetType))
        ) {
          return { ...data, hidden: true }
        }
        const base = {
          ...data,
          label: showEdgeLabels ? data.label : "",
          size: Math.max(minimumEdgeSize, Math.min(Number(data.size ?? 1), maximumEdgeSize)),
        }
        if (activeRelationId) {
          return edge === activeRelationId
            ? { ...base, highlighted: true, size: Math.max(Number(base.size), 2) }
            : hideUnselectedEdges
              ? { ...base, hidden: true }
              : base
        }
        if (!activeEntityId) return base
        return graph.extremities(edge).includes(activeEntityId)
          ? base
          : hideUnselectedEdges
            ? { ...base, hidden: true }
            : base
      },
    })
  }, [
    hideUnselectedEdges,
    hoveredEntityId,
    hoveredRelationId,
    maximumEdgeSize,
    minimumEdgeSize,
    selectedEntityId,
    selectedRelationId,
    selectedTypes,
    setSettings,
    showEdgeLabels,
    renderNodeLabels,
    sigma,
  ])

  return null
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
    <div className="grid min-h-80 flex-1 place-items-center rounded-xl border bg-card p-6 text-center">
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

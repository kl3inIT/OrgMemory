import { Link } from "@tanstack/react-router"
import cytoscape, { type Core, type ElementDefinition } from "cytoscape"
import fcose from "cytoscape-fcose"
import { Box, Building2, FileText, Network, Search, Tags, UserRound } from "lucide-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { PageTitle } from "@/components/layout/page-title"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { formatAssetType } from "@/features/assets/asset-type"
import { StatusBadge } from "@/features/assets/status-badge"
import { useAssets, useKnowledgeGraph } from "@/features/assets/use-assets"
import type { GraphNodeKind, KnowledgeGraph, KnowledgeGraphNode } from "@/lib/api"

let cytoscapeReady = false

const kindLabels: Record<GraphNodeKind, string> = {
  ASSET: "Asset",
  ASSET_TYPE: "Type",
  DEPARTMENT: "Department",
  USER: "Owner",
  TAG: "Tag",
}

const kindIcons: Record<GraphNodeKind, typeof FileText> = {
  ASSET: FileText,
  ASSET_TYPE: Box,
  DEPARTMENT: Building2,
  USER: UserRound,
  TAG: Tags,
}

const kindColors: Record<GraphNodeKind, string> = {
  ASSET: "#2563eb",
  ASSET_TYPE: "#16a34a",
  DEPARTMENT: "#f59e0b",
  USER: "#7c3aed",
  TAG: "#ef4444",
}

export function KnowledgeGraphPage() {
  const [query, setQuery] = useState("")
  const [focusAssetId, setFocusAssetId] = useState("")
  const [depth, setDepth] = useState("2")
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const { data, isLoading, isError } = useKnowledgeGraph({
    query,
    focusAssetId: focusAssetId || undefined,
    depth: Number(depth),
  })
  const { data: assets } = useAssets()

  const selectedNode = useMemo(() => {
    if (!data || !selectedNodeId) return null
    return data.nodes.find((node) => node.id === selectedNodeId) ?? null
  }, [data, selectedNodeId])

  const assetOptions = useMemo(() => {
    return [...(assets ?? [])].sort((a, b) => a.title.localeCompare(b.title))
  }, [assets])

  const counts = useMemo(() => countKinds(data?.nodes ?? []), [data?.nodes])
  const selectedFocusTitle = assets?.find((asset) => asset.id === focusAssetId)?.title

  return (
    <div className="space-y-6">
      <PageTitle
        title="Knowledge Graph"
        subtitle="Explore OrgMemory like an Obsidian graph: assets, owners, departments, tags, and capability types."
      />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <Card className="min-w-0">
          <CardHeader className="border-b">
            <CardTitle className="flex items-center gap-2">
              <Network className="size-5 text-primary" />
              Capability Map
            </CardTitle>
            <CardDescription>
              Force-directed graph powered by Cytoscape. Global graph by default, local graph when an asset is focused.
            </CardDescription>
            <CardAction>
              <Badge variant={focusAssetId ? "secondary" : "outline"}>
                {focusAssetId ? `Local: ${selectedFocusTitle ?? "asset"}` : "Global graph"}
              </Badge>
            </CardAction>
          </CardHeader>
          <CardContent className="space-y-4 pt-6">
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_18rem_9rem_auto] lg:items-end">
              <div className="grid gap-2">
                <Label>Search graph</Label>
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    className="pl-9"
                    value={query}
                    onChange={(event) => {
                      setQuery(event.target.value)
                      setSelectedNodeId(null)
                    }}
                    placeholder="Search asset, tag, owner, department, capability type..."
                  />
                </div>
              </div>
              <div className="grid gap-2">
                <Label>Local graph focus</Label>
                <Select
                  value={focusAssetId || "GLOBAL"}
                  onValueChange={(value) => {
                    setFocusAssetId(value === "GLOBAL" ? "" : value)
                    setSelectedNodeId(value === "GLOBAL" ? null : `asset:${value}`)
                  }}
                >
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GLOBAL">Global graph</SelectItem>
                    {assetOptions.map((asset) => (
                      <SelectItem key={asset.id} value={asset.id}>{asset.title}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label>Depth</Label>
                <Select value={depth} onValueChange={setDepth}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="1">1 hop</SelectItem>
                    <SelectItem value="2">2 hops</SelectItem>
                    <SelectItem value="3">3 hops</SelectItem>
                    <SelectItem value="4">4 hops</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <Button variant="outline" onClick={() => {
                setQuery("")
                setFocusAssetId("")
                setSelectedNodeId(null)
              }}>
                Reset
              </Button>
            </div>

            <div className="flex flex-wrap gap-2">
              {Object.entries(counts).map(([kind, count]) => (
                <Badge key={kind} variant="secondary">
                  {kindLabels[kind as GraphNodeKind]}: {count}
                </Badge>
              ))}
              <Badge variant="outline">Edges: {data?.edges.length ?? 0}</Badge>
            </div>

            <div className="h-[34rem] overflow-hidden rounded-lg border bg-muted/20">
              {isLoading ? (
                <div className="grid h-full place-items-center p-6">
                  <div className="w-full max-w-md space-y-3">
                    <Skeleton className="h-8 w-3/4" />
                    <Skeleton className="h-56 w-full" />
                    <Skeleton className="h-8 w-1/2" />
                  </div>
                </div>
              ) : null}
              {isError ? (
                <div className="grid h-full place-items-center p-6 text-sm text-destructive">
                  Could not load graph from API.
                </div>
              ) : null}
              {!isLoading && !isError && data ? (
                <CytoscapeGraph graph={data} selectedNodeId={selectedNodeId} onSelectNode={setSelectedNodeId} />
              ) : null}
            </div>
          </CardContent>
        </Card>

        <aside className="grid content-start gap-4">
          <Card>
            <CardHeader>
              <CardTitle>Selected Node</CardTitle>
              <CardDescription>Click a node to inspect it.</CardDescription>
            </CardHeader>
            <CardContent>
              <NodeInspector node={selectedNode} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Graph Semantics</CardTitle>
              <CardDescription>How OrgMemory builds links.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <LegendItem kind="ASSET" text="Capability asset: workflow, prompt, bot, guardrail, rubric, pack." />
              <LegendItem kind="ASSET_TYPE" text="Asset classification for filtering and governance." />
              <LegendItem kind="DEPARTMENT" text="Business ownership context." />
              <LegendItem kind="USER" text="Owner or backup owner continuity." />
              <LegendItem kind="TAG" text="Knowledge relation through reusable tags." />
            </CardContent>
          </Card>
        </aside>
      </div>
    </div>
  )
}

function CytoscapeGraph({
  graph,
  selectedNodeId,
  onSelectNode,
}: {
  graph: KnowledgeGraph
  selectedNodeId: string | null
  onSelectNode: (nodeId: string | null) => void
}) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const cyRef = useRef<Core | null>(null)
  const handleSelectNode = useCallback((nodeId: string | null) => onSelectNode(nodeId), [onSelectNode])

  useEffect(() => {
    if (!cytoscapeReady) {
      cytoscape.use(fcose)
      cytoscapeReady = true
    }
  }, [])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const cy = cytoscape({
      container,
      elements: buildElements(graph),
      style: graphStyles,
      minZoom: 0.2,
      maxZoom: 2.5,
      wheelSensitivity: 0.18,
      selectionType: "single",
      boxSelectionEnabled: false,
      layout: {
        name: "fcose",
        quality: graph.nodes.length > 80 ? "default" : "proof",
        randomize: true,
        animate: false,
        fit: true,
        padding: 48,
        nodeDimensionsIncludeLabels: true,
        nodeSeparation: graph.focusNodeId ? 110 : 155,
        idealEdgeLength: graph.focusNodeId ? 110 : 155,
        edgeElasticity: 0.28,
        gravity: graph.focusNodeId ? 0.18 : 0.08,
        gravityRangeCompound: 1.5,
        gravityCompound: 1.0,
        numIter: graph.focusNodeId ? 2200 : 3200,
      } as cytoscape.LayoutOptions,
    })

    cy.on("tap", "node", (event) => {
      handleSelectNode(event.target.id())
    })
    cy.on("tap", (event) => {
      if (event.target === cy) {
        handleSelectNode(null)
      }
    })
    cy.ready(() => {
      cy.fit(undefined, 56)
    })
    cyRef.current = cy

    return () => {
      cy.destroy()
      cyRef.current = null
    }
  }, [graph, handleSelectNode])

  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return
    cy.nodes().unselect()
    if (selectedNodeId) {
      const node = cy.$id(selectedNodeId)
      node.select()
      if (node.length) {
        cy.animate({ center: { eles: node }, zoom: Math.max(cy.zoom(), 0.85) }, { duration: 220 })
      }
    }
  }, [selectedNodeId])

  return <div ref={containerRef} data-testid="knowledge-graph-canvas" className="h-full w-full" />
}

function NodeInspector({ node }: { node: KnowledgeGraphNode | null }) {
  if (!node) {
    return <p className="text-sm text-muted-foreground">No node selected.</p>
  }

  const Icon = kindIcons[node.kind]
  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
          <Icon className="size-5" />
        </span>
        <div className="min-w-0">
          <div className="font-medium">{node.label}</div>
          <div className="text-sm text-muted-foreground">{kindLabels[node.kind]}</div>
        </div>
      </div>
      <Separator />
      <ScrollArea className="max-h-40 pr-3">
        <p className="text-sm leading-6 text-muted-foreground">{node.detail ?? "No detail captured."}</p>
      </ScrollArea>
      <div className="flex flex-wrap gap-2">
        {node.assetType ? <Badge variant="outline">{formatAssetType(node.assetType)}</Badge> : null}
        {node.status ? <StatusBadge status={node.status} /> : null}
        <Badge variant="secondary">weight {node.weight}</Badge>
      </div>
      {node.assetId ? (
        <Button asChild className="w-full">
          <Link to="/assets/$assetId" params={{ assetId: node.assetId }}>Open Asset Detail</Link>
        </Button>
      ) : null}
    </div>
  )
}

function LegendItem({ kind, text }: { kind: GraphNodeKind; text: string }) {
  const Icon = kindIcons[kind]
  return (
    <div className="flex gap-3">
      <span className="grid size-8 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
        <Icon className="size-4" />
      </span>
      <div>
        <div className="font-medium">{kindLabels[kind]}</div>
        <p className="text-muted-foreground">{text}</p>
      </div>
    </div>
  )
}

function buildElements(graph: KnowledgeGraph): ElementDefinition[] {
  return [
    ...graph.nodes.map((node) => ({
      group: "nodes" as const,
      data: {
        id: node.id,
        label: truncateLabel(node.label, node.kind === "ASSET" ? 34 : 24),
        fullLabel: node.label,
        kind: node.kind,
        detail: node.detail,
        size: nodeSize(node),
        color: kindColors[node.kind],
        borderColor: node.status === "IN_REVIEW" ? "#f97316" : node.status === "APPROVED" ? "#22c55e" : kindColors[node.kind],
      },
      classes: node.kind.toLowerCase().replace("_", "-"),
    })),
    ...graph.edges.map((edge) => ({
      group: "edges" as const,
      data: {
        id: edge.id,
        source: edge.source,
        target: edge.target,
        label: edge.label,
        kind: edge.kind,
        width: Math.max(1, Math.min(4, edge.weight)),
      },
      classes: edge.kind.toLowerCase().replaceAll("_", "-"),
    })),
  ]
}

const graphStyles: cytoscape.StylesheetStyle[] = [
  {
    selector: "node",
    style: {
      width: "data(size)",
      height: "data(size)",
      label: "data(label)",
      "background-color": "data(color)",
      "border-color": "data(borderColor)",
      "border-width": 2,
      color: "#1f2937",
      "font-size": 10,
      "font-weight": 600,
      "text-wrap": "wrap",
      "text-max-width": "96px",
      "text-valign": "bottom",
      "text-halign": "center",
      "text-margin-y": 8,
      "overlay-opacity": 0,
      "transition-property": "background-color, border-width, width, height",
      "transition-duration": 160,
    },
  },
  {
    selector: "node.asset",
    style: {
      shape: "ellipse",
      "font-size": 11,
      "text-max-width": "128px",
    },
  },
  {
    selector: "node.asset-type",
    style: {
      shape: "round-rectangle",
    },
  },
  {
    selector: "node.department",
    style: {
      shape: "diamond",
    },
  },
  {
    selector: "node.user",
    style: {
      shape: "hexagon",
    },
  },
  {
    selector: "node.tag",
    style: {
      shape: "round-tag",
      "font-size": 9,
      "text-max-width": "78px",
    },
  },
  {
    selector: "node:selected",
    style: {
      "border-width": 5,
      "border-color": "#111827",
      "overlay-opacity": 0.12,
      "overlay-color": "#2563eb",
    },
  },
  {
    selector: "edge",
    style: {
      width: "data(width)",
      "line-color": "#94a3b8",
      "curve-style": "bezier",
      opacity: 0.42,
      "text-rotation": "autorotate",
      "font-size": 8,
      color: "#64748b",
      label: "data(label)",
      "text-background-color": "#ffffff",
      "text-background-opacity": 0.7,
      "text-background-padding": "2px",
    },
  },
  {
    selector: "edge.related-by-tag, edge.related-by-owner, edge.related-by-process",
    style: {
      "line-style": "dashed",
      opacity: 0.25,
      label: "",
    },
  },
  {
    selector: "edge.owned-by, edge.backed-up-by",
    style: {
      "line-color": "#7c3aed",
      opacity: 0.58,
    },
  },
  {
    selector: "edge.has-type",
    style: {
      "line-color": "#16a34a",
      opacity: 0.52,
    },
  },
  {
    selector: "edge.tagged-with",
    style: {
      "line-color": "#ef4444",
      opacity: 0.35,
      label: "",
    },
  },
]

function nodeSize(node: KnowledgeGraphNode) {
  const base = node.kind === "ASSET" ? 42 : node.kind === "TAG" ? 22 : 30
  return Math.min(72, base + Math.log2(Math.max(1, node.weight)) * 7)
}

function truncateLabel(label: string, maxLength: number) {
  return label.length <= maxLength ? label : `${label.slice(0, maxLength - 1)}...`
}

function countKinds(nodes: KnowledgeGraphNode[]) {
  return nodes.reduce<Record<GraphNodeKind, number>>(
    (counts, node) => {
      counts[node.kind] += 1
      return counts
    },
    { ASSET: 0, ASSET_TYPE: 0, DEPARTMENT: 0, USER: 0, TAG: 0 },
  )
}

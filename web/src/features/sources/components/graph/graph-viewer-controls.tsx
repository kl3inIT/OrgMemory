import { FullScreenControl, useCamera, useSigma } from "@react-sigma/core"
import { useLayoutCirclepack } from "@react-sigma/layout-circlepack"
import { useLayoutCircular } from "@react-sigma/layout-circular"
import { useLayoutRandom } from "@react-sigma/layout-random"
import forceAtlas2 from "graphology-layout-forceatlas2"
import ForceAtlas2Supervisor from "graphology-layout-forceatlas2/worker"
import ForceSupervisor from "graphology-layout-force/worker"
import NoverlapSupervisor from "graphology-layout-noverlap/worker"
import {
  CircleGauge,
  Expand,
  Eye,
  Grip,
  ListFilter,
  LocateFixed,
  Minimize,
  Minus,
  Pause,
  Play,
  Plus,
  RotateCcw,
  RotateCw,
  RefreshCw,
  Search,
  Settings2,
  Tags,
  X,
} from "lucide-react"
import MiniSearch from "minisearch"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { useGraphExplorerStore } from "@/features/sources/store/graph-explorer-store"
import { cn } from "@/lib/utils"

type LayoutName =
  | "Circular"
  | "Circlepack"
  | "Random"
  | "Noverlaps"
  | "Force Directed"
  | "Force Atlas"

type LayoutSupervisor = {
  start: () => void
  stop: () => void
  kill: () => void
  isRunning: () => boolean
}

const WORKER_LAYOUTS = new Set<LayoutName>(["Noverlaps", "Force Directed", "Force Atlas"])
const LAYOUT_NAMES: LayoutName[] = [
  "Circular",
  "Circlepack",
  "Random",
  "Noverlaps",
  "Force Directed",
  "Force Atlas",
]

export function GraphViewerControls({
  entityTypes,
  searchDocuments,
  selectedTypes,
  onSelectedTypesChange,
  onSelectEntity,
  onRefresh,
  edgeCount,
}: {
  entityTypes: Array<{ type: string; color: string; count: number }>
  searchDocuments: Array<{ id: string; label: string; entityType: string }>
  selectedTypes: Set<string>
  onSelectedTypesChange: (types: Set<string>) => void
  onSelectEntity: (entityId: string | null) => void
  onRefresh: () => Promise<unknown>
  edgeCount: number
}) {
  const [refreshing, setRefreshing] = useState(false)
  return (
    <>
      <div className="absolute left-3 top-3 z-20 flex max-w-[calc(100%-1.5rem)] items-start gap-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              className="bg-background/90 shadow-sm"
              disabled={refreshing}
              onClick={() => {
                setRefreshing(true)
                void onRefresh().finally(() => setRefreshing(false))
              }}
            >
              <RefreshCw className={cn("size-4", refreshing && "animate-spin")} />
              <span className="sr-only">Refresh current graph</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent side="bottom">Refresh current graph</TooltipContent>
        </Tooltip>
        <GraphTypeFilter
          entityTypes={entityTypes}
          selectedTypes={selectedTypes}
          onSelectedTypesChange={onSelectedTypesChange}
        />
        <GraphNodeSearch searchDocuments={searchDocuments} onSelectEntity={onSelectEntity} />
      </div>
      <div className="absolute bottom-3 left-3 z-20 flex flex-col overflow-hidden rounded-lg border bg-background/90 shadow-sm backdrop-blur">
        <GraphLayoutControl />
        <GraphCameraControls />
        <GraphLegendControl entityTypes={entityTypes} />
        <GraphSettingsControl edgeCount={edgeCount} />
      </div>
    </>
  )
}

function GraphNodeSearch({
  searchDocuments,
  onSelectEntity,
}: {
  searchDocuments: Array<{ id: string; label: string; entityType: string }>
  onSelectEntity: (entityId: string | null) => void
}) {
  const showSearchBar = useGraphExplorerStore((state) => state.showSearchBar)
  const sigma = useSigma()
  const { gotoNode } = useCamera({ duration: 350 })
  const [query, setQuery] = useState("")
  const [open, setOpen] = useState(false)

  const searchEngine = useMemo(() => {
    const engine = new MiniSearch<{ id: string; label: string; entityType: string }>({
      idField: "id",
      fields: ["label", "entityType"],
      storeFields: ["label", "entityType"],
      searchOptions: { prefix: true, fuzzy: 0.2, boost: { label: 2 } },
    })
    engine.addAll(searchDocuments)
    return engine
  }, [searchDocuments])

  const results = useMemo(() => {
    if (!query.trim()) return []
    const normalizedQuery = query.trim().toLocaleLowerCase()
    const ranked = searchEngine.search(query.trim())
    const matchedIds = new Set(ranked.map((result) => String(result.id)))
    const middleMatches = searchDocuments
      .filter(
        (document) =>
          !matchedIds.has(document.id) &&
          document.label.toLocaleLowerCase().includes(normalizedQuery),
      )
      .map((document) => ({ ...document, score: 0 }))
    return [...ranked, ...middleMatches].slice(0, 10)
  }, [query, searchDocuments, searchEngine])

  if (!showSearchBar) return null

  return (
    <div className="relative w-72 max-w-[60vw]">
      <div className="relative">
        <Search
          className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={query}
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(true)
          }}
          className="h-9 bg-background/90 pl-9 pr-8 shadow-sm backdrop-blur"
          placeholder="Search visible nodes"
          aria-label="Search visible graph nodes"
        />
        {query ? (
          <button
            type="button"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            onClick={() => {
              setQuery("")
              setOpen(false)
            }}
            aria-label="Clear node search"
          >
            <X className="size-4" />
          </button>
        ) : null}
      </div>
      {open && query.trim() ? (
        <div className="absolute mt-1 max-h-72 w-full overflow-y-auto rounded-md border bg-popover p-1 shadow-md">
          {results.length ? (
            results.map((result) => (
              <button
                key={String(result.id)}
                type="button"
                className="flex w-full items-center gap-2 rounded-sm px-2 py-2 text-left text-sm hover:bg-accent"
                onMouseEnter={() => onSelectEntity(String(result.id))}
                onClick={() => {
                  const id = String(result.id)
                  onSelectEntity(id)
                  gotoNode(id)
                  setOpen(false)
                }}
              >
                <span
                  className="size-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: String(sigma.getGraph().getNodeAttribute(String(result.id), "color")) }}
                />
                <span className="min-w-0 flex-1 truncate">{String(result.label ?? result.id)}</span>
                <span className="text-xs text-muted-foreground">
                  {String(result.entityType ?? "")}
                </span>
              </button>
            ))
          ) : (
            <p className="px-2 py-3 text-sm text-muted-foreground">No visible node found.</p>
          )}
        </div>
      ) : null}
    </div>
  )
}

function GraphTypeFilter({
  entityTypes,
  selectedTypes,
  onSelectedTypesChange,
}: {
  entityTypes: Array<{ type: string; color: string; count: number }>
  selectedTypes: Set<string>
  onSelectedTypesChange: (types: Set<string>) => void
}) {
  const [query, setQuery] = useState("")
  const filteredTypes = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) return entityTypes
    return entityTypes.filter(({ type }) => type.toLowerCase().includes(needle))
  }, [entityTypes, query])

  return (
    <DropdownMenu>
      <Tooltip>
        <TooltipTrigger asChild>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="icon-sm" className="bg-background/90 shadow-sm">
              <ListFilter className="size-4" />
              <span className="sr-only">Filter entity types</span>
            </Button>
          </DropdownMenuTrigger>
        </TooltipTrigger>
        <TooltipContent side="right">Filter entity types</TooltipContent>
      </Tooltip>
      <DropdownMenuContent align="start" className="max-h-80 w-64">
        <DropdownMenuLabel className="flex items-center justify-between">
          Entity types
          <button
            type="button"
            className="text-xs font-normal text-muted-foreground hover:text-foreground"
            onClick={() => onSelectedTypesChange(new Set())}
          >
            Show all
          </button>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <div className="px-1 pb-1">
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => event.stopPropagation()}
            placeholder="Search entity types"
            aria-label="Search entity types"
            className="h-8"
          />
        </div>
        {filteredTypes.map((item) => (
          <DropdownMenuCheckboxItem
            key={item.type}
            checked={selectedTypes.size === 0 || selectedTypes.has(item.type)}
            onSelect={(event: Event) => event.preventDefault()}
            onCheckedChange={(checked: boolean | "indeterminate") => {
              const next =
                selectedTypes.size === 0
                  ? new Set(entityTypes.map(({ type }) => type))
                  : new Set(selectedTypes)
              if (checked) next.add(item.type)
              else next.delete(item.type)
              onSelectedTypesChange(
                next.size === entityTypes.length ? new Set() : next,
              )
            }}
          >
            <span className="size-2.5 rounded-full" style={{ backgroundColor: item.color }} />
            <span className="min-w-0 flex-1 truncate">{item.type}</span>
            <span className="font-mono text-xs text-muted-foreground">{item.count}</span>
          </DropdownMenuCheckboxItem>
        ))}
        {filteredTypes.length === 0 ? (
          <p className="px-2 py-3 text-sm text-muted-foreground">No entity type found.</p>
        ) : null}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function GraphLayoutControl() {
  const sigma = useSigma()
  const circular = useLayoutCircular()
  const circlepack = useLayoutCirclepack()
  const random = useLayoutRandom()
  const [layout, setLayout] = useState<LayoutName>("Force Atlas")
  const [running, setRunning] = useState(false)
  const supervisorRef = useRef<LayoutSupervisor | null>(null)
  const stopTimerRef = useRef<number | null>(null)

  const stop = useCallback(() => {
    if (stopTimerRef.current !== null) window.clearTimeout(stopTimerRef.current)
    stopTimerRef.current = null
    supervisorRef.current?.stop()
    setRunning(false)
    sigma.refresh()
  }, [sigma])

  const startWorker = useCallback(
    (name: LayoutName) => {
      const graph = sigma.getGraph()
      if (!WORKER_LAYOUTS.has(name) || graph.order === 0) return
      stop()
      supervisorRef.current?.kill()
      let supervisor: LayoutSupervisor
      if (name === "Force Atlas") {
        supervisor = new ForceAtlas2Supervisor(graph, {
          settings: forceAtlas2.inferSettings(graph.order),
        }) as LayoutSupervisor
      } else if (name === "Force Directed") {
        supervisor = new ForceSupervisor(graph, {
          settings: {
            attraction: 0.0003,
            repulsion: 0.02,
            gravity: 0.02,
            inertia: 0.8,
            maxMove: 5,
          },
        }) as LayoutSupervisor
      } else {
        supervisor = new NoverlapSupervisor(graph, {
          settings: { margin: 10, expansion: 1.1, gridSize: 1, ratio: 1, speed: 3 },
        }) as LayoutSupervisor
      }
      supervisorRef.current = supervisor
      sigma.setCustomBBox(null)
      supervisor.start()
      setRunning(true)
      stopTimerRef.current = window.setTimeout(
        stop,
        Math.min(1_500 + graph.order / 10, 10_000),
      )
    },
    [sigma, stop],
  )

  const runLayout = useCallback(
    (name: LayoutName) => {
      setLayout(name)
      if (WORKER_LAYOUTS.has(name)) {
        startWorker(name)
        return
      }
      stop()
      supervisorRef.current?.kill()
      supervisorRef.current = null
      sigma.setCustomBBox(null)
      if (name === "Circular") circular.assign()
      if (name === "Circlepack") circlepack.assign()
      if (name === "Random") random.assign()
      sigma.refresh()
      sigma.getCamera().animatedReset()
    },
    [circlepack, circular, random, sigma, startWorker, stop],
  )

  useEffect(
    () => () => {
      if (stopTimerRef.current !== null) window.clearTimeout(stopTimerRef.current)
      supervisorRef.current?.kill()
    },
    [],
  )

  useEffect(() => {
    const timer = window.setTimeout(() => startWorker("Force Atlas"), 100)
    return () => window.clearTimeout(timer)
  }, [startWorker])

  return (
    <>
      {WORKER_LAYOUTS.has(layout) ? (
        <IconControl
          label={running ? `Pause ${layout}` : `Run ${layout}`}
          icon={running ? Pause : Play}
          onClick={() => (running ? stop() : startWorker(layout))}
        />
      ) : null}
      <DropdownMenu>
        <Tooltip>
          <TooltipTrigger asChild>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" className="rounded-none">
                <Grip className="size-4" />
                <span className="sr-only">Choose graph layout</span>
              </Button>
            </DropdownMenuTrigger>
          </TooltipTrigger>
          <TooltipContent side="right">Choose graph layout</TooltipContent>
        </Tooltip>
        <DropdownMenuContent side="right" align="start">
          <DropdownMenuLabel>Layout</DropdownMenuLabel>
          {LAYOUT_NAMES.map((name) => (
            <DropdownMenuItem key={name} onSelect={() => runLayout(name)}>
              <span className={cn("size-1.5 rounded-full", layout === name && "bg-primary")} />
              {name}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  )
}

function GraphCameraControls() {
  const sigma = useSigma()
  const { zoomIn, zoomOut, reset } = useCamera({ duration: 200, factor: 1.5 })
  return (
    <>
      <IconControl
        label="Rotate clockwise"
        icon={RotateCw}
        onClick={() =>
          sigma.getCamera().animate({ angle: sigma.getCamera().angle + Math.PI / 8 }, { duration: 200 })
        }
      />
      <IconControl
        label="Rotate counter-clockwise"
        icon={RotateCcw}
        onClick={() =>
          sigma.getCamera().animate({ angle: sigma.getCamera().angle - Math.PI / 8 }, { duration: 200 })
        }
      />
      <IconControl
        label="Reset view"
        icon={LocateFixed}
        onClick={() => {
          sigma.setCustomBBox(null)
          reset()
        }}
      />
      <IconControl label="Zoom in" icon={Plus} onClick={() => zoomIn()} />
      <IconControl label="Zoom out" icon={Minus} onClick={() => zoomOut()} />
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="contents">
            <FullScreenControl
              className="!h-8 !w-8 !rounded-none !border-0 !bg-transparent !shadow-none"
            >
              <Expand className="size-4" />
              <Minimize className="size-4" />
            </FullScreenControl>
          </span>
        </TooltipTrigger>
        <TooltipContent side="right">Full screen</TooltipContent>
      </Tooltip>
    </>
  )
}

function GraphLegendControl({
  entityTypes,
}: {
  entityTypes: Array<{ type: string; color: string; count: number }>
}) {
  const showLegend = useGraphExplorerStore((state) => state.showLegend)
  const setPreference = useGraphExplorerStore((state) => state.setViewerPreference)
  return (
    <>
      <IconControl
        label={showLegend ? "Hide legend" : "Show legend"}
        icon={Tags}
        onClick={() => setPreference("showLegend", !showLegend)}
      />
      {showLegend ? (
        <div className="absolute bottom-0 left-11 w-64 rounded-lg border bg-background/95 p-3 shadow-md backdrop-blur">
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Entity types
          </p>
          <div className="max-h-56 space-y-1 overflow-y-auto">
            {entityTypes.map((item) => (
              <div key={item.type} className="flex items-center gap-2 text-xs">
                <span className="size-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                <span className="min-w-0 flex-1 truncate">{item.type}</span>
                <span className="font-mono text-muted-foreground">{item.count}</span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </>
  )
}

function GraphSettingsControl({ edgeCount }: { edgeCount: number }) {
  const settings = useGraphExplorerStore()
  const booleanSettings = [
    ["showPropertyPanel", "Property panel"],
    ["showSearchBar", "Node search"],
    ["showNodeLabels", "Node labels"],
    ["enableNodeDrag", "Node dragging"],
    ["showEdgeLabels", "Edge labels"],
    ["hideUnselectedEdges", "Hide unrelated edges"],
    ["enableEdgeEvents", "Edge selection"],
  ] as const
  return (
    <DropdownMenu>
      <Tooltip>
        <TooltipTrigger asChild>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" className="rounded-none">
              <Settings2 className="size-4" />
              <span className="sr-only">Graph settings</span>
            </Button>
          </DropdownMenuTrigger>
        </TooltipTrigger>
        <TooltipContent side="right">Graph settings</TooltipContent>
      </Tooltip>
      <DropdownMenuContent side="right" align="end" className="w-64">
        <DropdownMenuLabel className="flex items-center gap-2">
          <CircleGauge className="size-4" />
          Display
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {booleanSettings.map(([key, label]) => (
          <DropdownMenuCheckboxItem
            key={key}
            checked={settings[key]}
            disabled={key === "enableEdgeEvents" && edgeCount > 5_000}
            onSelect={(event: Event) => event.preventDefault()}
            onCheckedChange={(checked: boolean | "indeterminate") =>
              settings.setViewerPreference(key, Boolean(checked))
            }
          >
            {label}
          </DropdownMenuCheckboxItem>
        ))}
        <DropdownMenuSeparator />
        <div className="space-y-2 px-2 py-1.5">
          <label
            htmlFor="graph-edge-size-minimum"
            className="text-xs font-medium text-muted-foreground"
          >
            Edge size range
          </label>
          <div className="flex items-center gap-2">
            <Input
              id="graph-edge-size-minimum"
              type="number"
              min={1}
              max={settings.maximumEdgeSize}
              step={0.5}
              value={settings.minimumEdgeSize}
              className="h-8 w-20"
              onKeyDown={(event) => event.stopPropagation()}
              onChange={(event) => {
                const value = Number(event.target.value)
                if (Number.isFinite(value) && value >= 1 && value <= settings.maximumEdgeSize) {
                  settings.setEdgeSizeRange(value, settings.maximumEdgeSize)
                }
              }}
            />
            <span className="text-xs text-muted-foreground">to</span>
            <Input
              type="number"
              min={settings.minimumEdgeSize}
              max={10}
              step={0.5}
              value={settings.maximumEdgeSize}
              aria-label="Maximum edge size"
              className="h-8 w-20"
              onKeyDown={(event) => event.stopPropagation()}
              onChange={(event) => {
                const value = Number(event.target.value)
                if (
                  Number.isFinite(value) &&
                  value >= settings.minimumEdgeSize &&
                  value <= 10
                ) {
                  settings.setEdgeSizeRange(settings.minimumEdgeSize, value)
                }
              }}
            />
          </div>
          <button
            type="button"
            className="text-xs text-muted-foreground hover:text-foreground"
            onClick={() => settings.setEdgeSizeRange(1, 5)}
          >
            Reset to 1–5
          </button>
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled>
          <Eye className="size-4" />
          Visible graph only
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function IconControl({
  label,
  icon: Icon,
  onClick,
}: {
  label: string
  icon: React.ComponentType<{ className?: string }>
  onClick: () => void
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="rounded-none"
          onClick={onClick}
        >
          <Icon className="size-4" />
          <span className="sr-only">{label}</span>
        </Button>
      </TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  )
}

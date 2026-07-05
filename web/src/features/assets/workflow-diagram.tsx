import { Background, Controls, ReactFlow, type Edge, type Node } from "@xyflow/react"

type WorkflowDiagramProps = {
  steps?: string | null
}

export function WorkflowDiagram({ steps }: WorkflowDiagramProps) {
  const labels = parseStepLabels(steps)
  const nodes: Node[] = labels.map((label, index) => ({
    id: String(index + 1),
    position: { x: index * 230, y: index % 2 === 0 ? 20 : 120 },
    data: { label },
    type: "default",
  }))
  const edges: Edge[] = labels.slice(1).map((_, index) => ({
    id: `e${index + 1}-${index + 2}`,
    source: String(index + 1),
    target: String(index + 2),
    animated: true,
  }))

  return (
    <div className="h-72 overflow-hidden rounded-lg border bg-background">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        fitView
        minZoom={0.6}
        maxZoom={1.4}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  )
}

function parseStepLabels(steps?: string | null) {
  if (!steps) {
    return ["Capture input", "Run model", "Review output", "Publish capability"]
  }

  try {
    const parsed = JSON.parse(steps) as Array<{ name?: string; label?: string; title?: string }>
    const labels = parsed
      .map((step) => step.name ?? step.label ?? step.title)
      .filter((label): label is string => Boolean(label))
    return labels.length ? labels : ["Capture input", "Run model", "Review output", "Publish capability"]
  } catch {
    return steps
      .split(/\r?\n|->|,/)
      .map((step) => step.trim())
      .filter(Boolean)
      .slice(0, 6)
  }
}

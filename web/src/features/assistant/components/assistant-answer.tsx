import type { ReactNode } from "react"
import { useMemo } from "react"
import { defaultRemarkPlugins } from "streamdown"

import { MessageResponse } from "@/components/ai-elements/message-response"
import type { AssistantSourceRef } from "@/features/assistant/components/assistant-sources-panel"

const CITATION_TAG = "orgmemory-citation"
const CITATION_MARKER = /\[(\d{1,3})]/g

interface MarkdownNode {
  type: string
  value?: string
  children?: MarkdownNode[]
}

interface CitationElementProps {
  children?: ReactNode
  "data-number"?: string
}

export function AssistantAnswer({
  content,
  sources,
  onOpenSource,
}: {
  content: string
  sources: AssistantSourceRef[]
  onOpenSource: (sourceId: string) => void
}) {
  const sourceByNumber = useMemo(
    () => new Map(sources.map((source) => [source.citationNumber, source])),
    [sources],
  )
  const citationContractKey = useMemo(
    () =>
      sources
        .map((source) => `${source.citationNumber}:${source.id}:${source.title}`)
        .join("|"),
    [sources],
  )
  const remarkPlugins = useMemo(
    () => [
      ...Object.values(defaultRemarkPlugins),
      citationRemarkPlugin,
    ],
    [],
  )
  const components = useMemo(
    () => ({
      [CITATION_TAG]: ({ children, "data-number": rawNumber }: CitationElementProps) => {
        const citationNumber = Number(rawNumber)
        const source = sourceByNumber.get(citationNumber)
        if (!source) return <>{children}</>

        return (
          <button
            type="button"
            className="mx-0.5 inline-flex min-w-5 items-center justify-center rounded-md bg-accent px-1.5 py-0.5 align-baseline text-xs font-semibold tabular-nums text-accent-foreground transition-colors hover:bg-accent/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`Open source ${citationNumber}: ${source.title}`}
            title={source.title}
            onClick={() => onOpenSource(source.id)}
          >
            [{citationNumber}]
          </button>
        )
      },
    }),
    [onOpenSource, sourceByNumber],
  )

  return (
    <MessageResponse
      key={citationContractKey}
      remarkPlugins={remarkPlugins}
      allowedTags={{ [CITATION_TAG]: ["dataNumber"] }}
      literalTagContent={[CITATION_TAG]}
      components={components}
    >
      {content}
    </MessageResponse>
  )
}

function citationRemarkPlugin() {
  return (tree: MarkdownNode) => {
    replaceCitationMarkers(tree)
  }
}

function replaceCitationMarkers(node: MarkdownNode) {
  if (!node.children || node.type === "link" || node.type === "linkReference") {
    return
  }

  const children: MarkdownNode[] = []
  for (const child of node.children) {
    if (child.type !== "text" || child.value === undefined) {
      replaceCitationMarkers(child)
      children.push(child)
      continue
    }

    let cursor = 0
    CITATION_MARKER.lastIndex = 0
    for (const match of child.value.matchAll(CITATION_MARKER)) {
      const matchIndex = match.index
      const citationNumber = Number(match[1])
      if (matchIndex > cursor) {
        children.push({
          type: "text",
          value: child.value.slice(cursor, matchIndex),
        })
      }
      children.push({
        type: "html",
        value: `<${CITATION_TAG} data-number="${citationNumber}">[${citationNumber}]</${CITATION_TAG}>`,
      })
      cursor = matchIndex + match[0].length
    }
    if (cursor === 0) {
      children.push(child)
    } else if (cursor < child.value.length) {
      children.push({
        type: "text",
        value: child.value.slice(cursor),
      })
    }
  }
  node.children = children
}

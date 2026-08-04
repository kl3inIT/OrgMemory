import type { ReactNode } from "react"
import { useMemo } from "react"
import { defaultRemarkPlugins } from "streamdown"

import {
  InlineCitation,
  InlineCitationTrigger,
} from "@/components/ai-elements/inline-citation"
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
          <InlineCitation>
            <InlineCitationTrigger
              sources={[source.url]}
              label={`[${citationNumber}]`}
              role="button"
              tabIndex={0}
              aria-label={`Open source ${citationNumber}: ${source.title}`}
              onClick={() => onOpenSource(source.id)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault()
                  onOpenSource(source.id)
                }
              }}
            />
          </InlineCitation>
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

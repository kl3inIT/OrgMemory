import type { ReactNode } from "react"
import { createContext, useContext, useMemo } from "react"
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

interface CitationContract {
  sourceByNumber: Map<number, AssistantSourceRef>
  onOpenSource: (sourceId: string) => void
}

/**
 * Carries the citation contract to markers that are already rendered.
 *
 * Streamdown memoizes each parsed block and its comparator does not re-render
 * one for a changed component map, so a marker rendered before its source
 * arrived would stay literal text. Context reaches those markers because
 * context propagation ignores memo boundaries, which lets the component map
 * stay referentially stable and the parsed answer stay mounted while sources
 * stream in.
 */
const CitationContractContext = createContext<CitationContract>({
  sourceByNumber: new Map(),
  onOpenSource: () => {},
})

function CitationMarker({ children, "data-number": rawNumber }: CitationElementProps) {
  const { sourceByNumber, onOpenSource } = useContext(CitationContractContext)
  const citationNumber = Number(rawNumber)
  const source = sourceByNumber.get(citationNumber)
  if (!source) return <>{children}</>

  if (!source.available) {
    return (
      <InlineCitation>
        <InlineCitationTrigger
          sources={[]}
          label={`[${citationNumber}]`}
          className="cursor-default opacity-60"
          aria-label={`Source ${citationNumber} is no longer available`}
          title="Private file no longer available"
        />
      </InlineCitation>
    )
  }

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
}

const CITATION_COMPONENTS = { [CITATION_TAG]: CitationMarker }
const CITATION_ALLOWED_TAGS = { [CITATION_TAG]: ["dataNumber"] }
const CITATION_LITERAL_TAGS = [CITATION_TAG]
const CITATION_REMARK_PLUGINS = [
  ...Object.values(defaultRemarkPlugins),
  citationRemarkPlugin,
]

export function AssistantAnswer({
  content,
  sources,
  onOpenSource,
  showEvidenceDisclaimer,
}: {
  content: string
  sources: AssistantSourceRef[]
  onOpenSource: (sourceId: string) => void
  showEvidenceDisclaimer: boolean
}) {
  const citationContract = useMemo<CitationContract>(
    () => ({
      sourceByNumber: new Map(sources.map((source) => [source.citationNumber, source])),
      onOpenSource,
    }),
    [onOpenSource, sources],
  )

  return (
    <>
      <CitationContractContext.Provider value={citationContract}>
        <MessageResponse
          remarkPlugins={CITATION_REMARK_PLUGINS}
          allowedTags={CITATION_ALLOWED_TAGS}
          literalTagContent={CITATION_LITERAL_TAGS}
          components={CITATION_COMPONENTS}
        >
          {content}
        </MessageResponse>
      </CitationContractContext.Provider>
      {showEvidenceDisclaimer ? (
        <p className="mt-2 text-xs text-content-muted">
          Câu trả lời chỉ dựa trên tài liệu bạn có quyền truy cập.
        </p>
      ) : null}
    </>
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

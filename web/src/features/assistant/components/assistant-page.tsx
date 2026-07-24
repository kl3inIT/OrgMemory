import { useChat } from "@ai-sdk/react"
import { type SourceUrlUIPart, type UIMessage } from "ai"
import { Copy, LoaderCircle, RotateCcw, ShieldCheck } from "lucide-react"
import { useCallback, useMemo, useRef, useState } from "react"
import { toast } from "sonner"

import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation"
import {
  Message,
  MessageAction,
  MessageActions,
  MessageContent,
} from "@/components/ai-elements/message"
import {
  PromptInput,
  PromptInputBody,
  PromptInputFooter,
  type PromptInputMessage,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
} from "@/components/ai-elements/prompt-input"
import { Source, Sources, SourcesContent, SourcesTrigger } from "@/components/ai-elements/sources"
import { Suggestion, Suggestions } from "@/components/ai-elements/suggestion"
import { Button } from "@/components/ui/button"
import { createAssistantTransport } from "@/features/assistant/api/chat-transport"
import { AssistantAnswer } from "@/features/assistant/components/assistant-answer"
import {
  type AssistantSourceRef,
  AssistantSourcesPanel,
} from "@/features/assistant/components/assistant-sources-panel"

const SUGGESTIONS = [
  "What is the probation policy?",
  "How do I submit a travel expense claim?",
  "What is the product release process?",
]

function textFor(message: UIMessage) {
  return message.parts
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("\n")
}

function sourcesFor(message: UIMessage) {
  const sources: AssistantSourceRef[] = []
  const seenNumbers = new Set<number>()
  const seenIds = new Set<string>()
  for (const part of message.parts) {
    if (part.type !== "source-url") continue
    const citationNumber = citationNumberFor(part)
    const url = citationUrl(part.url)
    if (
      citationNumber === null ||
      url === null ||
      seenNumbers.has(citationNumber) ||
      seenIds.has(part.sourceId)
    ) {
      continue
    }
    seenNumbers.add(citationNumber)
    seenIds.add(part.sourceId)
    sources.push({
      id: part.sourceId,
      citationNumber,
      title: part.title ?? "Company knowledge",
      url,
    })
  }
  return sources.sort((left, right) => left.citationNumber - right.citationNumber)
}

function hasVisibleOutput(message: UIMessage) {
  return textFor(message).trim().length > 0 || sourcesFor(message).length > 0
}

function citationNumberFor(source: SourceUrlUIPart) {
  const metadata = source.providerMetadata?.orgmemory
  if (!metadata || Array.isArray(metadata)) return null
  const number = metadata.citationNumber
  return typeof number === "number" && Number.isSafeInteger(number) && number > 0
    ? number
    : null
}

function citationUrl(rawUrl: string) {
  try {
    const baseUrl = new URL("https://orgmemory.invalid")
    const sourceUrl = new URL(rawUrl, baseUrl)
    if (
      sourceUrl.origin === baseUrl.origin &&
      /^\/api\/citations\/[0-9a-f-]{36}\/content$/i.test(sourceUrl.pathname) &&
      sourceUrl.search === "" &&
      sourceUrl.hash === ""
    ) {
      return sourceUrl.pathname
    }
  } catch {
    return null
  }
  return null
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 12) return "Good morning"
  if (hour < 18) return "Good afternoon"
  return "Good evening"
}

export function AssistantPage() {
  const transport = useMemo(() => createAssistantTransport(), [])
  const [text, setText] = useState("")
  const [sourcePanel, setSourcePanel] = useState<{
    messageId: string
    sources: AssistantSourceRef[]
    selectedSourceId: string
  } | null>(null)
  const submitLock = useRef(false)
  const { messages, sendMessage, status, stop, error, clearError } = useChat({ transport })
  const busy = status === "submitted" || status === "streaming"
  const latestMessage = messages.at(-1)
  const retryText = [...messages]
    .reverse()
    .find((message) => message.role === "user")
  const retryMessage = retryText ? textFor(retryText) : ""
  const showWaiting =
    busy &&
    (latestMessage === undefined ||
      latestMessage.role === "user" ||
      !hasVisibleOutput(latestMessage))
  const openSources = useCallback((messageId: string, sources: AssistantSourceRef[], sourceId: string) => {
    setSourcePanel({
      messageId,
      sources,
      selectedSourceId: sourceId,
    })
  }, [])

  function send(rawMessage: string) {
    const message = rawMessage.trim()
    if (!message || busy || submitLock.current) return

    submitLock.current = true
    clearError()
    const turn = sendMessage({ text: message })
    setText("")
    const release = () => {
      submitLock.current = false
    }
    void turn.then(release, release)
    return turn
  }

  function submit(message: PromptInputMessage) {
    return send(message.text)
  }

  const composer = (
    <PromptInput
      onSubmit={submit}
      className="w-full [&_[data-slot=input-group]]:rounded-3xl [&_[data-slot=input-group]]:border-border-subtle [&_[data-slot=input-group]]:bg-assistant-composer [&_[data-slot=input-group]]:shadow-sm [&_[data-slot=input-group]]:ring-0"
    >
      <PromptInputBody>
        <PromptInputTextarea
          value={text}
          onChange={(event) => setText(event.currentTarget.value)}
          placeholder="Ask OrgMemory…"
          autoFocus
          className="min-h-12"
        />
      </PromptInputBody>
      <PromptInputFooter>
        <PromptInputTools>
          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <ShieldCheck className="size-3.5" aria-hidden="true" />
            Permission-aware
          </span>
        </PromptInputTools>
        <PromptInputSubmit
          status={status}
          onStop={stop}
          disabled={!busy && !text.trim()}
          className="rounded-full"
        />
      </PromptInputFooter>
    </PromptInput>
  )

  if (messages.length === 0) {
    return (
      <div className="flex min-w-0 flex-1 flex-col items-center justify-center gap-5 overflow-y-auto px-5 pb-12">
        <h1 className="text-page-title text-content-primary">{greeting()}</h1>
        <div className="w-full max-w-2xl">{composer}</div>
        <Suggestions className="mx-auto max-w-2xl flex-wrap justify-center whitespace-normal">
          {SUGGESTIONS.map((suggestion) => (
            <Suggestion
              key={suggestion}
              suggestion={suggestion}
              className="bg-transparent text-foreground"
              onClick={(value) => {
                void send(value)?.catch(() => undefined)
              }}
            />
          ))}
        </Suggestions>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden">
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Conversation className="min-h-0 flex-1">
          <ConversationContent className="mx-auto w-full max-w-3xl gap-7 px-4 py-6">
            {messages.map((message) => {
              const content = textFor(message)
              const sources = sourcesFor(message)
              if (!content.trim() && sources.length === 0) return null

              return (
                <Message from={message.role} key={message.id}>
                  {content.trim() ? (
                    <MessageContent className="text-body">
                      <AssistantAnswer
                        content={content}
                        sources={sources}
                        onOpenSource={(sourceId) => openSources(message.id, sources, sourceId)}
                      />
                    </MessageContent>
                  ) : null}
                  {sources.length > 0 ? (
                    <Sources className="mb-0 text-content-secondary">
                      <SourcesTrigger
                        count={sources.length}
                        onClick={() => {
                          openSources(message.id, sources, sources[0].id)
                        }}
                      />
                      <SourcesContent className="flex-row flex-wrap gap-2">
                        {sources.map((source) => (
                          <Source
                            key={source.id}
                            href={source.url}
                            title={source.title}
                            target="_self"
                            onClick={(event) => {
                              event.preventDefault()
                              openSources(message.id, sources, source.id)
                            }}
                            className="inline-flex items-center gap-1.5 rounded-md border border-border-subtle bg-surface-subtle px-2.5 py-1.5 text-supporting text-content-secondary transition-colors hover:bg-action-ghost-hover hover:text-content-primary"
                          >
                            <span className="text-xs tabular-nums">{source.citationNumber}</span>
                            <span className="font-medium">{source.title}</span>
                          </Source>
                        ))}
                      </SourcesContent>
                    </Sources>
                  ) : null}
                  {content.trim() ? (
                    <MessageActions className={message.role === "user" ? "justify-end" : undefined}>
                      <MessageAction
                        label="Copy message"
                        tooltip="Copy message"
                        onClick={() =>
                          navigator.clipboard
                            .writeText(content)
                            .then(() => toast.success("Message copied"))
                            .catch(() => toast.error("Could not copy message"))
                        }
                      >
                        <Copy className="size-4" />
                      </MessageAction>
                    </MessageActions>
                  ) : null}
                </Message>
              )
            })}
            {showWaiting ? (
              <Message from="assistant">
                <MessageContent className="flex-row items-center gap-2 text-body text-muted-foreground">
                  <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                  <span>Searching permitted knowledge…</span>
                </MessageContent>
              </Message>
            ) : null}
            {error ? (
              <div
                role="alert"
                className="flex items-center justify-between gap-4 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3"
              >
                <p className="text-sm text-destructive">
                  OrgMemory could not complete this turn.
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!retryMessage || busy}
                  onClick={() => {
                    void send(retryMessage)?.catch(() => undefined)
                  }}
                >
                  <RotateCcw className="size-4" aria-hidden="true" />
                  Retry
                </Button>
              </div>
            ) : null}
          </ConversationContent>
          <ConversationScrollButton />
        </Conversation>
        <div className="mx-auto w-full max-w-3xl px-4 pb-6">{composer}</div>
      </div>
      <AssistantSourcesPanel
        open={sourcePanel !== null}
        sources={sourcePanel?.sources ?? []}
        selectedSourceId={sourcePanel?.selectedSourceId ?? null}
        onClose={() => setSourcePanel(null)}
        onSelect={(selectedSourceId) =>
          setSourcePanel((current) => (current ? { ...current, selectedSourceId } : current))
        }
      />
    </div>
  )
}

import { useChat } from "@ai-sdk/react"
import {
  type SourceDocumentUIPart,
  type SourceUrlUIPart,
  type UIMessage,
} from "ai"
import { Copy, LoaderCircle, ShieldCheck } from "lucide-react"
import { useMemo, useRef, useState } from "react"
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
import { MessageResponse } from "@/components/ai-elements/message-response"
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
import { createAssistantTransport } from "@/features/assistant/api/chat-transport"

const SUGGESTIONS = [
  "What is the probation policy?",
  "How do I submit a travel expense claim?",
  "What is the product release process?",
]

type SourcePart = SourceUrlUIPart | SourceDocumentUIPart

function textFor(message: UIMessage) {
  return message.parts
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("\n")
}

function sourcesFor(message: UIMessage) {
  const sources = message.parts.filter(
    (part): part is SourcePart => part.type === "source-url" || part.type === "source-document",
  )
  return [...new Map(sources.map((source) => [source.sourceId, source])).values()]
}

function hasVisibleOutput(message: UIMessage) {
  return textFor(message).trim().length > 0 || sourcesFor(message).length > 0
}

function sourceHref(source: SourcePart) {
  if (source.type === "source-url") return source.url
  try {
    const baseUrl = new URL("https://orgmemory.invalid")
    const sourceUrl = new URL(source.sourceId, baseUrl)
    if (sourceUrl.origin === baseUrl.origin && sourceUrl.pathname === "/sources") {
      return `${sourceUrl.pathname}${sourceUrl.search}${sourceUrl.hash}`
    }
  } catch {
    // Fall through to the validated Documents search route.
  }
  const query = source.title?.trim()
  return query ? `/sources?q=${encodeURIComponent(query)}` : "/sources"
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
  const submitLock = useRef(false)
  const { messages, sendMessage, status, stop, error, clearError } = useChat({ transport })
  const busy = status === "submitted" || status === "streaming"
  const latestMessage = messages.at(-1)
  const showWaiting =
    busy &&
    (latestMessage === undefined ||
      latestMessage.role === "user" ||
      !hasVisibleOutput(latestMessage))

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
                    <MessageResponse>{content}</MessageResponse>
                  </MessageContent>
                ) : null}
                {sources.length > 0 ? (
                  <Sources className="mb-0 text-content-secondary">
                    <SourcesTrigger count={sources.length} />
                    <SourcesContent className="flex-row flex-wrap gap-2">
                      {sources.map((source) => (
                        <Source
                          key={`${source.type}-${source.sourceId}`}
                          href={sourceHref(source)}
                          title={source.title ?? "Company knowledge"}
                          target={sourceHref(source).startsWith("/") ? "_self" : "_blank"}
                          className="inline-flex items-center gap-1.5 rounded-md border border-border-subtle bg-surface-subtle px-2.5 py-1.5 text-supporting text-content-secondary transition-colors hover:bg-action-ghost-hover hover:text-content-primary"
                        />
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
            <p role="alert" className="text-sm text-destructive">
              OrgMemory could not complete this turn. Please try again.
            </p>
          ) : null}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>
      <div className="mx-auto w-full max-w-3xl px-4 pb-6">{composer}</div>
    </div>
  )
}
